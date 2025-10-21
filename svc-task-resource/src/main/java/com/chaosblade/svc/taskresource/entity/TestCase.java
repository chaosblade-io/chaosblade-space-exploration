package com.chaosblade.svc.taskresource.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
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

  @Column(name = "faults_json", columnDefinition = "JSON", nullable = false)
  private String faultsJson;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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

  public CaseType getCaseType() {
    return caseType;
  }

  public Integer getTargetCount() {
    return targetCount;
  }

  public String getFaultsJson() {
    return faultsJson;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public Long getExecutionId() {
    return executionId;
  }
}
