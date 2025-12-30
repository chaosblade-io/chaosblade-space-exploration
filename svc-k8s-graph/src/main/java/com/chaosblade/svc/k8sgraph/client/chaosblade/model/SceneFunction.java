package com.chaosblade.svc.k8sgraph.client.chaosblade.model;

/**
 * 故障场景功能
 */
public class SceneFunction {
    private String functionId;
    private String code;
    private String name;
    private String description;
    private String sceneId;
    private String type;
    
    public String getFunctionId() { return functionId; }
    public void setFunctionId(String functionId) { this.functionId = functionId; }
    
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getSceneId() { return sceneId; }
    public void setSceneId(String sceneId) { this.sceneId = sceneId; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

