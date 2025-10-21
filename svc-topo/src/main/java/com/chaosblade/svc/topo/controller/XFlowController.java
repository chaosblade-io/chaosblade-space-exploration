/*
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

import com.chaosblade.svc.topo.service.TopologyAutoRefreshService;
import com.chaosblade.svc.topo.service.XFlowConverterService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** XFlow 拓扑可视化控制器 提供 XFlow 格式的拓扑数据接口 */
@RestController
@RequestMapping("/api/xflow")
@CrossOrigin(origins = "*")
public class XFlowController {

  private static final Logger logger = LoggerFactory.getLogger(XFlowController.class);

  @Autowired private XFlowConverterService xFlowConverterService;

  @Autowired private TopologyAutoRefreshService autoRefreshService;

  /**
   * 获取当前拓扑的 XFlow 格式数据
   *
   * @return XFlow 格式的拓扑数据
   */
  @GetMapping("/topology")
  public ResponseEntity<Map<String, Object>> getTopology() {
    try {
      logger.info("获取 XFlow 格式拓扑数据");

      Map<String, Object> xflowData = xFlowConverterService.getCurrentTopologyAsXFlow();

      if (xflowData.isEmpty()) {
        logger.warn("当前没有可用的拓扑数据");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(xflowData);
      }

      logger.info(
          "成功返回 XFlow 格式拓扑数据，节点数: {}, 边数: {}",
          ((java.util.List<?>) xflowData.get("nodes")).size(),
          ((java.util.List<?>) xflowData.get("edges")).size());

      return ResponseEntity.ok(xflowData);

    } catch (Exception e) {
      logger.error("获取 XFlow 格式拓扑数据失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "获取拓扑数据失败", "message", e.getMessage()));
    }
  }

  /**
   * 刷新拓扑数据
   *
   * @return 刷新后的 XFlow 格式拓扑数据
   */
  @PostMapping("/refresh")
  public ResponseEntity<Map<String, Object>> refreshTopology() {
    try {
      logger.info("刷新 XFlow 格式拓扑数据");

      // 刷新数据
      Map<String, Object> xflowData = xFlowConverterService.refreshAndGetXFlowData();

      logger.info("成功刷新 XFlow 格式拓扑数据");
      return ResponseEntity.ok(xflowData);

    } catch (Exception e) {
      logger.error("刷新 XFlow 格式拓扑数据失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "刷新拓扑数据失败", "message", e.getMessage()));
    }
  }

  /**
   * 获取节点详情
   *
   * @param nodeId 节点ID
   * @return 节点详细信息
   */
  @GetMapping("/nodes/{nodeId}")
  public ResponseEntity<Map<String, Object>> getNodeDetails(@PathVariable String nodeId) {
    try {
      logger.info("获取节点详情: {}", nodeId);

      Map<String, Object> nodeDetails = xFlowConverterService.getNodeDetails(nodeId);

      if (nodeDetails.isEmpty()) {
        logger.warn("未找到节点: {}", nodeId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "节点不存在", "nodeId", nodeId));
      }

      return ResponseEntity.ok(nodeDetails);

    } catch (Exception e) {
      logger.error("获取节点详情失败: {}", nodeId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "获取节点详情失败", "message", e.getMessage()));
    }
  }

