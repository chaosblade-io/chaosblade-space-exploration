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

package com.chaosblade.svc.taskexecutor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_cases")
public class TestCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    public enum CaseType { BASELINE, SINGLE, DUAL }

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 16)
    private CaseType caseType;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount;

    // 存储 faults 的 JSON 字符串
    @Column(name = "faults_json", columnDefinition = "JSON", nullable = false)
    private String faultsJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "execution_id")
    private Long executionId;

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public CaseType getCaseType() { return caseType; }
    public void setCaseType(CaseType caseType) { this.caseType = caseType; }

    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }

    public String getFaultsJson() { return faultsJson; }
    public void setFaultsJson(String faultsJson) { this.faultsJson = faultsJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
}

