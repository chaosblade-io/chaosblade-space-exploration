package com.chaosblade.svc.topo.model.k8s;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes 元数据
 * 存储从 K8s API 获取的资源元数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KubernetesMetadata {

    /**
     * 资源类型（Pod、Node、Service 等）
     */
    @JsonProperty("kind")
    private String kind;

    /**
     * API 版本
     */
    @JsonProperty("apiVersion")
    private String apiVersion;

    /**
     * 命名空间
     */
    @JsonProperty("namespace")
    private String namespace;

    /**
     * 资源名称
     */
    @JsonProperty("name")
    private String name;

    /**
     * UID
     */
    @JsonProperty("uid")
    private String uid;

    /**
     * 标签
     */
    @JsonProperty("labels")
    private Map<String, String> labels;

    /**
     * 注解
     */
    @JsonProperty("annotations")
    private Map<String, String> annotations;

    /**
     * 所有者引用
     */
    @JsonProperty("ownerReferences")
    private List<OwnerReference> ownerReferences;

    /**
     * 创建时间
     */
    @JsonProperty("creationTimestamp")
    private String creationTimestamp;

    /**
     * 资源版本
     */
    @JsonProperty("resourceVersion")
    private String resourceVersion;

    public KubernetesMetadata() {
        this.labels = new HashMap<>();
        this.annotations = new HashMap<>();
        this.ownerReferences = new ArrayList<>();
    }

    // Getters and Setters
    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public List<OwnerReference> getOwnerReferences() {
        return ownerReferences;
    }

    public void setOwnerReferences(List<OwnerReference> ownerReferences) {
        this.ownerReferences = ownerReferences;
    }

    public String getCreationTimestamp() {
        return creationTimestamp;
    }

    public void setCreationTimestamp(String creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    public String getResourceVersion() {
        return resourceVersion;
    }

    public void setResourceVersion(String resourceVersion) {
        this.resourceVersion = resourceVersion;
    }

    /**
     * 所有者引用
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OwnerReference {
        @JsonProperty("kind")
        private String kind;

        @JsonProperty("name")
        private String name;

        @JsonProperty("uid")
        private String uid;

        @JsonProperty("controller")
        private Boolean controller;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public Boolean getController() {
            return controller;
        }

        public void setController(Boolean controller) {
            this.controller = controller;
        }
    }
}

