package com.chaosblade.svc.k8sgraph.domain;

/**
 * K8s资源领域定义
 * 将K8s资源按功能职责划分为四大领域
 */
public class Domain {
    
    /** 领域唯一标识 */
    private String key;
    
    /** 显示名称 */
    private String name;
    
    /** 主题颜色 */
    private String color;
    
    /** 图标 */
    private String icon;
    
    /** 该领域下的实体数量 */
    private int entityCount;
    
    public Domain() {}
    
    public Domain(String key, String name, String color, String icon) {
        this.key = key;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.entityCount = 0;
    }
    
    // 预定义的四大领域
    public static final String DOMAIN_INFRA = "k8s.infra";
    public static final String DOMAIN_WORKLOAD = "k8s.workload";
    public static final String DOMAIN_NETWORK = "k8s.network";
    public static final String DOMAIN_CONFIG = "k8s.config";
    
    public static Domain infra() {
        return new Domain(DOMAIN_INFRA, "基础设施", "#3B82F6", "🏗️");
    }
    
    public static Domain workload() {
        return new Domain(DOMAIN_WORKLOAD, "工作负载", "#10B981", "⚙️");
    }
    
    public static Domain network() {
        return new Domain(DOMAIN_NETWORK, "网络", "#8B5CF6", "🌐");
    }
    
    public static Domain config() {
        return new Domain(DOMAIN_CONFIG, "配置与安全", "#F59E0B", "🔐");
    }

    // Getters and Setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public int getEntityCount() { return entityCount; }
    public void setEntityCount(int entityCount) { this.entityCount = entityCount; }
}

