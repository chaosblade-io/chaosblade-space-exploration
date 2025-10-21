package com.chaosblade.svc.taskexecutor.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "fault_config")
public class FaultConfig {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "node_id", nullable = false)
  private Long nodeId;

  @Column(name = "faultscript", nullable = false, columnDefinition = "TEXT")
  private String faultscript;

  @Column(name = "type")
  private String type;

  @Column(name = "task_id")
  private Long taskId;

  public Long getId() {
    return id;
  }

  public Long getNodeId() {
    return nodeId;
  }

  public String getFaultscript() {
    return faultscript;
  }

  public String getType() {
    return type;
  }

  public Long getTaskId() {
    return taskId;
  }
}
