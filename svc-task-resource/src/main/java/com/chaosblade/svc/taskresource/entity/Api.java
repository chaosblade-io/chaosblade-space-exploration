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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * API实体类
 */
@Entity
@Table(name = "apis")
public class Api {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "operation_id", nullable = false, length = 128)
    private String operationId;

    @Column(name = "method", nullable = false)
    private String method;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(name = "summary", length = 512)
    private String summary;

    @Column(name = "tags", columnDefinition = "JSON")
    private String tags;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType = "application/json";

    @Column(name = "headers_template", columnDefinition = "JSON")
    private String headersTemplate;

    @Column(name = "auth_type", nullable = false)
    private String authType = "NONE";

    @Column(name = "auth_template", columnDefinition = "JSON")
    private String authTemplate;

    @Column(name = "path_params", columnDefinition = "JSON")
    private String pathParams;

    @Column(name = "query_params", columnDefinition = "JSON")
    private String queryParams;

    @Column(name = "body_template", columnDefinition = "JSON")
    private String bodyTemplate;

    @Column(name = "variables", columnDefinition = "JSON")
    private String variables;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 15000;

    @Column(name = "retry_config", columnDefinition = "JSON")
    private String retryConfig;
    
    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    public Api() {}
    
    public Api(Long systemId, String operationId, String path, String method, String summary) {
        this.systemId = systemId;
        this.operationId = operationId;
        this.path = path;
        this.method = method;
        this.summary = summary;
    }
    
    // Getters and Setters
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
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
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
