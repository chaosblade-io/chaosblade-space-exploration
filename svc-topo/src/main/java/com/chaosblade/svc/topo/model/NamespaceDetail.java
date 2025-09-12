package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 命名空间详情对象
 * 用于封装单个命名空间的详细信息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamespaceDetail {

    /**
     * 命名空间ID
     */
    @JsonProperty("id")
    private Long id;

    /**
     * 系统键名
     */
    @JsonProperty("systemKey")
    private String systemKey;

    /**
     * Kubernetes命名空间
     */
    @JsonProperty("k8sNamespace")
    private String k8sNamespace;

    /**
     * 命名空间显示名称
     */
    @JsonProperty("name")
    private String name;

    /**
     * 命名空间描述
     */
    @JsonProperty("description")
    private String description;

    /**
     * 所有者
     */
    @JsonProperty("owner")
    private String owner;

    /**
     * 默认环境
     */
    @JsonProperty("defaultEnvironment")
    private String defaultEnvironment;

    // 构造函数
    public NamespaceDetail() {
    }

    public NamespaceDetail(Long id, String systemKey, String k8sNamespace, String name, 
                          String description, String owner, String defaultEnvironment) {
        this.id = id;
        this.systemKey = systemKey;
        this.k8sNamespace = k8sNamespace;
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.defaultEnvironment = defaultEnvironment;
    }

    // Getter and Setter methods
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystemKey() {
        return systemKey;
    }

    public void setSystemKey(String systemKey) {
        this.systemKey = systemKey;
    }

    public String getK8sNamespace() {
        return k8sNamespace;
    }

    public void setK8sNamespace(String k8sNamespace) {
        this.k8sNamespace = k8sNamespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getDefaultEnvironment() {
        return defaultEnvironment;
    }

    public void setDefaultEnvironment(String defaultEnvironment) {
        this.defaultEnvironment = defaultEnvironment;
    }

    @Override
    public String toString() {
        return "NamespaceDetail{" +
                "id=" + id +
                ", systemKey='" + systemKey + '\'' +
                ", k8sNamespace='" + k8sNamespace + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", owner='" + owner + '\'' +
                ", defaultEnvironment='" + defaultEnvironment + '\'' +
                '}';
    }
}