package com.chaosblade.svc.taskexecutor.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "baggage_map",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_exec_service",
            columnNames = {"execution_id", "service_name"}))
public class BaggageMap {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "execution_id", nullable = false)
  private Long executionId;

  @Column(name = "service_name", nullable = false, length = 200)
  private String serviceName;

  @Column(name = "value", nullable = false, length = 1024)
  private String value;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  public BaggageMap() {}

  public BaggageMap(Long executionId, String serviceName, String value) {
    this.executionId = executionId;
    this.serviceName = serviceName;
    this.value = value;
  }

  public Long getId() {
    return id;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
