package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.svc.taskexecutor.entity.InterceptReplayResult;
import com.chaosblade.svc.taskexecutor.entity.TaskExecution;
import com.chaosblade.svc.taskexecutor.entity.TaskConclusion;
import com.chaosblade.svc.taskexecutor.entity.TestResult;
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
        List<TestResult> results = testResultRepository.findByExecutionId(executionId);
        int failedCases = 0;
        double avgErrRate = 0.0;
        if (results != null && !results.isEmpty()) {
            for (TestResult r : results) {
                double er = (r.getErrRate()==null) ? 0.0 : r.getErrRate().doubleValue();
                if (er > 0.0) failedCases++;
                avgErrRate += er;
            }
            avgErrRate /= results.size();
        }
        // 故障类型与影响服务
        Map<String, Set<String>> serviceToTypes = new LinkedHashMap<>();
        List<InterceptReplayResult> rrs = interceptReplayResultRepository.findByExecutionId(executionId);
        for (InterceptReplayResult r : rrs) {
            String svc = r.getServiceName();
            String type = r.getFaultType();
            if (svc == null || svc.trim().isEmpty()) continue;
            serviceToTypes.computeIfAbsent(svc, k -> new LinkedHashSet<>());
            if (type != null && !type.trim().isEmpty()) serviceToTypes.get(svc).add(type);
        }
        int affectedServices = serviceToTypes.size();
        Set<String> allTypes = new LinkedHashSet<>();
        for (Set<String> e : serviceToTypes.values()) allTypes.addAll(e);

        String prompt = buildPrompt(exec, totalCases, failedCases, avgErrRate, affectedServices, allTypes, serviceToTypes);
        String content = null;
        try {
            content = llmClient.chat(prompt);
        } catch (Exception ex) {
            log.warn("LLM call error: {}", ex.getMessage());
        }
        if (content == null || content.trim().isEmpty()) {
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
        sb.append("你是混沌工程/故障注入领域的专家。请根据以下测试执行数据，生成一份紧凑的中文总结报告。\n\n");
        sb.append("## 测试数据\n");
        if (exec != null) {
            sb.append("- 执行ID: ").append(exec.getId()).append("，环境: ").append(exec.getNamespace()).append("，状态: ").append(exec.getStatus()).append("\n");
        }
        sb.append("- 用例总数: ").append(totalCases).append("，失败: ").append(failedCases)
          .append("，平均错误率: ").append(String.format(java.util.Locale.ROOT, "%.2f%%", avgErrRate)).append("\n");
        sb.append("- 受影响服务: ").append(affectedServices).append("个，故障类型: ").append(allTypes).append("\n");
        sb.append("- 服务→故障映射: ").append(serviceToTypes).append("\n\n");
        sb.append("## 输出要求\n");
        sb.append("请严格按以下 Markdown 格式输出，**不要在段落之间插入多余空行**，每个章节之间只空一行：\n\n");
        sb.append("```\n");
        sb.append("# 混沌工程测试总结报告\n");
        sb.append("（一句话概述）\n\n");
        sb.append("## 1. 潜在故障空间规模\n");
        sb.append("（紧凑描述，不超过3行）\n\n");
        sb.append("## 2. 关键故障类型与组合\n");
        sb.append("（用列表，每项一行）\n\n");
        sb.append("## 3. 失败用例根因与风险评估\n");
        sb.append("（紧凑描述，要点用加粗标注）\n\n");
        sb.append("## 4. 优化建议\n");
        sb.append("（用列表，分\"测试层面\"和\"架构层面\"两小节，每项一行）\n");
        sb.append("```\n\n");
        sb.append("**格式规则**：\n");
        sb.append("- 使用标准 Markdown 语法（#标题、**加粗**、- 列表、`代码`）\n");
        sb.append("- 段落之间只用一个空行分隔，禁止连续空行\n");
        sb.append("- 总长度控制在 300-500 字\n");
        sb.append("- 直接输出 Markdown 内容，不要包裹在代码块中\n");
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

