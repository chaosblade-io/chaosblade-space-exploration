package com.chaosblade.svc.taskexecutor.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "task_conclusion")
public class TaskConclusion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "model_content", columnDefinition = "LONGTEXT")
  private String modelContent;

  @Column(name = "execution_id")
  private Long executionId;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getModelContent() {
    return modelContent;
  }

  public void setModelContent(String modelContent) {
    this.modelContent = modelContent;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }
}