  /**
   * 应用布局算法
   *
   * @param layoutRequest 布局请求参数
   * @return 应用布局后的拓扑数据
   */
  @PostMapping("/layout")
  public ResponseEntity<Map<String, Object>> applyLayout(
      @RequestBody Map<String, Object> layoutRequest) {
    try {
      String algorithm = (String) layoutRequest.get("algorithm");
      String direction = (String) layoutRequest.getOrDefault("direction", "TB");
      Map<String, Object> options =
          (Map<String, Object>) layoutRequest.getOrDefault("options", Map.of());

      logger.info("应用布局算法: {}, 方向: {}", algorithm, direction);

      Map<String, Object> layoutedData =
          xFlowConverterService.applyLayout(algorithm, direction, options);

      logger.info("成功应用布局算法: {}", algorithm);
      return ResponseEntity.ok(layoutedData);

    } catch (Exception e) {
      logger.error("应用布局算法失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "应用布局失败", "message", e.getMessage()));
    }
  }

  // ==================== 自动刷新管理接口 ====================

  /**
   * 获取自动刷新状态
   *
   * @return 自动刷新状态信息
   */
  @GetMapping("/auto-refresh/status")
  public ResponseEntity<Map<String, Object>> getAutoRefreshStatus() {
    try {
      logger.debug("获取自动刷新状态");

      TopologyAutoRefreshService.RefreshStatus status = autoRefreshService.getRefreshStatus();

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("status", status);
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("获取自动刷新状态失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("success", false, "error", "获取状态失败", "message", e.getMessage()));
    }
  }

  /**
   * 手动触发拓扑数据刷新
   *
   * @return 刷新结果
   */
  @PostMapping("/auto-refresh/trigger")
  public ResponseEntity<Map<String, Object>> triggerManualRefresh() {
    try {
      logger.info("手动触发拓扑数据刷新");

      autoRefreshService.manualRefresh();

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("message", "手动刷新完成");
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("手动刷新失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("success", false, "error", "手动刷新失败", "message", e.getMessage()));
    }
  }

  /**
   * 启用自动刷新
   *
   * @return 操作结果
   */
  @PostMapping("/auto-refresh/enable")
  public ResponseEntity<Map<String, Object>> enableAutoRefresh() {
    try {
      logger.info("启用自动刷新");

      autoRefreshService.enableAutoRefresh();

      return ResponseEntity.ok(
          Map.of("success", true, "message", "自动刷新已启用", "timestamp", System.currentTimeMillis()));

    } catch (Exception e) {
      logger.error("启用自动刷新失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("success", false, "error", "启用失败", "message", e.getMessage()));
    }
  }

  /**
   * 禁用自动刷新
   *
   * @return 操作结果
   */
  @PostMapping("/auto-refresh/disable")
  public ResponseEntity<Map<String, Object>> disableAutoRefresh() {
    try {
      logger.info("禁用自动刷新");

      autoRefreshService.disableAutoRefresh();

      return ResponseEntity.ok(
          Map.of("success", true, "message", "自动刷新已禁用", "timestamp", System.currentTimeMillis()));

    } catch (Exception e) {
      logger.error("禁用自动刷新失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("success", false, "error", "禁用失败", "message", e.getMessage()));
    }
  }

  /**
   * 更新 Jaeger 配置
   *
   * @param config Jaeger 配置参数
   * @return 操作结果
   */
  @PostMapping("/auto-refresh/config")
  public ResponseEntity<Map<String, Object>> updateJaegerConfig(
      @RequestBody Map<String, Object> config) {
    try {
      logger.info("更新 Jaeger 配置: {}", config);

      String host = (String) config.getOrDefault("host", "localhost");
      int port = (Integer) config.getOrDefault("port", 16685);
      String serviceName = (String) config.getOrDefault("serviceName", "frontend");
      String operationName = (String) config.getOrDefault("operationName", "all");
      int timeRangeMinutes = (Integer) config.getOrDefault("timeRangeMinutes", 15);

      autoRefreshService.updateJaegerConfig(
          host, port, serviceName, operationName, timeRangeMinutes);

      return ResponseEntity.ok(
          Map.of(
              "success",
              true,
              "message",
              "Jaeger 配置已更新",
              "config",
              Map.of(
                  "host", host,
                  "port", port,
                  "serviceName", serviceName,
                  "operationName", operationName,
                  "timeRangeMinutes", timeRangeMinutes),
              "timestamp",
              System.currentTimeMillis()));

    } catch (Exception e) {
      logger.error("更新 Jaeger 配置失败", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("success", false, "error", "配置更新失败", "message", e.getMessage()));
    }
  }
}
