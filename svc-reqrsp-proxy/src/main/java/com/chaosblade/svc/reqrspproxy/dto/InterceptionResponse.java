package com.chaosblade.svc.reqrspproxy.dto;

/**
 * 拦截响应
 */
public class InterceptionResponse {
    
    private String sessionId;
    private String status;
    private String message;
    private Integer recordingRulesCount;
    private Integer interceptionRulesCount;
    private String mode; // "RECORDING_WITH_INTERCEPTION" 或 "INTERCEPTION_ONLY"
    
    public InterceptionResponse() {}
    
    public InterceptionResponse(String sessionId, String status, String message,
                               Integer recordingRulesCount, Integer interceptionRulesCount) {
        this.sessionId = sessionId;
        this.status = status;
        this.message = message;
        this.recordingRulesCount = recordingRulesCount;
        this.interceptionRulesCount = interceptionRulesCount;

        // 自动判断模式
        if (recordingRulesCount > interceptionRulesCount) {
            this.mode = "RECORDING_WITH_INTERCEPTION";
        } else {
            this.mode = "INTERCEPTION_ONLY";
        }
    }

    public InterceptionResponse(String sessionId, String status, String message,
                               Integer recordingRulesCount, Integer interceptionRulesCount, String mode) {
        this.sessionId = sessionId;
        this.status = status;
        this.message = message;
        this.recordingRulesCount = recordingRulesCount;
        this.interceptionRulesCount = interceptionRulesCount;
        this.mode = mode;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Integer getRecordingRulesCount() {
        return recordingRulesCount;
    }
    
    public void setRecordingRulesCount(Integer recordingRulesCount) {
        this.recordingRulesCount = recordingRulesCount;
    }
    
    public Integer getInterceptionRulesCount() {
        return interceptionRulesCount;
    }
    
    public void setInterceptionRulesCount(Integer interceptionRulesCount) {
        this.interceptionRulesCount = interceptionRulesCount;
    }
    
    public String getMode() {
        return mode;
    }
    
    public void setMode(String mode) {
        this.mode = mode;
    }
    
    @Override
    public String toString() {
        return "InterceptionResponse{" +
                "sessionId='" + sessionId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", recordingRulesCount=" + recordingRulesCount +
                ", interceptionRulesCount=" + interceptionRulesCount +
                ", mode='" + mode + '\'' +
                '}';
    }
}
