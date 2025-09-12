package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 系统API列表响应对象
 * 用于封装 /v1/topology/{systemId}/apis 接口的响应数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemApiListResponse {

    /**
     * 请求是否成功
     */
    @JsonProperty("success")
    private Boolean success;

    /**
     * 响应数据
     */
    @JsonProperty("data")
    private SystemApiListData data;

    // 构造函数
    public SystemApiListResponse() {
    }

    public SystemApiListResponse(Boolean success, SystemApiListData data) {
        this.success = success;
        this.data = data;
    }

    // Getter and Setter methods
    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public SystemApiListData getData() {
        return data;
    }

    public void setData(SystemApiListData data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "SystemApiListResponse{" +
                "success=" + success +
                ", data=" + data +
                '}';
    }

    /**
     * 系统API列表数据对象
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SystemApiListData {

        /**
         * API详情列表
         */
        @JsonProperty("items")
        private List<SystemApiDetail> items;

        /**
         * 总数
         */
        @JsonProperty("total")
        private Integer total;

        // 构造函数
        public SystemApiListData() {
        }

        public SystemApiListData(List<SystemApiDetail> items, Integer total) {
            this.items = items;
            this.total = total;
        }

        // Getter and Setter methods
        public List<SystemApiDetail> getItems() {
            return items;
        }

        public void setItems(List<SystemApiDetail> items) {
            this.items = items;
        }

        public Integer getTotal() {
            return total;
        }

        public void setTotal(Integer total) {
            this.total = total;
        }

        @Override
        public String toString() {
            return "SystemApiListData{" +
                    "items=" + items +
                    ", total=" + total +
                    '}';
        }
    }

    /**
     * 系统API详情对象
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SystemApiDetail {

        /**
         * API标识号
         */
        @JsonProperty("id")
        private Long id;

        /**
         * 系统ID
         */
        @JsonProperty("systemId")
        private Long systemId;

        /**
         * Kubernetes命名空间
         */
        @JsonProperty("k8sNamespace")
        private String k8sNamespace;

        /**
         * 操作ID（API名称）
         */
        @JsonProperty("operationId")
        private String operationId;

        /**
         * HTTP方法
         */
        @JsonProperty("method")
        private String method;

        /**
         * API路径
         */
        @JsonProperty("path")
        private String path;

        /**
         * API摘要描述
         */
        @JsonProperty("summary")
        private String summary;

        /**
         * 标签
         */
        @JsonProperty("tags")
        private String tags;

        /**
         * 基础URL
         */
        @JsonProperty("baseUrl")
        private String baseUrl;

        /**
         * 创建时间
         */
        @JsonProperty("createdAt")
        private String createdAt;

        /**
         * 更新时间
         */
        @JsonProperty("updatedAt")
        private String updatedAt;

        // 构造函数
        public SystemApiDetail() {
        }

        public SystemApiDetail(Long id, Long systemId, String k8sNamespace, String operationId,
                              String method, String path, String summary, String tags, String baseUrl,
                              String createdAt, String updatedAt) {
            this.id = id;
            this.systemId = systemId;
            this.k8sNamespace = k8sNamespace;
            this.operationId = operationId;
            this.method = method;
            this.path = path;
            this.summary = summary;
            this.tags = tags;
            this.baseUrl = baseUrl;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        // Getter and Setter methods
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getSystemId() {
            return systemId;
        }

        public void setSystemId(Long systemId) {
            this.systemId = systemId;
        }

        public String getK8sNamespace() {
            return k8sNamespace;
        }

        public void setK8sNamespace(String k8sNamespace) {
            this.k8sNamespace = k8sNamespace;
        }

        public String getOperationId() {
            return operationId;
        }

        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getTags() {
            return tags;
        }

        public void setTags(String tags) {
            this.tags = tags;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }

        @Override
        public String toString() {
            return "SystemApiDetail{" +
                    "id=" + id +
                    ", systemId=" + systemId +
                    ", k8sNamespace='" + k8sNamespace + '\'' +
                    ", operationId='" + operationId + '\'' +
                    ", method='" + method + '\'' +
                    ", path='" + path + '\'' +
                    ", summary='" + summary + '\'' +
                    ", tags='" + tags + '\'' +
                    ", baseUrl='" + baseUrl + '\'' +
                    ", createdAt='" + createdAt + '\'' +
                    ", updatedAt='" + updatedAt + '\'' +
                    '}';
        }
    }
}