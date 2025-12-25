package com.chaosblade.svc.k8sgraph.domain;

/**
 * 节点状态枚举
 */
public enum NodeStatus {
    
    RUNNING("running", "#10B981", "运行中"),
    WARNING("warning", "#F59E0B", "警告"),
    ERROR("error", "#EF4444", "错误"),
    PENDING("pending", "#6B7280", "等待中"),
    TERMINATED("terminated", "#9CA3AF", "已终止");
    
    private final String value;
    private final String color;
    private final String displayName;
    
    NodeStatus(String value, String color, String displayName) {
        this.value = value;
        this.color = color;
        this.displayName = displayName;
    }
    
    public String getValue() { return value; }
    public String getColor() { return color; }
    public String getDisplayName() { return displayName; }
    
    public static NodeStatus fromValue(String value) {
        for (NodeStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PENDING;
    }
}

