package com.chaosblade.svc.k8sgraph.client.chaosblade.model;

import java.util.List;

/**
 * 故障场景分类
 */
public class SceneCategory {
    private String categoryId;
    private String name;
    private int level;
    private String parentId;
    private List<SceneCategory> children;
    
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    
    public List<SceneCategory> getChildren() { return children; }
    public void setChildren(List<SceneCategory> children) { this.children = children; }
}

