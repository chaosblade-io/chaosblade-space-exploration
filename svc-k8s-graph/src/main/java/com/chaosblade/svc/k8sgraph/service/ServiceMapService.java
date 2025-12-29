package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.client.ObservabilityApiClient;
import com.chaosblade.svc.k8sgraph.domain.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 服务拓扑图服务 - 处理服务间调用关系
 */
@Service
public class ServiceMapService {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceMapService.class);
    
    @Autowired
    private ObservabilityApiClient apiClient;
    
    /**
     * 获取服务拓扑图数据
     */
    public ServiceMapData getServiceMap(long fromMs, long toMs) {
        logger.info("Getting service map: from={}, to={}", fromMs, toMs);
        
        JsonNode mapArray = apiClient.getServiceMap(fromMs, toMs);
        ServiceMapData mapData = parseServiceMap(mapArray);
        mapData.setFromTime(fromMs);
        mapData.setToTime(toMs);
        
        return mapData;
    }
    
    /**
     * 获取服务的上下游依赖
     */
    public Map<String, List<String>> getServiceDependencies(String serviceName, long fromMs, long toMs) {
        ServiceMapData mapData = getServiceMap(fromMs, toMs);
        
        List<String> upstream = new ArrayList<>();
        List<String> downstream = new ArrayList<>();
        
        for (ServiceMapEdge edge : mapData.getEdges()) {
            if (edge.getTargetService().equals(serviceName)) {
                upstream.add(edge.getSourceService());
            }
            if (edge.getSourceService().equals(serviceName)) {
                downstream.add(edge.getTargetService());
            }
        }
        
        Map<String, List<String>> result = new HashMap<>();
        result.put("upstream", upstream);
        result.put("downstream", downstream);
        return result;
    }
    
    /**
     * 解析服务拓扑图 - 适配 Coroot API 返回格式
     * Coroot 返回的格式是节点数组，每个节点包含 id, upstreams, downstreams
     */
    private ServiceMapData parseServiceMap(JsonNode mapArray) {
        ServiceMapData mapData = new ServiceMapData();
        Map<String, ServiceMapNode> nodeMap = new HashMap<>();
        Set<String> edgeKeys = new HashSet<>(); // 用于去重边

        if (mapArray == null || !mapArray.isArray()) {
            logger.warn("Service map data is null or not an array");
            return mapData;
        }

        logger.info("Parsing service map with {} nodes", mapArray.size());

        // 第一遍：创建所有节点
        for (JsonNode node : mapArray) {
            String nodeId = node.path("id").asText("");
            if (nodeId.isEmpty()) continue;

            String serviceName = extractServiceName(nodeId);
            String status = node.path("status").asText("unknown");
            String namespace = extractNamespace(nodeId);

            ServiceMapNode mapNode = nodeMap.computeIfAbsent(serviceName, ServiceMapNode::new);
            mapNode.setStatus(status);
            mapNode.setNamespace(namespace);
        }

        // 第二遍：解析边关系（从 upstreams 和 downstreams 中提取）
        for (JsonNode node : mapArray) {
            String nodeId = node.path("id").asText("");
            if (nodeId.isEmpty()) continue;

            String targetService = extractServiceName(nodeId);

            // 解析 upstreams（上游调用当前服务）
            JsonNode upstreams = node.path("upstreams");
            if (upstreams.isArray()) {
                for (JsonNode upstream : upstreams) {
                    String upstreamId = upstream.path("id").asText("");
                    if (upstreamId.isEmpty()) continue;

                    String sourceService = extractServiceName(upstreamId);
                    String edgeKey = sourceService + "->" + targetService;

                    // 当前服务的 upstream 意味着 upstream -> 当前服务
                    if (!edgeKeys.contains(edgeKey)) {
                        edgeKeys.add(edgeKey);
                        ServiceMapEdge edge = new ServiceMapEdge(sourceService, targetService);
                        parseEdgeStats(edge, upstream);
                        mapData.addEdge(edge);
                    }
                }
            }

            // 解析 downstreams（当前服务调用下游）
            JsonNode downstreams = node.path("downstreams");
            if (downstreams.isArray()) {
                for (JsonNode downstream : downstreams) {
                    String downstreamId = downstream.path("id").asText("");
                    if (downstreamId.isEmpty()) continue;

                    String targetDownstream = extractServiceName(downstreamId);
                    String edgeKey = targetService + "->" + targetDownstream;

                    // 当前服务 -> downstream
                    if (!edgeKeys.contains(edgeKey)) {
                        edgeKeys.add(edgeKey);
                        ServiceMapEdge edge = new ServiceMapEdge(targetService, targetDownstream);
                        parseEdgeStats(edge, downstream);
                        mapData.addEdge(edge);
                    }
                }
            }
        }

        // 添加所有节点到 mapData
        for (ServiceMapNode mapNode : nodeMap.values()) {
            // 计算该服务的聚合指标
            calculateNodeMetrics(mapNode, mapData.getEdges());
            mapData.addNode(mapNode);
        }

        logger.info("Parsed service map: {} nodes, {} edges",
            mapData.getNodes().size(), mapData.getEdges().size());

        return mapData;
    }

    /**
     * 从节点ID中提取服务名称
     * 格式: namespace:Type:service-name
     */
    private String extractServiceName(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return "unknown";
        String[] parts = nodeId.split(":");
        if (parts.length >= 3) {
            return parts[2]; // 返回服务名
        } else if (parts.length >= 1) {
            return parts[parts.length - 1];
        }
        return nodeId;
    }

    /**
     * 从节点ID中提取命名空间
     */
    private String extractNamespace(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return "default";
        String[] parts = nodeId.split(":");
        if (parts.length >= 1) {
            return parts[0];
        }
        return "default";
    }

    /**
     * 解析边的统计信息（从 stats 数组解析）
     */
    private void parseEdgeStats(ServiceMapEdge edge, JsonNode relation) {
        JsonNode stats = relation.path("stats");
        if (stats.isArray()) {
            for (JsonNode stat : stats) {
                String statStr = stat.asText("");
                // 解析格式如: "📈 0.3 rps ⏱️ 5ms" 或 "↑76B/s ↓148B/s"
                if (statStr.contains("rps")) {
                    // 解析 rps
                    try {
                        String rpsStr = statStr.replaceAll("[^0-9.]", " ").trim().split("\\s+")[0];
                        double rps = Double.parseDouble(rpsStr);
                        edge.setCallCount((long) (rps * 3600)); // 转换为1小时的调用次数
                    } catch (Exception e) {
                        // 忽略解析错误
                    }
                }
                if (statStr.contains("ms")) {
                    // 解析延迟
                    try {
                        int msIndex = statStr.indexOf("ms");
                        String beforeMs = statStr.substring(0, msIndex).trim();
                        String[] parts = beforeMs.split("\\s+");
                        String latencyStr = parts[parts.length - 1].replaceAll("[^0-9.]", "");
                        edge.setAvgLatency(Double.parseDouble(latencyStr));
                    } catch (Exception e) {
                        // 忽略解析错误
                    }
                }
            }
        }

        // 从 status 推断是否有错误
        String status = relation.path("status").asText("ok");
        if ("critical".equals(status) || "error".equals(status)) {
            edge.setErrorCount(1L);
            if (edge.getCallCount() != null && edge.getCallCount() > 0) {
                edge.setErrorRate(100.0 / edge.getCallCount());
            }
        }
    }
    
    /**
     * 计算节点的聚合指标
     */
    private void calculateNodeMetrics(ServiceMapNode node, List<ServiceMapEdge> edges) {
        long totalRequests = 0;
        long totalErrors = 0;
        double totalLatency = 0;
        int latencyCount = 0;
        
        for (ServiceMapEdge edge : edges) {
            if (edge.getTargetService().equals(node.getServiceName())) {
                if (edge.getCallCount() != null) totalRequests += edge.getCallCount();
                if (edge.getErrorCount() != null) totalErrors += edge.getErrorCount();
                if (edge.getAvgLatency() != null) {
                    totalLatency += edge.getAvgLatency();
                    latencyCount++;
                }
            }
        }
        
        node.setRequestCount(totalRequests);
        node.setErrorCount(totalErrors);
        if (totalRequests > 0) {
            node.setErrorRate((double) totalErrors / totalRequests * 100);
        }
        if (latencyCount > 0) {
            node.setAvgLatency(totalLatency / latencyCount);
        }
    }
    
}

