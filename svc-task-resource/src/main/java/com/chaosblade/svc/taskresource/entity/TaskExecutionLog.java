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
import java.time.LocalDateTime;

/**
 * 只读引用 executor 侧写入的 task_execution_log 表
 */
@Entity
@Table(name = "task_execution_log")
public class TaskExecutionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "level", nullable = false)
    private Integer level; // 0=DEBUG,1=INFO,2=WARN,3=ERROR

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getExecutionId() { return executionId; }
    public LocalDateTime getTs() { return ts; }
    public Integer getLevel() { return level; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

