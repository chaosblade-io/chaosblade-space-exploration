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

package com.chaosblade.svc.taskresource.dto;

import com.chaosblade.svc.taskresource.entity.ApiTopology;
import com.chaosblade.svc.taskresource.entity.ApiTopologyEdge;
import com.chaosblade.svc.taskresource.entity.ApiTopologyNode;
import java.util.List;

/** 完整拓扑信息DTO */
public class CompleteTopologyDto {

  private ApiTopology topology;
  private List<ApiTopologyNode> nodes;
  private List<ApiTopologyEdge> edges;

  // Constructors
  public CompleteTopologyDto() {}

  public CompleteTopologyDto(
      ApiTopology topology, List<ApiTopologyNode> nodes, List<ApiTopologyEdge> edges) {
    this.topology = topology;
    this.nodes = nodes;
    this.edges = edges;
  }

  // Getters and Setters
  public ApiTopology getTopology() {
    return topology;
  }

  public void setTopology(ApiTopology topology) {
    this.topology = topology;
  }

  public List<ApiTopologyNode> getNodes() {
    return nodes;
  }

  public void setNodes(List<ApiTopologyNode> nodes) {
    this.nodes = nodes;
  }

  public List<ApiTopologyEdge> getEdges() {
    return edges;
  }

  public void setEdges(List<ApiTopologyEdge> edges) {
    this.edges = edges;
  }
}
