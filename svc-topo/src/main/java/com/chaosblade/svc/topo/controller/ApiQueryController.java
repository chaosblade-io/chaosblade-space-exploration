package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.ApiQueryRequest;
import com.chaosblade.svc.topo.model.ApiQueryResponse;
import com.chaosblade.svc.topo.model.MetricsByApiRequest;
import com.chaosblade.svc.topo.model.MetricsByApiResponse;
import com.chaosblade.svc.topo.model.NamespacesResponse;
import com.chaosblade.svc.topo.model.NamespaceDetail;
import com.chaosblade.svc.topo.model.NamespaceListResponse;
import com.chaosblade.svc.topo.model.SystemApiListResponse;
import com.chaosblade.svc.topo.model.TopologyByApiRequest;
import com.chaosblade.svc.topo.model.SystemApiListResponse.SystemApiDetail;
import com.chaosblade.svc.topo.model.entity.Entity;
import com.chaosblade.svc.topo.model.entity.EntityType;
import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.service.ApiQueryService;
import com.chaosblade.svc.topo.service.TopologyConverterService;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API查询控制器
 * 提供查询指定命名空间、指定时间范围内API列表的功能
 */
@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "*")
public class ApiQueryController {

    private static final Logger logger = LoggerFactory.getLogger(ApiQueryController.class);

    @Autowired
    private ApiQueryService apiQueryService;

    @Autowired
    private TopologyConverterService topologyConverterService;

