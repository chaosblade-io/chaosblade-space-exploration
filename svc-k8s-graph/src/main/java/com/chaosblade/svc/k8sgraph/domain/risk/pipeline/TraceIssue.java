package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

/**
 * Trace分析发现的问题
 */
public class TraceIssue {
    
    /** 问题类型: SLOW_RESPONSE, ERROR, EXCEPTION, HIGH_LATENCY_VARIANCE 等 */
    private String issueType;
    
    /** 问题严重程度: CRITICAL, HIGH, MEDIUM, LOW */
    private String severity;
    
    /** 相关服务 */
    private String serviceName;
    
    /** 相关API */
    private String spanName;
    
    /** 相关Trace ID */
    private String traceId;
    
    /** 问题描述 */
    private String description;
    
    /** 根因分析 */
    private String rootCause;
    
    /** 建议措施 */
    private String recommendation;
    
    // Getters and Setters
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public String getSpanName() { return spanName; }
    public void setSpanName(String spanName) { this.spanName = spanName; }
    
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}

