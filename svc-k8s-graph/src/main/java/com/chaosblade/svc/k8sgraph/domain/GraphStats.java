package com.chaosblade.svc.k8sgraph.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * 图统计信息
 */
public class GraphStats {
    
    /** 节点总数 */
    private int totalNodes;
    
    /** 边总数 */
    private int totalEdges;
    
    /** 按领域统计 */
    private Map<String, Integer> domainStats;
    
    /** 按状态统计 */
    private Map<String, Integer> statusStats;
    
    /** 按命名空间统计 */
    private Map<String, Integer> namespaceStats;
    
    public GraphStats() {
        this.domainStats = new HashMap<>();
        this.statusStats = new HashMap<>();
        this.namespaceStats = new HashMap<>();
    }

    // Getters and Setters
    public int getTotalNodes() { return totalNodes; }
    public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }
    public int getTotalEdges() { return totalEdges; }
    public void setTotalEdges(int totalEdges) { this.totalEdges = totalEdges; }
    public Map<String, Integer> getDomainStats() { return domainStats; }
    public void setDomainStats(Map<String, Integer> domainStats) { this.domainStats = domainStats; }
    public Map<String, Integer> getStatusStats() { return statusStats; }
    public void setStatusStats(Map<String, Integer> statusStats) { this.statusStats = statusStats; }
    public Map<String, Integer> getNamespaceStats() { return namespaceStats; }
    public void setNamespaceStats(Map<String, Integer> namespaceStats) { this.namespaceStats = namespaceStats; }
}

