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

package com.chaosblade.svc.taskresource.dto;

import com.chaosblade.svc.taskresource.entity.*;

import java.time.LocalDateTime;
import java.util.List;

public class ExecutionDetailsDto {
    public static class BasicInfo {
        public Long id;
        public String taskName;
        public String environment; // namespace
        public Api api;            // 若需要更详细也可扩为 HttpReqDef + Api
        public String initiator;   // 发起人（目前暂无字段，暂填 task.createdBy ）
        public LocalDateTime startTime;
        public String currentStatus;
        public long cumulativeDuration;
    }

    public static class LogEntry {
        public LocalDateTime ts;
        public String level;
        public String message;
    }

    public static class RealtimeStatus {
        public int totalTestCases;
        public int completedTestCases;
        public int totalServices;
        public int completedServices;
        public int testingServices;
    }

    // 用例 + 指标合并视图
    public static class TestCaseItem {
        public Long id;
        public Long taskId;
        public String caseType;
        public Integer targetCount;
        public String faultsJson;
        public LocalDateTime createdAt;
        public Long executionId;
        public MetricResult p50; // p50 无SLO阈值，仅返回值
        public MetricResult p95;
        public MetricResult p99;
        public MetricResult errRate;

        public static class MetricResult {
            public Boolean meetsSlo; // 是否满足SLO要求；p50恒为true或null（无阈值）
            public Object value;     // 指标值（Integer for p50/p95/p99, BigDecimal for errRate）
        }
    }

    // 故障注入摘要：关键呈现“对哪些服务注入了何种故障”
    public static class FaultInjectionSummary {
        public String serviceName;
        public java.util.List<String> faultTypes; // 例如 ["remove", "delay"]
    }

    public BasicInfo basic;
    public List<LogEntry> logs; // 如暂无日志表，暂返回空列表
    public RealtimeStatus realtime;
    public List<TestCaseItem> testCases; // 合并后的用例+指标
    public List<FaultInjectionSummary> faultInjections;
    // 大模型总结内容（来自 task_conclusion.model_content）
    public String modelConclusion;
    // 新增：LLM摘要（若DONE且生成或已缓存），对外不返回
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String llmSummary;
}

