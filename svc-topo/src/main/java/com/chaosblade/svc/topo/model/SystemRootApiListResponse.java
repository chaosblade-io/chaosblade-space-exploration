/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 系统根API列表响应对象 用于封装 /v1/topology/{systemId}/apis/root 接口的响应数据 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemRootApiListResponse {

  /** 请求是否成功 */
  @JsonProperty("success")
  private Boolean success;

  /** 响应数据 */
  @JsonProperty("data")
  private SystemRootApiListData data;

  // 构造函数
  public SystemRootApiListResponse() {}

  public SystemRootApiListResponse(Boolean success, SystemRootApiListData data) {
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

  public SystemRootApiListData getData() {
    return data;
  }

  public void setData(SystemRootApiListData data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "SystemRootApiListResponse{" + "success=" + success + ", data=" + data + '}';
  }

  /** 系统根API列表数据对象 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class SystemRootApiListData {

    /** 根API详情列表 */
    @JsonProperty("items")
    private List<SystemRootApiDetail> items;

    /** 总数 */
    @JsonProperty("total")
    private Integer total;

    // 构造函数
    public SystemRootApiListData() {}

    public SystemRootApiListData(List<SystemRootApiDetail> items, Integer total) {
      this.items = items;
      this.total = total;
    }

    // Getter and Setter methods
    public List<SystemRootApiDetail> getItems() {
      return items;
    }

    public void setItems(List<SystemRootApiDetail> items) {
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
      return "SystemRootApiListData{" + "items=" + items + ", total=" + total + '}';
    }
  }

  /** 系统根API详情对象 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class SystemRootApiDetail {

    /** API标识号 */
    @JsonProperty("id")
    private Long id;

    /** 系统ID */
    @JsonProperty("systemId")
    private Long systemId;

    /** 操作ID（API名称） */
    @JsonProperty("operationId")
    private String operationId;

    /** HTTP方法 */
    @JsonProperty("method")
    private String method;

    /** API路径 */
    @JsonProperty("path")
    private String path;

    /** API摘要描述 */
    @JsonProperty("summary")
    private String summary;

    /** 标签 */
    @JsonProperty("tags")
    private String tags;

    /** API版本 */
    @JsonProperty("version")
    private String version;

    /** 基础URL */
    @JsonProperty("baseUrl")
    private String baseUrl;

    /** 创建时间 */
    @JsonProperty("createdAt")
    private String createdAt;

    /** 更新时间 */
    @JsonProperty("updatedAt")
    private String updatedAt;

    /** 内容类型 */
    @JsonProperty("contentType")
    private String contentType;

    /** 请求头模板 */
    @JsonProperty("headersTemplate")
    private String headersTemplate;

    /** 认证类型 */
    @JsonProperty("authType")
    private String authType;

    /** 认证模板 */
    @JsonProperty("authTemplate")
    private String authTemplate;

    /** 路径参数 */
    @JsonProperty("pathParams")
    private String pathParams;

    /** 查询参数 */
    @JsonProperty("queryParams")
    private String queryParams;

    /** 请求体模板 */
    @JsonProperty("bodyTemplate")
    private String bodyTemplate;

    /** 变量定义 */
    @JsonProperty("variables")
    private String variables;

    /** 超时时间（毫秒） */
    @JsonProperty("timeoutMs")
    private Integer timeoutMs;

    /** 重试配置 */
    @JsonProperty("retryConfig")
    private String retryConfig;

    /** 根服务名称 */
    @JsonIgnore private String rootService;

    /** 根操作名称 */
    @JsonIgnore private String rootOperation;

    // 构造函数
    public SystemRootApiDetail() {}

    public SystemRootApiDetail(
        Long id,
        Long systemId,
        String operationId,
        String method,
        String path,
        String summary,
        String tags,
        String version,
        String baseUrl,
        String createdAt,
        String updatedAt,
        String contentType,
        String headersTemplate,
        String authType,
        String authTemplate,
        String pathParams,
        String queryParams,
        String bodyTemplate,
        String variables,
        Integer timeoutMs,
        String retryConfig) {
      this.id = id;
      this.systemId = systemId;
      this.operationId = operationId;
      this.method = method;
      this.path = path;
      this.summary = summary;
      this.tags = tags;
      this.version = version;
      this.baseUrl = baseUrl;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
      this.contentType = contentType;
      this.headersTemplate = headersTemplate;
      this.authType = authType;
      this.authTemplate = authTemplate;
      this.pathParams = pathParams;
      this.queryParams = queryParams;
      this.bodyTemplate = bodyTemplate;
      this.variables = variables;
      this.timeoutMs = timeoutMs;
      this.retryConfig = retryConfig;
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

    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
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

    public String getContentType() {
      return contentType;
    }

    public void setContentType(String contentType) {
      this.contentType = contentType;
    }

    public String getHeadersTemplate() {
      return headersTemplate;
    }

    public void setHeadersTemplate(String headersTemplate) {
      this.headersTemplate = headersTemplate;
    }

    public String getAuthType() {
      return authType;
    }

    public void setAuthType(String authType) {
      this.authType = authType;
    }

    public String getAuthTemplate() {
      return authTemplate;
    }

    public void setAuthTemplate(String authTemplate) {
      this.authTemplate = authTemplate;
    }

    public String getPathParams() {
      return pathParams;
    }

    public void setPathParams(String pathParams) {
      this.pathParams = pathParams;
    }

    public String getQueryParams() {
      return queryParams;
    }

    public void setQueryParams(String queryParams) {
      this.queryParams = queryParams;
    }

    public String getBodyTemplate() {
      return bodyTemplate;
    }

    public void setBodyTemplate(String bodyTemplate) {
      this.bodyTemplate = bodyTemplate;
    }

    public String getVariables() {
      return variables;
    }

    public void setVariables(String variables) {
      this.variables = variables;
    }

    public String getRootService() {
      return rootService;
    }

    public void setRootService(String rootService) {
      this.rootService = rootService;
    }

    public String getRootOperation() {
      return rootOperation;
    }

    public void setRootOperation(String rootOperation) {
      this.rootOperation = rootOperation;
    }

    public Integer getTimeoutMs() {
      return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
      this.timeoutMs = timeoutMs;
    }

    public String getRetryConfig() {
      return retryConfig;
    }

    public void setRetryConfig(String retryConfig) {
      this.retryConfig = retryConfig;
    }

    @Override
    public String toString() {
      return "SystemRootApiDetail{"
          + "id="
          + id
          + ", systemId="
          + systemId
          + ", operationId='"
          + operationId
          + '\''
          + ", method='"
          + method
          + '\''
          + ", path='"
          + path
          + '\''
          + ", summary='"
          + summary
          + '\''
          + ", tags='"
          + tags
          + '\''
          + ", version='"
          + version
          + '\''
          + ", baseUrl='"
          + baseUrl
          + '\''
          + ", createdAt='"
          + createdAt
          + '\''
          + ", updatedAt='"
          + updatedAt
          + '\''
          + ", contentType='"
          + contentType
          + '\''
          + ", headersTemplate='"
          + headersTemplate
          + '\''
          + ", authType='"
          + authType
          + '\''
          + ", authTemplate='"
          + authTemplate
          + '\''
          + ", pathParams='"
          + pathParams
          + '\''
          + ", queryParams='"
          + queryParams
          + '\''
          + ", bodyTemplate='"
          + bodyTemplate
          + '\''
          + ", variables='"
          + variables
          + '\''
          + ", timeoutMs="
          + timeoutMs
          + ", retryConfig='"
          + retryConfig
          + '\''
          + ", rootService='"
          + rootService
          + '\''
          + ", rootOperation='"
          + rootOperation
          + '\''
          + '}';
    }
  }
}
