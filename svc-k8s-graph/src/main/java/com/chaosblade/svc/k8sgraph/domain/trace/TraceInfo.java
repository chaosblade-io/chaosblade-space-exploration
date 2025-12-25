package com.chaosblade.svc.k8sgraph.domain.trace;

import java.util.List;
import java.util.Map;

/**
 * Trace 信息模型
 */
public class TraceInfo {
    
    /** Trace ID */
    private String traceId;
    
    /** 持续时间（纳秒） */
    private Long duration;
    
    /** 开始时间（纳秒时间戳） */
    private Long startTime;
    
    /** 结束时间（纳秒时间戳） */
    private Long endTime;
    
    /** 状态码 */
    private Integer statusCode;
    
    /** 服务名 */
    private String serviceName;
    
    /** 操作名 */
    private String operationName;
    
    /** Span 数量 */
    private Integer spanCount;
    
    /** 是否有错误 */
    private Boolean hasError;
    
    /** HTTP 方法 */
    private String httpMethod;
    
    /** HTTP URL */
    private String httpUrl;
    
    /** Span 列表（详情查询时填充） */
    private List<SpanInfo> spans;
    
    /** 额外属性 */
    private Map<String, Object> attributes;
    
    public TraceInfo() {}
    
    // Getters and Setters
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }
    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public Integer getSpanCount() { return spanCount; }
    public void setSpanCount(Integer spanCount) { this.spanCount = spanCount; }
    public Boolean getHasError() { return hasError; }
    public void setHasError(Boolean hasError) { this.hasError = hasError; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getHttpUrl() { return httpUrl; }
    public void setHttpUrl(String httpUrl) { this.httpUrl = httpUrl; }
    public List<SpanInfo> getSpans() { return spans; }
    public void setSpans(List<SpanInfo> spans) { this.spans = spans; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}

