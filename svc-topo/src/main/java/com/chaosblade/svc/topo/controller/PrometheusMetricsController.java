package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.metrics.PrometheusMetrics;
import com.chaosblade.svc.topo.service.PrometheusQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Prometheus 指标查询 API 控制器
 */
@RestController
@RequestMapping("/api/metrics")
public class PrometheusMetricsController {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusMetricsController.class);

    @Autowired
    private PrometheusQueryService prometheusQueryService;

    /**
     * 查询 Pod 指标
     * GET /api/metrics/pod/{namespace}/{podName}
     */
    @GetMapping("/pod/{namespace}/{podName}")
    public ResponseEntity<Map<String, Object>> getPodMetrics(
            @PathVariable String namespace,
            @PathVariable String podName) {

        logger.info("API request: Get Pod metrics for {}/{}", namespace, podName);

        try {
            PrometheusMetrics metrics = prometheusQueryService.queryPodMetrics(namespace, podName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("namespace", namespace);
            response.put("podName", podName);
            response.put("metrics", metrics);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get Pod metrics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to query Pod metrics");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 查询 Node 指标
     * GET /api/metrics/node/{nodeName}
     */
    @GetMapping("/node/{nodeName}")
    public ResponseEntity<Map<String, Object>> getNodeMetrics(@PathVariable String nodeName) {

        logger.info("API request: Get Node metrics for {}", nodeName);

        try {
            PrometheusMetrics metrics = prometheusQueryService.queryNodeMetrics(nodeName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("nodeName", nodeName);
            response.put("metrics", metrics);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get Node metrics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to query Node metrics");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 查询 Container 指标
     * GET /api/metrics/container/{namespace}/{podName}/{containerName}
     */
    @GetMapping("/container/{namespace}/{podName}/{containerName}")
    public ResponseEntity<Map<String, Object>> getContainerMetrics(
            @PathVariable String namespace,
            @PathVariable String podName,
            @PathVariable String containerName) {

        logger.info("API request: Get Container metrics for {}/{}/{}", namespace, podName, containerName);

        try {
            PrometheusMetrics metrics = prometheusQueryService.queryContainerMetrics(
                namespace, podName, containerName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("namespace", namespace);
            response.put("podName", podName);
            response.put("containerName", containerName);
            response.put("metrics", metrics);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get Container metrics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to query Container metrics");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

