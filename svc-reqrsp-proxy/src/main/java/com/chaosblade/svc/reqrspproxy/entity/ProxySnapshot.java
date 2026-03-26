package com.chaosblade.svc.reqrspproxy.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * proxy-agent 录制的请求/响应快照。
 * 快照在录制停止时从 proxy-agent 拉取并持久化到 MySQL。
 */
@Entity
@Table(name = "proxy_snapshot")
public class ProxySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false, length = 64)
    private String recordingId;

    @Column(name = "signature_hash", nullable = false, length = 128)
    private String signatureHash;

    @Column(name = "protocol", nullable = false, length = 8)
    private String protocol = "http";

    @Column(name = "method", length = 16)
    private String method;

    @Column(name = "path", length = 512)
    private String path;

    @Column(name = "request_headers", columnDefinition = "JSON")
    private String requestHeaders;

    @Lob
    @Column(name = "request_body", columnDefinition = "MEDIUMBLOB")
    private byte[] requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_headers", columnDefinition = "JSON")
    private String responseHeaders;

    @Lob
    @Column(name = "response_body", columnDefinition = "MEDIUMBLOB")
    private byte[] responseBody;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs = 0;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }

    // ─── Getters/Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecordingId() { return recordingId; }
    public void setRecordingId(String recordingId) { this.recordingId = recordingId; }

    public String getSignatureHash() { return signatureHash; }
    public void setSignatureHash(String signatureHash) { this.signatureHash = signatureHash; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(String requestHeaders) { this.requestHeaders = requestHeaders; }

    public byte[] getRequestBody() { return requestBody; }
    public void setRequestBody(byte[] requestBody) { this.requestBody = requestBody; }

    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }

    public String getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(String responseHeaders) { this.responseHeaders = responseHeaders; }

    public byte[] getResponseBody() { return responseBody; }
    public void setResponseBody(byte[] responseBody) { this.responseBody = responseBody; }

    public int getLatencyMs() { return latencyMs; }
    public void setLatencyMs(int latencyMs) { this.latencyMs = latencyMs; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
