package com.chaosblade.svc.k8sgraph.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * 图节点模型
 * 表示K8s中的一个资源
 */
public class GraphNode {
    
    /** 节点唯一标识：{type}/{namespace}/{name} */
    private String id;
    
    /** 资源类型，如 k8s.workload.pod */
    private String type;
    
    /** 所属领域，如 k8s.workload */
    private String domain;
    
    /** 资源名称 */
    private String name;
    
    /** 命名空间（集群级资源可为空） */
    private String namespace;
    
    /** K8s标签 */
    private Map<String, String> labels;
    
    /** 扩展属性（资源特有属性） */
    private Map<String, Object> properties;
    
    /** 节点状态 */
    private NodeStatus status;
    
    public GraphNode() {
        this.labels = new HashMap<>();
        this.properties = new HashMap<>();
        this.status = NodeStatus.PENDING;
    }
    
    /**
     * 根据资源信息生成节点ID
     * 格式：{type}/{namespace}/{name} 或 {type}//{name}（集群级资源）
     */
    public static String generateId(String type, String namespace, String name) {
        if (namespace == null || namespace.isEmpty()) {
            return type + "//" + name;
        }
        return type + "/" + namespace + "/" + name;
    }
    
    /**
     * 从领域和资源类型构建完整类型标识
     */
    public static String buildType(String domain, String resourceType) {
        return domain + "." + resourceType;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
    
    public void addProperty(String key, Object value) {
        this.properties.put(key, value);
    }
}

