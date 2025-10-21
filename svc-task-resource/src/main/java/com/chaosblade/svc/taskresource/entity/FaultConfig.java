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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "fault_config")
public class FaultConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @NotBlank
    @Column(name = "faultscript", nullable = false, columnDefinition = "TEXT")
    private String faultscript;

    @Column(name = "type")
    private String type;

    @Column(name = "task_id")
    private Long taskId; // 可为空

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public String getFaultscript() { return faultscript; }
    public void setFaultscript(String faultscript) { this.faultscript = faultscript; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
}

