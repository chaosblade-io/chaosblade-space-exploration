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

package com.chaosblade.svc.reqrspproxy.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * HTTP请求定义实体类
 * 对应数据库表 http_req_def
 */
@Entity
@Table(name = "http_req_def")
public class HttpReqDef {
    
    /**
     * HTTP方法枚举
     */
    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
    }
    
    /**
     * 请求体模式枚举
     */
    public enum BodyMode {
        NONE, JSON, FORM, RAW
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", nullable = false, length = 64)
    private String code;
    
    @Column(name = "name", nullable = false, length = 128)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private HttpMethod method;
    
    @Column(name = "url_template", nullable = false, length = 1024)
    private String urlTemplate;
    
    @Column(name = "headers", columnDefinition = "JSON")
    private String headers;
    
    @Column(name = "query_params", columnDefinition = "JSON")
    private String queryParams;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "body_mode", nullable = false)
    private BodyMode bodyMode = BodyMode.NONE;
    
    @Column(name = "content_type", length = 128)
    private String contentType;
    
    @Column(name = "body_template", columnDefinition = "JSON")
    private String bodyTemplate;
    
    @Column(name = "raw_body", columnDefinition = "MEDIUMTEXT")
    private String rawBody;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @Column(name = "api_id")
    private Long apiId;
    
    // 默认构造函数
    public HttpReqDef() {}
    
    // 构造函数
    public HttpReqDef(String code, String name, HttpMethod method, String urlTemplate) {
        this.code = code;
        this.name = name;
        this.method = method;
        this.urlTemplate = urlTemplate;
    }
    
    // Getter 和 Setter 方法
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
    
    public Long getApiId() {
        return apiId;
    }
    
    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }
    
    @Override
    public String toString() {
        return "HttpReqDef{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", method=" + method +
                ", urlTemplate='" + urlTemplate + '\'' +
                ", bodyMode=" + bodyMode +
                ", contentType='" + contentType + '\'' +
                ", apiId=" + apiId +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
