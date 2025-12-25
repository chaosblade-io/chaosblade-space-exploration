package com.chaosblade.svc.k8sgraph.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图数据模型
 * 包含节点、边和领域定义
 */
public class GraphData {
    
    /** 所有节点 */
    private List<GraphNode> nodes;
    
    /** 所有边 */
    private List<GraphEdge> edges;
    
    /** 领域定义 */
    private List<Domain> domains;
    
    /** 统计信息 */
    private GraphStats stats;
    
    public GraphData() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.domains = new ArrayList<>();
        this.stats = new GraphStats();
    }
    
    public void addNode(GraphNode node) {
        this.nodes.add(node);
    }
    
    public void addEdge(GraphEdge edge) {
        this.edges.add(edge);
    }
    
    public void addDomain(Domain domain) {
        this.domains.add(domain);
    }
    
    /**
     * 计算统计信息
     */
    public void calculateStats() {
        stats.setTotalNodes(nodes.size());
        stats.setTotalEdges(edges.size());
        
        // 按领域统计
        Map<String, Integer> domainStats = new HashMap<>();
        Map<String, Integer> statusStats = new HashMap<>();
        Map<String, Integer> namespaceStats = new HashMap<>();
        
        for (GraphNode node : nodes) {
            // 领域统计
            domainStats.merge(node.getDomain(), 1, Integer::sum);
            // 状态统计
            if (node.getStatus() != null) {
                statusStats.merge(node.getStatus().getValue(), 1, Integer::sum);
            }
            // 命名空间统计
            if (node.getNamespace() != null && !node.getNamespace().isEmpty()) {
                namespaceStats.merge(node.getNamespace(), 1, Integer::sum);
            }
        }
        
        stats.setDomainStats(domainStats);
        stats.setStatusStats(statusStats);
        stats.setNamespaceStats(namespaceStats);
        
        // 更新领域实体数量
        for (Domain domain : domains) {
            domain.setEntityCount(domainStats.getOrDefault(domain.getKey(), 0));
        }
    }

    // Getters and Setters
    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> nodes) { this.nodes = nodes; }
    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> edges) { this.edges = edges; }
    public List<Domain> getDomains() { return domains; }
    public void setDomains(List<Domain> domains) { this.domains = domains; }
    public GraphStats getStats() { return stats; }
    public void setStats(GraphStats stats) { this.stats = stats; }
}

