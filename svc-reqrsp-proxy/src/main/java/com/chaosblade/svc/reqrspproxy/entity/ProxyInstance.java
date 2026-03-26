package com.chaosblade.svc.reqrspproxy.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * proxy-agent 部署实例。
 * 每次录制/拦截操作会创建一个 ProxyInstance，记录 proxy Pod 的部署信息
 * 和被劫持服务的原始 selector（用于恢复）。
 */
@Entity
@Table(name = "proxy_instance")
public class ProxyInstance {

    public enum Status {
        PENDING, DEPLOYING, RUNNING, RESTORING, DESTROYED, ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false, length = 64)
    private String recordingId;

    @Column(name = "namespace", nullable = false, length = 128)
    private String namespace;

    @Column(name = "target_service", nullable = false, length = 128)
    private String targetService;

    @Column(name = "proxy_pod_ip", length = 45)
    private String proxyPodIp;

    @Column(name = "proxy_port", nullable = false)
    private int proxyPort = 8080;

    @Column(name = "control_port", nullable = false)
    private int controlPort = 9090;

    @Column(name = "original_selector", columnDefinition = "JSON")
    private String originalSelector;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "deployment_name", length = 128)
    private String deploymentName;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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

    // ─── Getters/Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecordingId() { return recordingId; }
    public void setRecordingId(String recordingId) { this.recordingId = recordingId; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }

    public String getProxyPodIp() { return proxyPodIp; }
    public void setProxyPodIp(String proxyPodIp) { this.proxyPodIp = proxyPodIp; }

    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }

    public int getControlPort() { return controlPort; }
    public void setControlPort(int controlPort) { this.controlPort = controlPort; }

    public String getOriginalSelector() { return originalSelector; }
    public void setOriginalSelector(String originalSelector) { this.originalSelector = originalSelector; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getDeploymentName() { return deploymentName; }
    public void setDeploymentName(String deploymentName) { this.deploymentName = deploymentName; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
