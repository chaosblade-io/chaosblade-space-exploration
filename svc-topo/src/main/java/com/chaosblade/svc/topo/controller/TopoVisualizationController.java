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

package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 拓扑可视化控制器
 *
 * <p>提供拓扑图可视化相关的API： 1. 图形统计信息 2. 导出功能
 */
@RestController
@RequestMapping("/api/visualization")
@CrossOrigin(origins = "*")
public class TopoVisualizationController {

  private static final Logger logger = LoggerFactory.getLogger(TopoVisualizationController.class);

  @Autowired private ObjectMapper objectMapper;

  /** 获取图形统计信息 */
  @PostMapping("/statistics")
  public ResponseEntity<Map<String, Object>> getTopologyStatistics(
      @RequestBody TopologyGraph topology) {
    logger.info("获取拓扑图统计信息");

    try {
      TopologyGraph.GraphStatistics stats = topology.getStatistics();

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("nodeCount", stats.getNodeCount());
      response.put("edgeCount", stats.getEdgeCount());
      response.put("nodeTypeCount", stats.getNodeTypeCount());
      response.put("edgeTypeCount", stats.getEdgeTypeCount());

      // 计算层级分布
      Map<String, Integer> levelDistribution = new HashMap<>();
      topology
          .getNodes()
          .forEach(
              node -> {
                String level =
                    "Level " + (node.getEntityType() != null ? node.getEntityType().getLevel() : 0);
                levelDistribution.put(level, levelDistribution.getOrDefault(level, 0) + 1);
              });
      response.put("levelDistribution", levelDistribution);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("获取拓扑图统计信息失败: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
    }
  }

  /** 导出拓扑图数据 */
  @PostMapping("/export/json")
  public ResponseEntity<Map<String, Object>> exportTopologyAsJson(
      @RequestBody TopologyGraph topology) {
    logger.info("导出拓扑图为JSON格式");

    try {
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("format", "json");
      response.put("topology", topology);
      response.put("exportedAt", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("导出拓扑数据失败: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
    }
  }

  /** 创建错误响应 */
  private Map<String, Object> createErrorResponse(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", false);
    response.put("error", message);
    response.put("timestamp", System.currentTimeMillis());

    return response;
  }
}
