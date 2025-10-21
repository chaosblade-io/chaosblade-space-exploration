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

  public enum CaseType {
    BASELINE,
    SINGLE,
    DUAL
  }

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

  public Long getId() {
    return id;
  }

  public Long getTaskId() {
    return taskId;
  }

  public void setTaskId(Long taskId) {
    this.taskId = taskId;
  }

  public CaseType getCaseType() {
    return caseType;
  }

  public void setCaseType(CaseType caseType) {
    this.caseType = caseType;
  }

  public Integer getTargetCount() {
    return targetCount;
  }

  public void setTargetCount(Integer targetCount) {
    this.targetCount = targetCount;
  }

  public String getFaultsJson() {
    return faultsJson;
  }

  public void setFaultsJson(String faultsJson) {
    this.faultsJson = faultsJson;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }
}
