package com.chaosblade.svc.k8sgraph.domain.risk;

/**
 * 链路风险分析请求
 */
public class TraceRiskRequest {
    
    /** 链路追踪ID */
    private String traceId;
    
    public TraceRiskRequest() {}
    
    public TraceRiskRequest(String traceId) {
        this.traceId = traceId;
    }
    
    // Getters and Setters
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}

