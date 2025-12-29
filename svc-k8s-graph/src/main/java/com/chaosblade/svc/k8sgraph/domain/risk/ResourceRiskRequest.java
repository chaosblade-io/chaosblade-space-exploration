package com.chaosblade.svc.k8sgraph.domain.risk;

/**
 * K8s资源配置风险分析请求
 */
public class ResourceRiskRequest {
    
    /** K8s资源类型 (如 "deployment", "pod", "service") */
    private String resourceType;
    
    /** 资源名称 */
    private String resourceName;
    
    /** 命名空间 */
    private String namespace;
    
    public ResourceRiskRequest() {}
    
    public ResourceRiskRequest(String resourceType, String resourceName, String namespace) {
        this.resourceType = resourceType;
        this.resourceName = resourceName;
        this.namespace = namespace;
    }
    
    // Getters and Setters
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
}

