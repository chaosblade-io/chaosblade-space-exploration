package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.k8sgraph.domain.GraphData;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceDetail;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMetrics;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceRelations;
import com.chaosblade.svc.k8sgraph.domain.trace.TraceInfo;
import com.chaosblade.svc.k8sgraph.domain.trace.TraceListResponse;
import com.chaosblade.svc.k8sgraph.service.K8sGraphService;
import com.chaosblade.svc.k8sgraph.service.ServiceDetailService;
import com.chaosblade.svc.k8sgraph.service.ServiceMapService;
import com.chaosblade.svc.k8sgraph.service.TraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * K8s资源拓扑图控制器
 *
 * API 路径：/api/k8s-graph
 */
@RestController
@RequestMapping("/api/k8s-graph")
public class K8sGraphController {

    private static final Logger logger = LoggerFactory.getLogger(K8sGraphController.class);

    @Autowired
    private K8sGraphService graphService;

    @Autowired
    private ServiceMapService serviceMapService;

    @Autowired
    private ServiceDetailService serviceDetailService;

    @Autowired
    private TraceService traceService;
    
    /**
     * 获取完整的K8s资源拓扑图
     * GET /api/k8s-graph/full
     */
    @GetMapping("/full")
    public ApiResponse<GraphData> getFullGraph() {
        logger.info("GET /api/k8s-graph/full - Fetching full K8s resource graph");
        
        try {
            GraphData graphData = graphService.getFullGraph();
            logger.info("Graph fetched successfully: {} nodes, {} edges", 
                graphData.getNodes().size(), graphData.getEdges().size());
            return ApiResponse.success(graphData);
        } catch (Exception e) {
            logger.error("Failed to fetch K8s graph: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch K8s graph: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定命名空间的资源拓扑图
     * GET /api/k8s-graph/namespace/{namespace}
     */
    @GetMapping("/namespace/{namespace}")
    public ApiResponse<GraphData> getGraphByNamespace(@PathVariable String namespace) {
        logger.info("GET /api/k8s-graph/namespace/{} - Fetching namespace resource graph", namespace);
        
        try {
            GraphData graphData = graphService.getGraphByNamespace(namespace);
            logger.info("Namespace graph fetched: {} nodes, {} edges", 
                graphData.getNodes().size(), graphData.getEdges().size());
            return ApiResponse.success(graphData);
        } catch (Exception e) {
            logger.error("Failed to fetch namespace graph: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch namespace graph: " + e.getMessage());
        }
    }
    
    /**
     * 健康检查接口
     * GET /api/k8s-graph/health
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("svc-k8s-graph is running");
    }
    
    /**
     * 测试K8s连接
     * GET /api/k8s-graph/test-connection
     */
    @GetMapping("/test-connection")
    public ApiResponse<String> testConnection() {
        logger.info("GET /api/k8s-graph/test-connection - Testing K8s connection");

        try {
            // 尝试获取命名空间列表来测试连接
            GraphData graphData = graphService.getFullGraph();
            int nodeCount = graphData.getNodes().size();
            int edgeCount = graphData.getEdges().size();
            String message = String.format("K8s connection successful. Found %d nodes and %d edges.", nodeCount, edgeCount);
            return ApiResponse.success(message);
        } catch (Exception e) {
            logger.error("K8s connection failed: {}", e.getMessage(), e);
            return ApiResponse.error("500", "K8s connection failed: " + e.getMessage());
        }
    }

    // ========== 服务调用拓扑相关接口 ==========

    /**
     * 获取包含服务调用关系的完整拓扑图
     * GET /api/k8s-graph/with-service-map?from={fromMs}&to={toMs}&namespaces={ns1,ns2}
     *
     * @param from 开始时间（毫秒时间戳）
     * @param to 结束时间（毫秒时间戳）
     * @param namespaces 可选，命名空间过滤（逗号分隔），不传则返回所有命名空间
     */
    @GetMapping("/with-service-map")
    public ApiResponse<GraphData> getGraphWithServiceMap(
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(required = false) String namespaces) {
        logger.info("GET /api/k8s-graph/with-service-map - from={}, to={}, namespaces={}", from, to, namespaces);

        try {
            GraphData graphData;
            if (namespaces != null && !namespaces.trim().isEmpty()) {
                // 解析命名空间列表
                String[] nsArray = namespaces.split(",");
                java.util.List<String> nsList = java.util.Arrays.stream(nsArray)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
                graphData = graphService.getGraphWithServiceMap(from, to, nsList);
            } else {
                graphData = graphService.getGraphWithServiceMap(from, to);
            }
            return ApiResponse.success(graphData);
        } catch (Exception e) {
            logger.error("Failed to fetch graph with service map: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch graph: " + e.getMessage());
        }
    }

    /**
     * 获取服务调用拓扑图
     * GET /api/k8s-graph/service-map?from={fromMs}&to={toMs}
     */
    @GetMapping("/service-map")
    public ApiResponse<ServiceMapData> getServiceMap(
            @RequestParam long from,
            @RequestParam long to) {
        logger.info("GET /api/k8s-graph/service-map - from={}, to={}", from, to);

        try {
            ServiceMapData mapData = serviceMapService.getServiceMap(from, to);
            return ApiResponse.success(mapData);
        } catch (Exception e) {
            logger.error("Failed to fetch service map: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch service map: " + e.getMessage());
        }
    }

    // ========== 服务详情相关接口 ==========

    /**
     * 获取服务基本详情
     * GET /api/k8s-graph/service/{namespace}/{serviceName}
     */
    @GetMapping("/service/{namespace}/{serviceName}")
    public ApiResponse<ServiceDetail> getServiceDetail(
            @PathVariable String namespace,
            @PathVariable String serviceName) {
        logger.info("GET /api/k8s-graph/service/{}/{}", namespace, serviceName);

        try {
            ServiceDetail detail = serviceDetailService.getServiceDetail(serviceName, namespace);
            return ApiResponse.success(detail);
        } catch (Exception e) {
            logger.error("Failed to fetch service detail: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch service detail: " + e.getMessage());
        }
    }

    /**
     * 获取服务 YAML 定义
     * GET /api/k8s-graph/service/{namespace}/{serviceName}/yaml
     */
    @GetMapping("/service/{namespace}/{serviceName}/yaml")
    public ApiResponse<String> getServiceYaml(
            @PathVariable String namespace,
            @PathVariable String serviceName) {
        logger.info("GET /api/k8s-graph/service/{}/{}/yaml", namespace, serviceName);

        try {
            String yaml = serviceDetailService.getServiceYaml(serviceName, namespace);
            return ApiResponse.success(yaml);
        } catch (Exception e) {
            logger.error("Failed to fetch service YAML: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch service YAML: " + e.getMessage());
        }
    }

    /**
     * 获取服务关系（技术层面 + 业务层面）
     * GET /api/k8s-graph/service/{namespace}/{serviceName}/relations
     * 不需要时间参数，K8s 资源关系相对稳定，业务关系使用默认时间范围
     */
    @GetMapping("/service/{namespace}/{serviceName}/relations")
    public ApiResponse<ServiceRelations> getServiceRelations(
            @PathVariable String namespace,
            @PathVariable String serviceName) {
        logger.info("GET /api/k8s-graph/service/{}/{}/relations", namespace, serviceName);

        try {
            ServiceRelations relations = serviceDetailService.getServiceRelations(serviceName, namespace);
            return ApiResponse.success(relations);
        } catch (Exception e) {
            logger.error("Failed to fetch service relations: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch service relations: " + e.getMessage());
        }
    }

    /**
     * 获取服务性能指标
     * GET /api/k8s-graph/service/{serviceName}/metrics?from={fromMs}&to={toMs}
     */
    @GetMapping("/service/{serviceName}/metrics")
    public ApiResponse<ServiceMetrics> getServiceMetrics(
            @PathVariable String serviceName,
            @RequestParam long from,
            @RequestParam long to) {
        logger.info("GET /api/k8s-graph/service/{}/metrics - from={}, to={}", serviceName, from, to);

        try {
            ServiceMetrics metrics = serviceDetailService.getServiceMetrics(serviceName, from, to);
            return ApiResponse.success(metrics);
        } catch (Exception e) {
            logger.error("Failed to fetch service metrics: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch service metrics: " + e.getMessage());
        }
    }

    // ========== Trace 相关接口 ==========

    /**
     * 获取服务的 Trace 列表
     * GET /api/k8s-graph/traces?serviceName={serviceName}&from={fromMs}&to={toMs}
     */
    @GetMapping("/traces")
    public ApiResponse<TraceListResponse> getTraceList(
            @RequestParam String serviceName,
            @RequestParam long from,
            @RequestParam long to) {
        logger.info("GET /api/k8s-graph/traces - serviceName={}, from={}, to={}", serviceName, from, to);

        try {
            TraceListResponse response = traceService.getTraceList(serviceName, from, to);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to fetch trace list: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch trace list: " + e.getMessage());
        }
    }

    /**
     * 获取 Trace 详情
     * GET /api/k8s-graph/traces/{traceId}
     * traceId 已经唯一标识了特定的 trace，不需要时间范围参数
     */
    @GetMapping("/traces/{traceId}")
    public ApiResponse<TraceInfo> getTraceDetail(@PathVariable String traceId) {
        logger.info("GET /api/k8s-graph/traces/{}", traceId);

        try {
            TraceInfo trace = traceService.getTraceDetail(traceId);
            if (trace == null) {
                return ApiResponse.error("404", "Trace not found: " + traceId);
            }
            return ApiResponse.success(trace);
        } catch (Exception e) {
            logger.error("Failed to fetch trace detail: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch trace detail: " + e.getMessage());
        }
    }
}