    /**
     * 查询API列表
     * 获取指定命名空间、指定时间范围内的API列表
     *
     * @param request API查询请求对象
     * @return API查询响应对象
     */
    @PostMapping("/apis/end2end")
    public ResponseEntity<ApiQueryResponse> queryApis(@RequestBody ApiQueryRequest request) {
        logger.info("收到API查询请求: namespace={}, services={}",
            request.getNamespace(),
            request.getAppSelector() != null ? request.getAppSelector().getServices() : "all");

        try {
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
            }

            ApiQueryResponse response = apiQueryService.queryApisFromTopology(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询API列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据API ID查询拓扑信息
     * 返回该节点的唯一上游节点，和所有下游节点，以及相关的边
     * 需要先通过 /apis/end2end 拿到正确的 apiId。
     *
     * @param request 拓扑查询请求对象
     * @return 包含指定API节点上下游信息的拓扑图
     */
    @PostMapping("/topology/byapi")
    public ResponseEntity<TopologyGraph> queryTopologyByApi(@RequestBody TopologyByApiRequest request) {
        logger.info("收到拓扑查询请求: namespace={}, apiId={}",
            request.getNamespace(),
            request.getApiId());

        try {
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
            }

            TopologyGraph response = apiQueryService.queryTopologyByApiId(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询拓扑信息失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据API ID查询性能指标
     * 返回根API的性能数据：错误率、吞吐量、p50/p95/p99
     *
     * @param request 指标查询请求对象
     * @return 性能指标响应对象
     */
    @PostMapping("/metrics/byapi")
    public ResponseEntity<MetricsByApiResponse> queryMetricsByApi(@RequestBody MetricsByApiRequest request) {
        logger.info("收到指标查询请求: apiId={}", request.getApiId());

        try {
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
            }

            MetricsByApiResponse response = apiQueryService.queryMetricsByApiId(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询性能指标失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有命名空间列表
     * 从当前拓扑图中过滤出EntityType为NAMESPACE的节点，并返回它们的显示名称列表
     *
     * @return 命名空间列表响应对象
     */
    @GetMapping("/topology/namespaces")
    public ResponseEntity<NamespaceListResponse> getNamespaces() {
        logger.info("收到命名空间列表查询请求");

        try {
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                NamespaceListResponse.NamespaceListData data = new NamespaceListResponse.NamespaceListData(List.of(), 0);
                return ResponseEntity.ok(new NamespaceListResponse(true, data)); // 返回空列表而不是null
            }

            // 过滤出EntityType为NAMESPACE的节点，并提取显示名称
            List<String> namespaceNames = currentTopology.getNodes().stream()
                    .filter(node -> EntityType.NAMESPACE.equals(node.getEntityType()))
                    .map(Node::getDisplayName)
                    .collect(Collectors.toList());

            // 转换为NamespaceDetail对象列表
            List<NamespaceDetail> namespaceDetails = new ArrayList<>();
            NamespaceDetail TrainTicket = new NamespaceDetail(
                    (long) 1,           // id
                    "train-ticket",           // systemKey
                    "default",                // k8sNamespace
                    "订票系统",                     // name
                    "火车票订票系统（被测系统）",    // description
                    "admin",                  // owner
                    "prod"                    // defaultEnvironment
            );
            namespaceDetails.add(TrainTicket);

            NamespaceListResponse.NamespaceListData data = new NamespaceListResponse.NamespaceListData(namespaceDetails, namespaceDetails.size());
            NamespaceListResponse response = new NamespaceListResponse(true, data);
            logger.info("返回 {} 个命名空间", namespaceDetails.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询命名空间列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // 添加一个静态计数器用于生成递增的ID
    private static final java.util.concurrent.atomic.AtomicLong idCounter = new java.util.concurrent.atomic.AtomicLong(1);

    /**
     * 获取指定系统ID的API列表
     * 根据systemId获取对应的API列表信息
     *
     * @param systemId 系统ID
     * @return 系统API列表响应对象
     */
    @GetMapping("/topology/{systemId}/apis")
    public ResponseEntity<SystemApiListResponse> getApisBySystemId(@PathVariable("systemId") Long systemId) {
        logger.info("收到系统API列表查询请求: systemId={}", systemId);

        // 重置计数器，确保每次请求都从1开始编号
        idCounter.set(1);

        try {
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                SystemApiListResponse.SystemApiListData data = new SystemApiListResponse.SystemApiListData(new ArrayList<>(), 0);
                return ResponseEntity.ok(new SystemApiListResponse(true, data));
            }

            // 获取所有RPC类型的节点（代表API）
            List<Node> rpcNodes = currentTopology.getNodesByType(EntityType.RPC);
            
            // 过滤出属于指定systemId的节点
            // 这里我们假设systemId为1时对应"default"命名空间
            final String targetNamespace = systemId == 1 ? "default" : "default"; // 根据实际需求调整映射关系
            
            List<SystemApiDetail> apiDetails = rpcNodes.stream()
                .filter(node -> {
                    Entity entity = node.getEntity();
                    if (entity == null) return false;
                    
                    // 根据实体的namespace属性进行过滤
                    String namespace = entity.getNamespace();
                    return targetNamespace.equals(namespace);
                })
                .map(this::convertNodeToSystemApiDetail)
                .collect(Collectors.toList());

            SystemApiListResponse.SystemApiListData data = new SystemApiListResponse.SystemApiListData(apiDetails, apiDetails.size());
            SystemApiListResponse response = new SystemApiListResponse(true, data);
            logger.info("返回 {} 个API", apiDetails.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询系统API列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 将节点转换为系统API详情对象
     */
    private SystemApiDetail convertNodeToSystemApiDetail(Node node) {
        SystemApiDetail detail = new SystemApiDetail();
        
        Entity entity = node.getEntity();
        if (entity != null) {
            // ID使用递增编号从1开始
            detail.setId(idCounter.getAndIncrement());
            detail.setSystemId(1L); // 默认系统ID
            detail.setK8sNamespace(entity.getNamespace());
            detail.setOperationId(entity.getDisplayName());
            
            // 从属性中提取方法和路径信息
            Map<String, Object> attributes = entity.getAttributes();
            if (attributes != null) {
                detail.setMethod((String) attributes.getOrDefault("method", "GET"));
                detail.setPath((String) attributes.getOrDefault("path", ""));
            } else {
                detail.setMethod("GET");
                detail.setPath("");
            }
            
            detail.setSummary(entity.getDisplayName());
            detail.setTags("[\"api\"]"); // 默认标签
            detail.setBaseUrl("http://localhost:8080"); // 默认基础URL
            detail.setCreatedAt("2025-09-12 10:00:00"); // 默认创建时间
            detail.setUpdatedAt("2025-09-12 10:00:00"); // 默认更新时间
        }
        
        return detail;
    }
}
