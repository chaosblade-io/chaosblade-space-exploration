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
@Table(name = "task_execution_log")
public class TaskExecutionLog {
    @Id
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "level", nullable = false)
    private Integer level; // 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum LogLevel {
        DEBUG(0), INFO(1), WARN(2), ERROR(3);
        
        private final int value;
        LogLevel(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static LogLevel fromValue(int value) {
            for (LogLevel level : values()) {
                if (level.value == value) return level;
            }
            return INFO;
        }
    }

    // Constructors
    public TaskExecutionLog() {}

    public TaskExecutionLog(Long executionId, LogLevel level, String message) {
        this.id = com.chaosblade.svc.taskexecutor.util.Ids.newId();
        this.executionId = executionId;
        this.level = level.getValue();
        this.message = message;
        this.ts = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }

    public LocalDateTime getTs() { return ts; }
    public void setTs(LocalDateTime ts) { this.ts = ts; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Helper methods
    public LogLevel getLogLevel() { return LogLevel.fromValue(level); }
    public void setLogLevel(LogLevel logLevel) { this.level = logLevel.getValue(); }
}
