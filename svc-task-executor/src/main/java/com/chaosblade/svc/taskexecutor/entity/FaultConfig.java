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
