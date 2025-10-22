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

package com.chaosblade.svc.taskresource.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 只读映射到 svc-task-executor 产出的 test_result 表，用于在资源模块聚合返回测试指标
 */
@Entity
@Table(name = "test_result")
public class TestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "test_case_id", nullable = false)
    private Long testCaseId;

    @Column(name = "p50")
    private Integer p50;

    @Column(name = "p95")
    private Integer p95;

    @Column(name = "p99")
    private Integer p99;

    @Column(name = "err_rate")
    private BigDecimal errRate;

    public Long getId() { return id; }
    public Long getExecutionId() { return executionId; }
    public Long getTestCaseId() { return testCaseId; }
    public Integer getP50() { return p50; }
    public Integer getP95() { return p95; }
    public Integer getP99() { return p99; }
    public BigDecimal getErrRate() { return errRate; }
}

