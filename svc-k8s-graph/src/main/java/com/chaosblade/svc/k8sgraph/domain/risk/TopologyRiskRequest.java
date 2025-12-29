package com.chaosblade.svc.k8sgraph.domain.risk;

/**
 * K8s拓扑风险分析请求
 */
public class TopologyRiskRequest {
    
    /** 目标命名空间 */
    private String namespace;
    
    public TopologyRiskRequest() {}
    
    public TopologyRiskRequest(String namespace) {
        this.namespace = namespace;
    }
    
    // Getters and Setters
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
}

