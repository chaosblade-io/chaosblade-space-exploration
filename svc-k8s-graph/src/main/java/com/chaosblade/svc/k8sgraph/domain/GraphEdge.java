package com.chaosblade.svc.k8sgraph.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图边模型
 * 描述资源间的依赖和管理关系
 */
public class GraphEdge {
    
    /** 边唯一标识 */
    private String id;
    
    /** 边类型（关系类型） */
    private EdgeType type;
    
    /** 源节点ID */
    private String source;
    
    /** 目标节点ID */
    private String target;
    
    /** 扩展属性 */
    private Map<String, Object> properties;
    
    public GraphEdge() {
        this.id = UUID.randomUUID().toString();
        this.properties = new HashMap<>();
    }
    
    public GraphEdge(EdgeType type, String source, String target) {
        this();
        this.type = type;
        this.source = source;
        this.target = target;
    }
    
    /**
     * 创建边的工厂方法
     */
    public static GraphEdge create(EdgeType type, String sourceId, String targetId) {
        return new GraphEdge(type, sourceId, targetId);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public EdgeType getType() { return type; }
    public void setType(EdgeType type) { this.type = type; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    
    public void addProperty(String key, Object value) {
        this.properties.put(key, value);
    }
}

