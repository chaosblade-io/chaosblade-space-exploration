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
     * 解析服务拓扑图
     */
    private ServiceMapData parseServiceMap(JsonNode mapArray) {
        ServiceMapData mapData = new ServiceMapData();
        Set<String> serviceNames = new HashSet<>();
        
        if (mapArray == null || !mapArray.isArray()) {
            return mapData;
        }
        
        for (JsonNode node : mapArray) {
            // 解析边关系
            ServiceMapEdge edge = parseEdge(node);
            if (edge != null) {
                mapData.addEdge(edge);
                serviceNames.add(edge.getSourceService());
                serviceNames.add(edge.getTargetService());
            }
        }
        
        // 根据边信息创建节点
        for (String serviceName : serviceNames) {
            ServiceMapNode mapNode = new ServiceMapNode(serviceName);
            // 计算该服务的聚合指标
            calculateNodeMetrics(mapNode, mapData.getEdges());
            mapData.addNode(mapNode);
        }
        
        return mapData;
    }
    
    /**
     * 解析边信息
     */
    private ServiceMapEdge parseEdge(JsonNode node) {
        String source = getTextValue(node, "source", "Source", "from", "caller");
        String target = getTextValue(node, "target", "Target", "to", "callee");
        
        if (source == null || target == null) {
            return null;
        }
        
        ServiceMapEdge edge = new ServiceMapEdge(source, target);
        edge.setCallCount(getLongValue(node, "callCount", "call_count", "count", "requests"));
        edge.setErrorCount(getLongValue(node, "errorCount", "error_count", "errors"));
        edge.setAvgLatency(getDoubleValue(node, "avgLatency", "avg_latency", "latency", "avgDuration"));
        edge.setP99Latency(getDoubleValue(node, "p99Latency", "p99_latency", "p99"));
        
        // 计算错误率
        if (edge.getCallCount() != null && edge.getCallCount() > 0 && edge.getErrorCount() != null) {
            edge.setErrorRate((double) edge.getErrorCount() / edge.getCallCount() * 100);
        }
        
        return edge;
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
    
    // 辅助方法
    private String getTextValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return null;
    }
    
    private Long getLongValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asLong();
            }
        }
        return null;
    }
    
    private Double getDoubleValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asDouble();
            }
        }
        return null;
    }
}

