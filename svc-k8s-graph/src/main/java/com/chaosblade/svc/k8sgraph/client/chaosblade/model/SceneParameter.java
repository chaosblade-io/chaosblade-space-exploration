package com.chaosblade.svc.k8sgraph.client.chaosblade.model;

/**
 * 故障场景参数
 */
public class SceneParameter {
    private String parameterId;
    private String name;
    private String alias;
    private String description;
    private String defaultValue;
    private boolean required;
    private int grade;
    private String gradeName;
    
    public String getParameterId() { return parameterId; }
    public void setParameterId(String parameterId) { this.parameterId = parameterId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    
    public int getGrade() { return grade; }
    public void setGrade(int grade) { this.grade = grade; }
    
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
}

