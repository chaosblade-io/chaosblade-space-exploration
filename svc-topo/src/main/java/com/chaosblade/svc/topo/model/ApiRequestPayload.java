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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** API请求负载数据对象 用于封装API请求的相关信息 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiRequestPayload {

  /** 操作ID */
  @JsonProperty("operationId")
  private String operationId;

  /** 根服务名称 */
  @JsonProperty("rootService")
  private String rootService;

  /** 根操作名称 */
  @JsonProperty("rootOperation")
  private String rootOperation;

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

  // 构造函数
  public ApiRequestPayload() {}

  public ApiRequestPayload(
      String operationId,
      String rootService,
      String rootOperation,
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
    this.operationId = operationId;
    this.rootService = rootService;
    this.rootOperation = rootOperation;
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
  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
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
    return "ApiRequestPayload{"
        + "operationId='"
        + operationId
        + '\''
        + ", rootService='"
        + rootService
        + '\''
        + ", rootOperation='"
        + rootOperation
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
        + '}';
  }
}
