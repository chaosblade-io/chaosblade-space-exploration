/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.svc.taskexecutor.entity.InterceptReplayResult;
import com.chaosblade.svc.taskexecutor.entity.TaskExecution;
import com.chaosblade.svc.taskexecutor.entity.TaskConclusion;
import com.chaosblade.svc.taskexecutor.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SummaryService {
    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final InterceptReplayResultRepository interceptReplayResultRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final TaskConclusionRepository taskConclusionRepository;
    private final LlmClient llmClient;

    public SummaryService(TestCaseRepository testCaseRepository,
                          TestResultRepository testResultRepository,
                          InterceptReplayResultRepository interceptReplayResultRepository,
                          TaskExecutionRepository taskExecutionRepository,
                          TaskConclusionRepository taskConclusionRepository,
                          LlmClient llmClient) {
        this.testCaseRepository = testCaseRepository;
        this.testResultRepository = testResultRepository;
        this.interceptReplayResultRepository = interceptReplayResultRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.taskConclusionRepository = taskConclusionRepository;
        this.llmClient = llmClient;
    }

    @Transactional
    public void summarizeAsync(Long executionId) {
        // 异步执行，避免阻塞主流程
        new Thread(() -> {
            try { summarizeAndSave(executionId); } catch (Exception ex) { log.warn("Summarize failed: {}", ex.getMessage()); }
        }, "summary-"+executionId).start();
    }

    @Transactional
    public void summarizeAndSave(Long executionId) {
        Optional<TaskConclusion> existed = taskConclusionRepository.findByExecutionId(executionId);
        if (existed.isPresent()) return; // 已存在则不重复生成

        TaskExecution exec = taskExecutionRepository.findById(executionId).orElse(null);
        int totalCases = testCaseRepository.findByExecutionId(executionId).size();
        var results = testResultRepository.findByExecutionId(executionId);
        int failedCases = 0;
        double avgErrRate = 0.0;
        if (results != null && !results.isEmpty()) {
            for (var r : results) {
                double er = (r.getErrRate()==null) ? 0.0 : r.getErrRate().doubleValue();
                if (er > 0.0) failedCases++;
                avgErrRate += er;
            }
            avgErrRate /= results.size();
        }
        // 故障类型与影响服务
        Map<String, Set<String>> serviceToTypes = new LinkedHashMap<>();
        List<InterceptReplayResult> rrs = interceptReplayResultRepository.findByExecutionId(executionId);
        for (var r : rrs) {
            String svc = r.getServiceName();
            String type = r.getFaultType();
            if (svc == null || svc.isBlank()) continue;
            serviceToTypes.computeIfAbsent(svc, k -> new LinkedHashSet<>());
            if (type != null && !type.isBlank()) serviceToTypes.get(svc).add(type);
        }
        int affectedServices = serviceToTypes.size();
        Set<String> allTypes = new LinkedHashSet<>();
        for (var e : serviceToTypes.values()) allTypes.addAll(e);

        String prompt = buildPrompt(exec, totalCases, failedCases, avgErrRate, affectedServices, allTypes, serviceToTypes);
        String content = null;
        try {
            content = llmClient.chat(prompt);
        } catch (Exception ex) {
            log.warn("LLM call error: {}", ex.getMessage());
        }
        if (content == null || content.isBlank()) {
            content = fallbackSummary(totalCases, failedCases, avgErrRate, affectedServices, allTypes);
        }
        TaskConclusion tc = new TaskConclusion();
        tc.setExecutionId(executionId);
        tc.setModelContent(content);
        taskConclusionRepository.save(tc);
        log.info("[Summary] Saved model summary for executionId={}", executionId);
    }

    private String buildPrompt(TaskExecution exec,
                               int totalCases,
                               int failedCases,
                               double avgErrRate,
                               int affectedServices,
                               Set<String> allTypes,
                               Map<String, Set<String>> serviceToTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是混沌工程/故障注入领域的专家。请根据以下测试执行数据，生成一段面向前端产品用户、可读性强的中文总结（300-600字）：\n");
        if (exec != null) {
            sb.append("- 执行ID: ").append(exec.getId()).append("，环境(namespace): ").append(exec.getNamespace()).append("，状态: ").append(exec.getStatus()).append("\n");
        }
        sb.append("- 用例总数: ").append(totalCases).append("，失败用例数: ").append(failedCases)
          .append("，平均错误率(%): ").append(String.format(java.util.Locale.ROOT, "%.2f", avgErrRate)).append("\n");
        sb.append("- 受影响服务数: ").append(affectedServices).append("；故障类型覆盖: ").append(allTypes).append("\n");
        sb.append("- 服务-故障类型映射: ").append(serviceToTypes).append("\n\n");
        sb.append("请输出：\n");
        sb.append("1) 本次潜在故障空间规模（粗略估计）；\n");
        sb.append("2) 可能导致链路失效的关键故障类型或组合；\n");
        sb.append("3) 失败用例的可能根因与风险评估；\n");
        sb.append("4) 可操作的优化建议（测试与架构层面）。\n");
        return sb.toString();
    }

    private String fallbackSummary(int totalCases, int failedCases, double avgErrRate, int affectedServices, Set<String> allTypes) {
        return "自动总结暂不可用。基础信息：用例总数="+totalCases+
                "，失败用例数="+failedCases+
                "，平均错误率(%)="+String.format(java.util.Locale.ROOT, "%.2f", avgErrRate)+
                "，受影响服务数="+affectedServices+
                "，故障类型="+allTypes+"。";
    }
}

