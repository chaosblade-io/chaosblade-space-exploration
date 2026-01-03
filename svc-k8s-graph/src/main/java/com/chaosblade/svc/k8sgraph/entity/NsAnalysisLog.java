package com.chaosblade.svc.k8sgraph.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 命名空间分析执行日志实体
 */
@Entity
@Table(name = "ns_analysis_logs", indexes = {
    @Index(name = "idx_task_id", columnList = "task_id"),
    @Index(name = "idx_task_level", columnList = "task_id, level"),
    @Index(name = "idx_task_phase", columnList = "task_id, phase"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class NsAnalysisLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务ID */
    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    /** 日志级别: DEBUG=0, INFO=1, WARN=2, ERROR=3 */
    @Column(name = "level", nullable = false)
    private Integer level = 1;

    /** 执行阶段: 1-6 */
    @Column(name = "phase")
    private Integer phase;

    /** 日志消息 */
    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    /** 详细信息(JSON) */
    @Column(name = "details", columnDefinition = "JSON")
    private String details;

    /** 耗时(毫秒) */
    @Column(name = "duration_ms")
    private Long durationMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 日志级别常量
    public static final int DEBUG = 0;
    public static final int INFO = 1;
    public static final int WARN = 2;
    public static final int ERROR = 3;

    public NsAnalysisLog() {}

    public NsAnalysisLog(String taskId, int level, String message) {
        this.taskId = taskId;
        this.level = level;
        this.message = message;
    }

    public NsAnalysisLog(String taskId, int level, int phase, String message) {
        this.taskId = taskId;
        this.level = level;
        this.phase = phase;
        this.message = message;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getPhase() { return phase; }
    public void setPhase(Integer phase) { this.phase = phase; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getLevelName() {
        switch (level) {
            case 0: return "DEBUG";
            case 1: return "INFO";
            case 2: return "WARN";
            case 3: return "ERROR";
            default: return "UNKNOWN";
        }
    }
}

