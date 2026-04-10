package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.k8sgraph.domain.GraphData;
import com.chaosblade.svc.k8sgraph.domain.ResourceDetail;
import com.chaosblade.svc.k8sgraph.domain.ResourceRelations;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMetrics;
import com.chaosblade.svc.k8sgraph.domain.trace.TraceListResponse;
import com.chaosblade.svc.k8sgraph.service.K8sGraphService;
import com.chaosblade.svc.k8sgraph.service.K8sResourceDetailService;
import com.chaosblade.svc.k8sgraph.service.K8sResourceRelationService;
import com.chaosblade.svc.k8sgraph.service.K8sResourceYamlService;
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

    @Autowired
    private K8sResourceRelationService k8sResourceRelationService;

    @Autowired
    private K8sResourceDetailService k8sResourceDetailService;

    @Autowired
    private K8sResourceYamlService k8sResourceYamlService;

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
     * GET /api/k8s-graph/service-map?from={fromMs}&to={toMs}&namespace={namespace}
     *
     * @param from 开始时间（毫秒时间戳）
     * @param to 结束时间（毫秒时间戳）
     * @param namespace 可选，命名空间过滤，不传则返回所有命名空间
     */
    @GetMapping("/service-map")
    public ApiResponse<ServiceMapData> getServiceMap(
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(required = false) String namespace) {
        logger.info("GET /api/k8s-graph/service-map - from={}, to={}, namespace={}", from, to, namespace);

        try {
            ServiceMapData mapData = serviceMapService.getServiceMap(namespace, from, to);
            return ApiResponse.success(mapData);
        } catch (Exception e) {
            logger.error("Failed to fetch service map: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch service map: " + e.getMessage());
        }
    }

    // ========== 资源详情相关接口 ==========

    /**
     * 获取 K8s 资源基本详情
     * GET /api/k8s-graph/{resourceType}/{resourceName}?namespace={namespace}
     * 支持的资源类型：service, pod, deployment, statefulset, daemonset, node, ingress
     * namespace 参数对于 node 类型可选，其他类型必需
     */
    @GetMapping("/{resourceType}/{resourceName}")
    public ApiResponse<ResourceDetail> getResourceDetail(
            @PathVariable String resourceType,
            @PathVariable String resourceName,
            @RequestParam(required = false, defaultValue = "default") String namespace) {
        logger.info("GET /api/k8s-graph/{}/{}?namespace={}", resourceType, resourceName, namespace);

        try {
            ResourceDetail detail = k8sResourceDetailService.getResourceDetail(resourceType, resourceName, namespace);
            return ApiResponse.success(detail);
        } catch (Exception e) {
            logger.error("Failed to fetch resource detail: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch resource detail: " + e.getMessage());
        }
    }

    /**
     * 获取 K8s 资源 YAML 定义（通用接口）
     * GET /api/k8s-graph/resource/{resourceType}/{resourceName}/yaml?namespace={namespace}
     *
     * 支持的资源类型（使用 Type 标识或简写）：
     *
     * 基础设施（集群级，namespace 可选）:
     * - k8s.infra.namespace / namespace
     * - k8s.infra.node / node
     * - k8s.infra.persistentvolume / persistentvolume / pv
     * - k8s.infra.storageclass / storageclass / sc
     * - k8s.infra.clusterrole / clusterrole
     * - k8s.infra.clusterrolebinding / clusterrolebinding
     *
     * 工作负载（命名空间级）:
     * - k8s.workload.deployment / deployment
     * - k8s.workload.replicaset / replicaset / rs
     * - k8s.workload.statefulset / statefulset / sts
     * - k8s.workload.daemonset / daemonset / ds
     * - k8s.workload.job / job
     * - k8s.workload.cronjob / cronjob / cj
     * - k8s.workload.pod / pod / po
     * - k8s.workload.replicationcontroller / replicationcontroller / rc
     *
     * 网络:
     * - k8s.network.service / service / svc
     * - k8s.network.ingress / ingress / ing
     * - k8s.network.ingressclass / ingressclass（集群级）
     * - k8s.network.networkpolicy / networkpolicy / netpol
     * - k8s.network.endpoints / endpoints / ep
     * - k8s.network.endpointslice / endpointslice
     *
     * 配置与安全:
     * - k8s.config.configmap / configmap / cm
     * - k8s.config.secret / secret
     * - k8s.config.persistentvolumeclaim / persistentvolumeclaim / pvc
     * - k8s.config.serviceaccount / serviceaccount / sa
     * - k8s.config.role / role
     * - k8s.config.rolebinding / rolebinding
     * - k8s.config.limitrange / limitrange / limits
     * - k8s.config.resourcequota / resourcequota / quota
     * - k8s.config.horizontalpodautoscaler / horizontalpodautoscaler / hpa
     */
    @GetMapping("/resource/{resourceType}/{resourceName}/yaml")
    public ApiResponse<String> getResourceYaml(
            @PathVariable String resourceType,
            @PathVariable String resourceName,
            @RequestParam(required = false) String namespace) {
        logger.info("GET /api/k8s-graph/resource/{}/{}/yaml?namespace={}", resourceType, resourceName, namespace);

        try {
            String yaml = k8sResourceYamlService.getResourceYaml(resourceType, resourceName, namespace);
            if (yaml == null) {
                return ApiResponse.error("404", "Resource not found: " + resourceType + "/" + resourceName);
            }
            return ApiResponse.success(yaml);
        } catch (Exception e) {
            logger.error("Failed to fetch resource YAML: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch resource YAML: " + e.getMessage());
        }
    }

    /**
     * 获取服务 YAML 定义（保留向后兼容）
     * GET /api/k8s-graph/service/{namespace}/{serviceName}/yaml
     * @deprecated 推荐使用通用接口 /api/k8s-graph/resource/{resourceType}/{resourceName}/yaml
     */
    @GetMapping("/service/{namespace}/{serviceName}/yaml")
    public ApiResponse<String> getServiceYaml(
            @PathVariable String namespace,
            @PathVariable String serviceName) {
        logger.info("GET /api/k8s-graph/service/{}/{}/yaml (deprecated)", namespace, serviceName);

        try {
            String yaml = k8sResourceYamlService.getResourceYaml("service", serviceName, namespace);
            if (yaml == null) {
                return ApiResponse.error("404", "Service not found: " + namespace + "/" + serviceName);
            }
            return ApiResponse.success(yaml);
        } catch (Exception e) {
            logger.error("Failed to fetch service YAML: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch service YAML: " + e.getMessage());
        }
    }

    /**
     * 获取 K8s 资源关系（技术层面）
     * GET /api/k8s-graph/{resourceType}/{resourceName}/relations?namespace={namespace}
     * 支持的资源类型：service, pod, deployment, statefulset, daemonset, node, ingress
     * namespace 参数对于 node 类型可选，其他类型必需
     */
    @GetMapping("/{resourceType}/{resourceName}/relations")
    public ApiResponse<ResourceRelations> getResourceRelations(
            @PathVariable String resourceType,
            @PathVariable String resourceName,
            @RequestParam(required = false, defaultValue = "default") String namespace) {
        logger.info("GET /api/k8s-graph/{}/{}/relations?namespace={}", resourceType, resourceName, namespace);

        try {
            ResourceRelations relations = k8sResourceRelationService.getResourceRelations(resourceType, resourceName, namespace);
            return ApiResponse.success(relations);
        } catch (Exception e) {
            logger.error("Failed to fetch resource relations: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch resource relations: " + e.getMessage());
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
     * GET /api/k8s-graph/traces?serviceName={serviceName}&from={fromMs}&to={toMs}&namespace={namespace}
     */
    @GetMapping("/traces")
    public ApiResponse<TraceListResponse> getTraceList(
            @RequestParam String serviceName,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(required = false) String namespace) {
        logger.info("GET /api/k8s-graph/traces - serviceName={}, from={}, to={}, namespace={}", serviceName, from, to, namespace);

        try {
            TraceListResponse response = traceService.getTraceList(namespace, serviceName, from, to);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to fetch trace list: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to fetch trace list: " + e.getMessage());
        }
    }

    /**
     * 获取 Trace 详情（返回原始数据）
     * GET /api/k8s-graph/traces/{traceId}?serviceName={serviceName}&namespace={namespace}&from={fromMs}&to={toMs}
     */
    @GetMapping("/traces/{traceId}")
    public ApiResponse<Object> getTraceDetail(
            @PathVariable String traceId,
            @RequestParam String serviceName,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        logger.info("GET /api/k8s-graph/traces/{} - serviceName={}, namespace={}, from={}, to={}", 
            traceId, serviceName, namespace, from, to);

        try {
            // 如果未提供时间范围，使用默认值（最近1小时）
            long toMs = (to != null) ? to : System.currentTimeMillis();
            long fromMs = (from != null) ? from : toMs - 3600_000;
            
            Object trace = traceService.getTraceDetailRaw(traceId, namespace, serviceName, fromMs, toMs);
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

