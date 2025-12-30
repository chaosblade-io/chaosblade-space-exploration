package com.chaosblade.svc.k8sgraph.domain.experiment;

import java.util.Map;

/**
 * 故障参数
 */
public class FaultArgument {
    
    /** 参数ID */
    private String parameterId;
    
    /** 参数名称（如：cpu-count） */
    private String name;
    
    /** 参数别名 */
    private String alias;
    
    /** 参数值 */
    private String value;
    
    /** 状态 */
    private boolean state;
    
    /** 参数类型（通常为：user_args） */
    private String type = "user_args";
    
    /** 是否启用 */
    private boolean enabled = true;
    
    /** 参数等级：1=故障配置，2=影响范围，3=通用配置 */
    private int grade;
    
    /** 参数描述 */
    private String description;
    
    /** 功能ID */
    private String functionId;
    
    /** 错误信息 */
    private String errorMessage = "";
    
    /** 组件信息（包含defaultValue, required等） */
    private Map<String, Object> component;
    
    // Getters and Setters
    public String getParameterId() { return parameterId; }
    public void setParameterId(String parameterId) { this.parameterId = parameterId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    
    public boolean isState() { return state; }
    public void setState(boolean state) { this.state = state; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public int getGrade() { return grade; }
    public void setGrade(int grade) { this.grade = grade; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getFunctionId() { return functionId; }
    public void setFunctionId(String functionId) { this.functionId = functionId; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Map<String, Object> getComponent() { return component; }
    public void setComponent(Map<String, Object> component) { this.component = component; }
}

