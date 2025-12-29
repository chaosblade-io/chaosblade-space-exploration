package com.chaosblade.svc.k8sgraph.domain.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务拓扑图数据模型
 */
public class ServiceMapData {

    /** 命名空间（如果指定了过滤） */
    private String namespace;

    /** 服务节点列表 */
    private List<ServiceMapNode> nodes;

    /** 服务调用边列表 */
    private List<ServiceMapEdge> edges;

    /** 时间区间开始 (毫秒时间戳) */
    private Long fromTime;

    /** 时间区间结束 (毫秒时间戳) */
    private Long toTime;
    
    public ServiceMapData() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }
    
    public void addNode(ServiceMapNode node) {
        this.nodes.add(node);
    }
    
    public void addEdge(ServiceMapEdge edge) {
        this.edges.add(edge);
    }
    
    // Getters and Setters
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public List<ServiceMapNode> getNodes() { return nodes; }
    public void setNodes(List<ServiceMapNode> nodes) { this.nodes = nodes; }
    public List<ServiceMapEdge> getEdges() { return edges; }
    public void setEdges(List<ServiceMapEdge> edges) { this.edges = edges; }
    public Long getFromTime() { return fromTime; }
    public void setFromTime(Long fromTime) { this.fromTime = fromTime; }
    public Long getToTime() { return toTime; }
    public void setToTime(Long toTime) { this.toTime = toTime; }
}

