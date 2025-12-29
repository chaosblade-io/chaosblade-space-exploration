package com.chaosblade.svc.k8sgraph.domain.risk;

import java.util.HashMap;
import java.util.Map;

/**
 * 推荐的故障注入建议
 */
public class RecommendedFault {
    
    /** 故障类型（如：CPU压力、内存压力、网络延迟等） */
    private String faultType;
    
    /** ChaosBlade 故障代码 */
    private String faultCode;
    
    /** 故障名称 */
    private String faultName;
    
    /** 故障描述和验证目标 */
    private String description;
    
    /** 优先级（1最高） */
    private int priority;
    
    /** 目标资源类型 */
    private String targetResourceType;
    
    /** 目标资源名称 */
    private String targetResourceName;
    
    /** 目标命名空间 */
    private String targetNamespace;
    
    /** 故障注入参数 */
    private Map<String, Object> parameters;
    
    public RecommendedFault() {
        this.parameters = new HashMap<>();
    }
    
    public RecommendedFault(String faultCode, String description, int priority) {
        this();
        this.faultCode = faultCode;
        this.faultType = FaultType.getDisplayName(faultCode);
        this.faultName = this.faultType;
        this.description = description;
        this.priority = priority;
    }
    
    // Getters and Setters
    public String getFaultType() { return faultType; }
    public void setFaultType(String faultType) { this.faultType = faultType; }
    
    public String getFaultCode() { return faultCode; }
    public void setFaultCode(String faultCode) { this.faultCode = faultCode; }
    
    public String getFaultName() { return faultName; }
    public void setFaultName(String faultName) { this.faultName = faultName; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    public String getTargetResourceType() { return targetResourceType; }
    public void setTargetResourceType(String targetResourceType) { this.targetResourceType = targetResourceType; }
    
    public String getTargetResourceName() { return targetResourceName; }
    public void setTargetResourceName(String targetResourceName) { this.targetResourceName = targetResourceName; }
    
    public String getTargetNamespace() { return targetNamespace; }
    public void setTargetNamespace(String targetNamespace) { this.targetNamespace = targetNamespace; }
    
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    
    public void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }
}

