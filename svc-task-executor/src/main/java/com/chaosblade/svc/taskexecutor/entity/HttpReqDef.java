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

