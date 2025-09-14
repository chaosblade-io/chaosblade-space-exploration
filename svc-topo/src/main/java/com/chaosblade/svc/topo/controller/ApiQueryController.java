package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.config.ApiRequestConfig;
import com.chaosblade.svc.topo.config.SystemCatalogConfig;
import com.chaosblade.svc.topo.model.*;
import com.chaosblade.svc.topo.model.SystemApiListResponse.SystemApiDetail;
import com.chaosblade.svc.topo.model.SystemListResponse;
import com.chaosblade.svc.topo.model.SystemRootApiListResponse;
import com.chaosblade.svc.topo.model.entity.Edge;
import com.chaosblade.svc.topo.model.entity.Entity;
import com.chaosblade.svc.topo.model.entity.EntityType;
import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.model.entity.RelationType;
import com.chaosblade.svc.topo.service.ApiQueryService;
import com.chaosblade.svc.topo.service.TopologyConverterService;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.service.TopologyCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

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

    // 添加缓存服务依赖
    @Autowired
    private TopologyCacheService topologyCacheService;

    @Autowired
    private SystemCatalogConfig systemCatalogConfig;

    @Autowired
    private ApiRequestConfig apiRequestConfig;

    @Autowired
    private SystemUnderTest systemUnderTest;  // todo 目前是单例，之后有张表

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
            // 尝试从缓存中获取拓扑图
            TopologyGraph currentTopology = getTopologyFromCacheOrCurrentForApiQuery(request);

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
            // 尝试从缓存中获取拓扑图
            TopologyGraph currentTopology = getTopologyFromCacheOrCurrentForTopologyQuery(request);

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
            // 尝试从缓存中获取拓扑图
            TopologyGraph currentTopology = getTopologyFromCacheOrCurrentForMetricsQuery(request);

            MetricsByApiResponse response = apiQueryService.queryMetricsByApiId(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询性能指标失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有系统列表
     * 从系统目录中获取所有注册的系统信息
     *
     * @return 系统列表响应对象
     */
    @GetMapping("/topology/systems")
    public ResponseEntity<SystemListResponse> getSystems() {
        logger.info("收到系统列表查询请求");

        try {
            // 获取系统目录
            List<SystemInfo> systemInfos = systemCatalogConfig.getSystemCatalog();

            if (systemInfos == null) {
                logger.warn("系统目录为空");
                SystemListResponse.SystemListData data = new SystemListResponse.SystemListData(List.of(), 0);
                return ResponseEntity.ok(new SystemListResponse(true, data));
            }

            SystemListResponse.SystemListData data = new SystemListResponse.SystemListData(systemInfos, systemInfos.size());
            SystemListResponse response = new SystemListResponse(true, data);
            logger.info("返回 {} 个系统", systemInfos.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询系统列表失败: {}", e.getMessage(), e);
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
     * 将节点转换为系统API详情对象。已经过滤出类型为 RPC 的节点。
     */
    private SystemApiDetail convertNodeToSystemApiDetail(Node node) {
        SystemApiDetail detail = new SystemApiDetail();

        Entity entity = node.getEntity();
        if (entity != null) {
            // ID使用递增编号从1开始
            detail.setId(idCounter.getAndIncrement());
            detail.setSystemId(systemUnderTest.getSystemInfo().getId());
            detail.setK8sNamespace(entity.getNamespace());
            detail.setOperationId(entity.getDisplayName());

            // 从属性中拿到方法和路径信息
            Map<String, Object> attributes = entity.getAttributes();
            if (attributes != null) {
                detail.setMethod((String) attributes.getOrDefault("method", "GET"));
                detail.setPath((String) attributes.getOrDefault("path", ""));
            }

            detail.setSummary("");  // 默认无描述

            // 提取版本信息
            String path = detail.getPath();
            String version = ""; // 默认版本为空

            if (path != null && !path.isEmpty()) {
                // 按 / 拆分路径
                String[] pathParts = path.split("/");

                // 查找版本信息（v1, v2, v3 等）
                for (String part : pathParts) {
                    if (part != null && !part.isEmpty() &&
                        (part.equals("v1") || part.equals("v2") || part.equals("v3"))) {
                        version = part;
                        break; // 找到第一个版本信息就停止
                    }
                }
            }

            detail.setVersion(version);

            // 实现切 tag 的逻辑：将 path 按 / 拆成列表，过滤掉 "api"、"v1" 等词，再将非空 summary 加入列表，作为 tags
            List<String> tags = new ArrayList<>();

            if (path != null && !path.isEmpty()) {
                // 按 / 拆分路径
                String[] pathParts = path.split("/");

                // 过滤掉空字符串、"api"、"v1" 等词
                for (String part : pathParts) {
                    if (part != null && !part.isEmpty() &&
                        !"api".equals(part) && !"v1".equals(part) &&
                        !"v2".equals(part) && !"v3".equals(part)) {
                        tags.add(part);
                    }
                }
            }

            // 如果 summary 非空，也加入 tags 列表
            String summary = detail.getSummary();
            if (summary != null && !summary.isEmpty()) {
                tags.add(summary);
            }

            // 如果没有标签，则使用默认的 "api" 标签
            if (tags.isEmpty()) {
                tags.add("api");
            }

            // 将标签列表转换为 JSON 格式的字符串
            String tagsJson = tags.stream()
                .map(tag -> "\"" + tag + "\"")
                .collect(Collectors.joining(",", "[", "]"));
            detail.setTags(tagsJson);

            detail.setBaseUrl(""); // 默认基础URL
            // todo 在 RpcInterface 构造函数中提取 baseUrl，例如 http://details:9080
            detail.setCreatedAt(""); // 默认无创建时间
            detail.setUpdatedAt(""); // 默认无更新时间
        }

        return detail;
    }

    /**
     * 将 SystemApiDetail 转换为 SystemRootApiDetail
     */
    private SystemRootApiListResponse.SystemRootApiDetail convertSystemApiDetailToSystemRootApiDetail(SystemApiListResponse.SystemApiDetail apiDetail) {
        SystemRootApiListResponse.SystemRootApiDetail rootApiDetail = new SystemRootApiListResponse.SystemRootApiDetail();

        // 复制基础字段
        rootApiDetail.setId(apiDetail.getId());
        rootApiDetail.setSystemId(apiDetail.getSystemId());
        rootApiDetail.setOperationId(apiDetail.getOperationId());
        rootApiDetail.setMethod(apiDetail.getMethod());
        rootApiDetail.setPath(apiDetail.getPath());
        rootApiDetail.setSummary(apiDetail.getSummary());
        rootApiDetail.setTags(apiDetail.getTags());
        rootApiDetail.setVersion(apiDetail.getVersion());
        rootApiDetail.setBaseUrl(apiDetail.getBaseUrl());
        rootApiDetail.setCreatedAt(apiDetail.getCreatedAt());
        rootApiDetail.setUpdatedAt(apiDetail.getUpdatedAt());

        // 通过operationId查找对应的ApiRequestPayload
        Optional<ApiRequestPayload> payloadOptional = apiRequestConfig.findByOperationId(apiDetail.getOperationId());
        if (payloadOptional.isPresent()) {
            ApiRequestPayload payload = payloadOptional.get();
            rootApiDetail.setRootService(payload.getRootService());
            rootApiDetail.setRootOperation(payload.getRootOperation());

            // 设置新增字段的值
            rootApiDetail.setContentType(payload.getContentType());
            rootApiDetail.setHeadersTemplate(payload.getHeadersTemplate());
            rootApiDetail.setAuthType(payload.getAuthType());
            rootApiDetail.setAuthTemplate(payload.getAuthTemplate());
            rootApiDetail.setPathParams(payload.getPathParams());
            rootApiDetail.setQueryParams(payload.getQueryParams());
            rootApiDetail.setBodyTemplate(payload.getBodyTemplate());
            rootApiDetail.setVariables(payload.getVariables());
            rootApiDetail.setTimeoutMs(payload.getTimeoutMs());
            rootApiDetail.setRetryConfig(payload.getRetryConfig());
        }

        return rootApiDetail;
    }

    /**
     * 获取指定系统ID的根API列表
     * 根据systemId获取对应的根API列表信息，返回SystemRootApiDetail对象列表
     *
     * @param systemId 系统ID
     * @return 系统根API列表响应对象
     */
    @GetMapping("/topology/{systemId}/apis/root")
    public ResponseEntity<SystemRootApiListResponse> getRootApisBySystemId(@PathVariable("systemId") Long systemId) {
        logger.info("收到系统根API列表查询请求: systemId={}", systemId);

        try {
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                SystemRootApiListResponse.SystemRootApiListData data = new SystemRootApiListResponse.SystemRootApiListData(new ArrayList<>(), 0);
                return ResponseEntity.ok(new SystemRootApiListResponse(true, data));
            }

            // 获取所有RPC类型的节点（代表API）
            List<Node> rpcNodes = currentTopology.getNodesByType(EntityType.RPC);

            // 过滤出属于指定systemId的节点
            // 这里我们假设systemId为1时对应"default"命名空间
            final String targetNamespace = systemId == 1 ? "default" : "default"; // 根据实际需求调整映射关系

            List<SystemRootApiListResponse.SystemRootApiDetail> rootApiDetails = rpcNodes.stream()
                .filter(node -> {
                    Entity entity = node.getEntity();
                    if (entity == null) return false;

                    // 根据实体的namespace属性进行过滤
                    String namespace = entity.getNamespace();
                    return targetNamespace.equals(namespace);
                })
                .map(this::convertNodeToSystemApiDetail)
                .map(this::convertSystemApiDetailToSystemRootApiDetail)
                // 过滤出根API，根据operationId
                .filter(rootApiDetail -> systemUnderTest.getSystemInfo().getRootOperation().equals(rootApiDetail.getOperationId()))
                .collect(Collectors.toList());

            SystemRootApiListResponse.SystemRootApiListData data = new SystemRootApiListResponse.SystemRootApiListData(rootApiDetails, rootApiDetails.size());
            SystemRootApiListResponse response = new SystemRootApiListResponse(true, data);
            logger.info("返回 {} 个根API", rootApiDetails.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询系统根API列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取服务拓扑图
     * 根据根API ID获取服务级别的拓扑图，只包含Service节点和DEPENDS_ON边
     *
     * @param rootApiId 根API ID
     * @return 服务拓扑响应对象
     */
    @GetMapping("/topology/{rootApiId}/services")
    public ResponseEntity<ServiceTopologyResponse> getServiceTopology(@PathVariable("rootApiId") Long rootApiId) {
        logger.info("收到服务拓扑查询请求: rootApiId={}", rootApiId);

        try {
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                return ResponseEntity.ok(new ServiceTopologyResponse(false, null));
            }

            // 创建服务拓扑响应
            ServiceTopologyResponse response = buildServiceTopologyResponse(currentTopology, rootApiId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询服务拓扑失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 构建服务拓扑响应对象
     */
    private ServiceTopologyResponse buildServiceTopologyResponse(TopologyGraph topologyGraph, Long rootApiId) {
        // 创建响应对象
        ServiceTopologyResponse response = new ServiceTopologyResponse();
        response.setSuccess(true);

        ServiceTopologyResponse.ServiceTopologyData data = new ServiceTopologyResponse.ServiceTopologyData();

        // 创建拓扑信息
        ServiceTopologyResponse.TopologyInfo topologyInfo = new ServiceTopologyResponse.TopologyInfo();
        topologyInfo.setId(1L); // 服务拓扑ID
        topologyInfo.setSystemId(1L); // 系统ID，默认为1
        topologyInfo.setApiId(rootApiId); // 根API标识符
        topologyInfo.setDiscoveredAt(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())); // 发现时间
        topologyInfo.setCreatedAt(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())); // 创建时间

        data.setTopology(topologyInfo);

        // 过滤出Service类型的节点
        List<Node> serviceNodes = topologyGraph.getNodesByType(EntityType.SERVICE);

        // 过滤出DEPENDS_ON类型的边
        List<Edge> dependsOnEdges = topologyGraph.getEdgesByType(RelationType.DEPENDS_ON);

        // 为节点分配ID映射（用于边的转换）
        Map<String, Long> nodeKeyToIdMap = new HashMap<>();
        long nodeIdCounter = 1;
        for (Node node : serviceNodes) {
            nodeKeyToIdMap.put(node.getNodeId(), nodeIdCounter++);
        }

        // 构建邻接表用于拓扑排序
        Map<String, List<String>> adjacencyList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        // 初始化邻接表和入度
        for (Node node : serviceNodes) {
            adjacencyList.put(node.getNodeId(), new ArrayList<>());
            inDegree.put(node.getNodeId(), 0);
        }

        // 构建依赖关系图
        for (Edge edge : dependsOnEdges) {
            String fromNode = edge.getFrom();
            String toNode = edge.getTo();

            // 只处理Service节点之间的边
            if (nodeKeyToIdMap.containsKey(fromNode) && nodeKeyToIdMap.containsKey(toNode)) {
                adjacencyList.get(fromNode).add(toNode);
                inDegree.put(toNode, inDegree.get(toNode) + 1);
            }
        }

        // 执行拓扑排序计算节点层级
        Map<String, Integer> nodeLayers = calculateNodeLayers(adjacencyList, inDegree);

        // 转换节点
        List<ServiceTopologyResponse.ServiceNode> serviceNodeList = new ArrayList<>();
        long serviceNodeId = 1;
        Map<String, Long> serviceNodeIds = new HashMap<>(); // 保存新生成的节点ID映射

        for (Node node : serviceNodes) {
            ServiceTopologyResponse.ServiceNode serviceNode = new ServiceTopologyResponse.ServiceNode();
            long newId = serviceNodeId++;
            serviceNodeIds.put(node.getNodeId(), newId);

            serviceNode.setId(newId);
            serviceNode.setTopologyId(1L);
            serviceNode.setNodeKey(node.getNodeId().substring(4));
            serviceNode.setName(node.getDisplayName());
            serviceNode.setProtocol("HTTP");
            serviceNode.setLayer(nodeLayers.getOrDefault(node.getNodeId(), 1));
            serviceNodeList.add(serviceNode);
        }

        // 转换边
        List<ServiceTopologyResponse.ServiceEdge> serviceEdgeList = new ArrayList<>();
        long serviceEdgeId = 1;

        for (Edge edge : dependsOnEdges) {
            String fromNode = edge.getFrom();
            String toNode = edge.getTo();

            // 检查边的源节点和目标节点是否都在serviceNodes中
            if (serviceNodeIds.containsKey(fromNode) && serviceNodeIds.containsKey(toNode)) {
                ServiceTopologyResponse.ServiceEdge serviceEdge = new ServiceTopologyResponse.ServiceEdge();
                serviceEdge.setId(serviceEdgeId++);
                serviceEdge.setTopologyId(1L);
                serviceEdge.setFromNodeId(serviceNodeIds.get(fromNode));
                serviceEdge.setToNodeId(serviceNodeIds.get(toNode));
                serviceEdgeList.add(serviceEdge);
            }
        }

        // 更新拓扑信息中的notes字段
        topologyInfo.setNotes(String.format("{ \"totalEdges\": %d, \"totalServices\": %d}",
            serviceEdgeList.size(), serviceNodeList.size()));

        data.setNodes(serviceNodeList);
        data.setEdges(serviceEdgeList);
        response.setData(data);

        return response;
    }

    /**
     * 使用拓扑排序算法计算节点层级
     * @param adjacencyList 邻接表
     * @param inDegree 入度表
     * @return 节点层级映射
     */
    private Map<String, Integer> calculateNodeLayers(Map<String, List<String>> adjacencyList, Map<String, Integer> inDegree) {
        Map<String, Integer> nodeLayers = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        // 将所有入度为0的节点加入队列，层級設為1
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
                nodeLayers.put(entry.getKey(), 1);
            }
        }

        // 执行拓扑排序
        while (!queue.isEmpty()) {
            String currentNode = queue.poll();
            int currentLayer = nodeLayers.get(currentNode);

            // 遍历当前节点的所有邻居
            for (String neighbor : adjacencyList.get(currentNode)) {
                // 减少邻居节点的入度
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);

                // 如果邻居节点的入度变为0，将其加入队列
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                    // 邻居节点的层级为当前节点层级+1
                    nodeLayers.put(neighbor, currentLayer + 1);
                } else if (inDegree.get(neighbor) < 0) {
                    // 处理已经访问过的节点，确保层级正确
                    int existingLayer = nodeLayers.getOrDefault(neighbor, 0);
                    nodeLayers.put(neighbor, Math.max(existingLayer, currentLayer + 1));
                }
            }
        }

        // 对于仍有入度的节点（可能存在环），设置默认层级
        for (String node : inDegree.keySet()) {
            if (!nodeLayers.containsKey(node)) {
                nodeLayers.put(node, 1);
            }
        }

        return nodeLayers;
    }

    /**
     * 为API查询获取拓扑图（从缓存或当前拓扑）
     */
    private TopologyGraph getTopologyFromCacheOrCurrentForApiQuery(ApiQueryRequest request) {
        TopologyGraph currentTopology = null;

        // 如果请求包含时间范围，尝试从缓存中获取
        if (request != null && request.getTimeRange() != null) {
            Long start = request.getTimeRange().getStart();
            Long end = request.getTimeRange().getEnd();

            if (start != null && end != null) {
                currentTopology = topologyCacheService.get(start, end);
                if (currentTopology != null) {
                    logger.debug("从缓存中获取到拓扑图，时间范围: {}-{}", start, end);
                }
            }
        }

        // 如果缓存中没有找到，从当前拓扑获取
        if (currentTopology == null) {
            currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
            }
        }

        return currentTopology;
    }

    /**
     * 为拓扑查询获取拓扑图（从缓存或当前拓扑）
     */
    private TopologyGraph getTopologyFromCacheOrCurrentForTopologyQuery(TopologyByApiRequest request) {
        // TopologyByApiRequest不包含时间范围，直接从当前拓扑获取
        TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
        if (currentTopology == null) {
            logger.warn("当前拓扑图为空");
            currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
        }

        return currentTopology;
    }

    /**
     * 为指标查询获取拓扑图（从缓存或当前拓扑）
     */
    private TopologyGraph getTopologyFromCacheOrCurrentForMetricsQuery(MetricsByApiRequest request) {
        // MetricsByApiRequest也包含时间范围，可以类似处理
        if (request != null && request.getTimeRange() != null) {
            Long start = request.getTimeRange().getStart();
            Long end = request.getTimeRange().getEnd();

            if (start != null && end != null) {
                TopologyGraph cachedTopology = topologyCacheService.get(start, end);
                if (cachedTopology != null) {
                    logger.debug("从缓存中获取到拓扑图，时间范围: {}-{}", start, end);
                    return cachedTopology;
                }
            }
        }

        // 如果缓存中没有找到，从当前拓扑获取
        TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
        if (currentTopology == null) {
            logger.warn("当前拓扑图为空");
            currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
        }

        return currentTopology;
    }

    /**
     * 保存API请求负载数据到system-request.json文件
     *
     * @param payload 要保存的ApiRequestPayload对象
     * @return 保存结果响应
     */
    @PostMapping("/topology/api-request")
    public ResponseEntity<String> saveApiRequestPayload(@RequestBody ApiRequestPayload payload) {
        logger.info("收到保存API请求负载的请求: {}", payload);

        try {
            // 获取现有的API请求负载列表
            List<ApiRequestPayload> payloads = new ArrayList<>(apiRequestConfig.getApiRequestPayloads());

            // 检查是否已存在相同的operationId，如果存在则更新，否则添加
            boolean updated = false;
            for (int i = 0; i < payloads.size(); i++) {
                if (payloads.get(i).getOperationId().equals(payload.getOperationId())) {
                    payloads.set(i, payload);
                    updated = true;
                    break;
                }
            }

            if (!updated) {
                payloads.add(payload);
            }

            // 将更新后的列表写入system-request.json文件
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payloads);

            // 获取应用程序根目录下的system-request.json文件路径
            // 使用相对路径，文件将保存在项目根目录下
            File file = new File("system-request.json");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonString);
            }

            // 更新内存中的配置
            apiRequestConfig.updateOrAddApiRequestPayload(payload);

            logger.info("成功保存API请求负载到system-request.json文件");
            return ResponseEntity.ok("API请求负载保存成功");
        } catch (IOException e) {
            logger.error("保存API请求负载失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("保存API请求负载失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("保存API请求负载时发生未知错误: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("保存API请求负载时发生未知错误: " + e.getMessage());
        }
    }

    /**
     * 获取所有API请求负载
     *
     * @return API请求负载列表
     */
    @GetMapping("/topology/api-requests")
    public ResponseEntity<List<ApiRequestPayload>> getApiRequestPayloads() {
        logger.info("收到获取API请求负载列表的请求");

        try {
            List<ApiRequestPayload> payloads = apiRequestConfig.getApiRequestPayloads();
            logger.info("返回 {} 个API请求负载", payloads.size());
            return ResponseEntity.ok(payloads);
        } catch (Exception e) {
            logger.error("获取API请求负载列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
