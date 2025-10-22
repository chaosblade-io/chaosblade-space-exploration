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

package com.chaosblade.svc.taskresource.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** HTTP 请求定义实体 映射表：http_req_def */
@Entity
@Table(
    name = "http_req_def",
    indexes = {
      @Index(name = "idx_http_req_def_code", columnList = "code", unique = true),
      @Index(name = "idx_http_req_def_api_id", columnList = "api_id")
    })
public class HttpReqDef {

  public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS
  }

  public enum BodyMode {
    NONE,
    JSON,
    FORM,
    RAW
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank @Size(max = 64) @Column(name = "code", nullable = false, length = 64, unique = true)
  private String code;

  @NotBlank @Size(max = 128) @Column(name = "name", nullable = false, length = 128)
  private String name;

  @NotNull @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 10)
  private HttpMethod method;

  @NotBlank @Size(max = 1024) @Column(name = "url_template", nullable = false, length = 1024)
  private String urlTemplate;

  // JSON 字段以字符串存储，业务层做 JSON 合法性校验
  @Column(name = "headers", columnDefinition = "JSON")
  private String headers;

  @Column(name = "query_params", columnDefinition = "JSON")
  private String queryParams;

  @NotNull @Enumerated(EnumType.STRING)
  @Column(name = "body_mode", nullable = false, length = 10)
  private BodyMode bodyMode = BodyMode.NONE;

  @Size(max = 128) @Column(name = "content_type", length = 128)
  private String contentType;

  @Column(name = "body_template", columnDefinition = "JSON")
  private String bodyTemplate;

  @Column(name = "raw_body", columnDefinition = "MEDIUMTEXT")
  private String rawBody;

  @Column(name = "api_id")
  private Long apiId;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  // 复合校验：根据 bodyMode 要求 bodyTemplate/rawBody 的存在性
  @AssertTrue(
      message =
          "When bodyMode is JSON or FORM, bodyTemplate must be provided; when RAW, rawBody must be"
              + " provided; when NONE, both must be null")
  public boolean isBodyContentValid() {
    if (bodyMode == null) return true;
    switch (bodyMode) {
      case NONE:
        return (bodyTemplate == null || bodyTemplate.isBlank())
            && (rawBody == null || rawBody.isBlank());
      case JSON:
      case FORM:
        return bodyTemplate != null
            && !bodyTemplate.isBlank()
            && (rawBody == null || rawBody.isBlank());
      case RAW:
        return (bodyTemplate == null || bodyTemplate.isBlank())
            && rawBody != null
            && !rawBody.isBlank();
      default:
        return true;
    }
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(HttpMethod method) {
    this.method = method;
  }

  public String getUrlTemplate() {
    return urlTemplate;
  }

  public void setUrlTemplate(String urlTemplate) {
    this.urlTemplate = urlTemplate;
  }

  public String getHeaders() {
    return headers;
  }

  public void setHeaders(String headers) {
    this.headers = headers;
  }

  public String getQueryParams() {
    return queryParams;
  }

  public void setQueryParams(String queryParams) {
    this.queryParams = queryParams;
  }

  public BodyMode getBodyMode() {
    return bodyMode;
  }

  public void setBodyMode(BodyMode bodyMode) {
    this.bodyMode = bodyMode;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getBodyTemplate() {
    return bodyTemplate;
  }

  public void setBodyTemplate(String bodyTemplate) {
    this.bodyTemplate = bodyTemplate;
  }

  public String getRawBody() {
    return rawBody;
  }

  public void setRawBody(String rawBody) {
    this.rawBody = rawBody;
  }

  public Long getApiId() {
    return apiId;
  }

  public void setApiId(Long apiId) {
    this.apiId = apiId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
