package com.chaosblade.svc.taskresource.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 只读引用 executor 侧写入的 task_execution_log 表 */
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

  public Long getId() {
    return id;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public LocalDateTime getTs() {
    return ts;
  }

  public Integer getLevel() {
    return level;
  }

  public String getMessage() {
    return message;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
