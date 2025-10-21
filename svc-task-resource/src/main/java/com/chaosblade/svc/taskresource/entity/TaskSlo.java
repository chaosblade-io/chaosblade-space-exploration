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

/**
 * 任务SLO实体 - 对应表 task_slo（字段：id, p95, p99, err_rate, task_id, node_id）
 */
@Entity
@Table(name = "task_slo")
public class TaskSlo {

    @Id
    private Long id;

    // p95 限制（可为空）
    @Column(name = "p95")
    private Integer p95;

    // p99 限制（可为空）
    @Column(name = "p99")
    private Integer p99;

    // 错误率限制（可为空）
    @Column(name = "err_rate")
    private Integer errRate;

    // 关联任务（可为空）
    @Column(name = "task_id")
    private Long taskId;

    // 关联节点（可为空）
    @Column(name = "node_id")
    private Long nodeId;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getP95() { return p95; }
    public void setP95(Integer p95) { this.p95 = p95; }

    public Integer getP99() { return p99; }
    public void setP99(Integer p99) { this.p99 = p99; }

    public Integer getErrRate() { return errRate; }
    public void setErrRate(Integer errRate) { this.errRate = errRate; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
}
