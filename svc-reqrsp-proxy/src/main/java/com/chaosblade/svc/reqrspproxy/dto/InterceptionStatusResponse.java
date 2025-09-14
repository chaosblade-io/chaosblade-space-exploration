package com.chaosblade.svc.reqrspproxy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拦截状态响应
 */
public class InterceptionStatusResponse {
    
    private String sessionId;
    private String namespace;
    private String serviceName;
    private String status;
    private String mode; // "RECORDING_WITH_INTERCEPTION" 或 "INTERCEPTION_ONLY"
    private List<RecordingRule> recordingRules;
    private List<InterceptionRule> interceptionRules;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime startedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime stoppedAt;
    
    public InterceptionStatusResponse() {}
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMode() {
        return mode;
    }
    
    public void setMode(String mode) {
        this.mode = mode;
    }
    
    public List<RecordingRule> getRecordingRules() {
        return recordingRules;
    }
    
    public void setRecordingRules(List<RecordingRule> recordingRules) {
        this.recordingRules = recordingRules;
    }
    
    public List<InterceptionRule> getInterceptionRules() {
        return interceptionRules;
    }
    
    public void setInterceptionRules(List<InterceptionRule> interceptionRules) {
        this.interceptionRules = interceptionRules;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getStoppedAt() {
        return stoppedAt;
    }
    
    public void setStoppedAt(LocalDateTime stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
    
    @Override
    public String toString() {
        return "InterceptionStatusResponse{" +
                "sessionId='" + sessionId + '\'' +
                ", namespace='" + namespace + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", status='" + status + '\'' +
                ", mode='" + mode + '\'' +
                ", recordingRules=" + recordingRules +
                ", interceptionRules=" + interceptionRules +
                ", startedAt=" + startedAt +
                ", stoppedAt=" + stoppedAt +
                '}';
    }
}
