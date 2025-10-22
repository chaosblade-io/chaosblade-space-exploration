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

package com.chaosblade.svc.taskresource.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.dto.CompleteTopologyDto;
import com.chaosblade.svc.taskresource.entity.ApiTopology;
import com.chaosblade.svc.taskresource.entity.ApiTopologyNode;
import com.chaosblade.svc.taskresource.service.ApiTopologyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 拓扑管理控制器 */
@RestController
@RequestMapping("/api")
public class TopologyController {

  private static final Logger logger = LoggerFactory.getLogger(TopologyController.class);

  @Autowired private ApiTopologyService apiTopologyService;

  /** 获取拓扑详情 GET /api/topologies/{topologyId} */
  @GetMapping("/topologies/{topologyId}")
  public ApiResponse<ApiTopology> getTopologyById(@PathVariable Long topologyId) {
    logger.info("GET /api/topologies/{}", topologyId);

    ApiTopology topology = apiTopologyService.getTopologyById(topologyId);
    return ApiResponse.success(topology);
  }

  /** 更新拓扑信息 PUT /api/topologies/{topologyId} */
  @PutMapping("/topologies/{topologyId}")
  public ApiResponse<ApiTopology> updateTopology(
      @PathVariable Long topologyId, @Valid @RequestBody ApiTopology topology) {
    logger.info("PUT /api/topologies/{}", topologyId);

    ApiTopology updatedTopology = apiTopologyService.updateTopology(topologyId, topology);
    return ApiResponse.success(updatedTopology);
  }

  /** 删除拓扑 DELETE /api/topologies/{topologyId} */
  @DeleteMapping("/topologies/{topologyId}")
  public ApiResponse<Void> deleteTopology(@PathVariable Long topologyId) {
    logger.info("DELETE /api/topologies/{}", topologyId);

    apiTopologyService.deleteTopology(topologyId);
    return ApiResponse.success();
  }

  /** 获取拓扑的节点列表 GET /api/topologies/{topologyId}/nodes */
  @GetMapping("/topologies/{topologyId}/nodes")
  public ApiResponse<PageResponse<ApiTopologyNode>> getTopologyNodes(
      @PathVariable Long topologyId,
      @RequestParam(value = "protocol", required = false) String protocol,
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "layer", required = false) Integer layer,
      @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(value = "size", defaultValue = "50") @Min(1) int size) {

    logger.info(
        "GET /api/topologies/{}/nodes - protocol: {}, name: {}, layer: {}, page: {}, size: {}",
        topologyId,
        protocol,
        name,
        layer,
        page,
        size);

    PageResponse<ApiTopologyNode> nodes =
        apiTopologyService.getTopologyNodes(topologyId, protocol, name, layer, page, size);
    return ApiResponse.success(nodes);
  }

  /** 根据API ID获取完整拓扑信息 GET /api/apis/{apiId}/topology */
  @GetMapping("/topologies/{apiId}/topology")
  public ApiResponse<CompleteTopologyDto> getCompleteTopologyByApiId(@PathVariable Long apiId) {
    logger.info("GET /api/apis/{}/topology", apiId);

    CompleteTopologyDto completeTopology = apiTopologyService.getCompleteTopologyByApiId(apiId);
    return ApiResponse.success(completeTopology);
  }
}
