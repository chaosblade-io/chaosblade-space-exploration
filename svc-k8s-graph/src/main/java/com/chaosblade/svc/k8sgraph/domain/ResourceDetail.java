package com.chaosblade.svc.k8sgraph.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * K8s 资源详情模型 - 通用的资源基本信息
 */
public class ResourceDetail {
    
    /** 资源类型（如 Service, Pod, Deployment, Node 等） */
    private String resourceType;
    
    /** 资源名称 */
    private String resourceName;
    
    /** 命名空间（某些资源如 Node 没有命名空间） */
    private String namespace;
    
    /** 资源 UID */
    private String uid;
    
    /** 创建时间 */
    private String creationTimestamp;
    
    /** 标签 */
    private Map<String, String> labels;
    
    /** 注解 */
    private Map<String, String> annotations;
    
    /** 资源状态 */
    private String status;
    
    /** 资源对应的 GraphNode */
    private GraphNode resourceNode;
    
    /** 额外属性（不同资源类型有不同的属性） */
    private Map<String, Object> properties;
    
    public ResourceDetail() {
        this.labels = new HashMap<>();
        this.annotations = new HashMap<>();
        this.properties = new HashMap<>();
    }
    
    public ResourceDetail(String resourceType, String resourceName) {
        this();
        this.resourceType = resourceType;
        this.resourceName = resourceName;
    }
    
    public ResourceDetail(String resourceType, String resourceName, String namespace) {
        this(resourceType, resourceName);
        this.namespace = namespace;
    }
    
    // Getters and Setters
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getCreationTimestamp() { return creationTimestamp; }
    public void setCreationTimestamp(String creationTimestamp) { this.creationTimestamp = creationTimestamp; }
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    public Map<String, String> getAnnotations() { return annotations; }
    public void setAnnotations(Map<String, String> annotations) { this.annotations = annotations; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public GraphNode getResourceNode() { return resourceNode; }
    public void setResourceNode(GraphNode resourceNode) { this.resourceNode = resourceNode; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    
    public void addProperty(String key, Object value) { this.properties.put(key, value); }
}

