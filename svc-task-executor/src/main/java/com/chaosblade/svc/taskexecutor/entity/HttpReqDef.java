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

package com.chaosblade.svc.taskexecutor.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "http_req_def")
public class HttpReqDef {
    public enum HttpMethod { GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS }
    public enum BodyMode { NONE, JSON, FORM, RAW }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @Column(name = "api_id")
    private Long apiId;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public HttpMethod getMethod() { return method; }
    public String getUrlTemplate() { return urlTemplate; }
    public String getHeaders() { return headers; }
    public String getQueryParams() { return queryParams; }
    public BodyMode getBodyMode() { return bodyMode; }
    public String getContentType() { return contentType; }
    public String getBodyTemplate() { return bodyTemplate; }
    public String getRawBody() { return rawBody; }
    public Long getApiId() { return apiId; }
}

