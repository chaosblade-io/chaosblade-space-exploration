package com.chaosblade.svc.k8sgraph.domain.trace;

import java.util.List;
import java.util.Map;

/**
 * Span 信息模型
 */
public class SpanInfo {
    
    /** Span ID */
    private String spanId;
    
    /** 父 Span ID */
    private String parentSpanId;
    
    /** Trace ID */
    private String traceId;
    
    /** 操作名称 */
    private String operationName;
    
    /** 服务名 */
    private String serviceName;
    
    /** 开始时间（纳秒时间戳） */
    private Long startTime;
    
    /** 结束时间（纳秒时间戳） */
    private Long endTime;
    
    /** 持续时间（纳秒） */
    private Long duration;
    
    /** 状态码 */
    private Integer statusCode;
    
    /** 状态消息 */
    private String statusMessage;
    
    /** Span 类型 (SERVER, CLIENT, PRODUCER, CONSUMER, INTERNAL) */
    private String spanKind;
    
    /** 资源属性 */
    private Map<String, Object> resourceAttributes;
    
    /** Span 属性 */
    private Map<String, Object> spanAttributes;
    
    /** 事件列表 */
    private List<SpanEvent> events;
    
    /** 子 Span 列表（构建树形结构时使用） */
    private List<SpanInfo> children;
    
    public SpanInfo() {}
    
    // Getters and Setters
    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }
    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public String getSpanKind() { return spanKind; }
    public void setSpanKind(String spanKind) { this.spanKind = spanKind; }
    public Map<String, Object> getResourceAttributes() { return resourceAttributes; }
    public void setResourceAttributes(Map<String, Object> resourceAttributes) { this.resourceAttributes = resourceAttributes; }
    public Map<String, Object> getSpanAttributes() { return spanAttributes; }
    public void setSpanAttributes(Map<String, Object> spanAttributes) { this.spanAttributes = spanAttributes; }
    public List<SpanEvent> getEvents() { return events; }
    public void setEvents(List<SpanEvent> events) { this.events = events; }
    public List<SpanInfo> getChildren() { return children; }
    public void setChildren(List<SpanInfo> children) { this.children = children; }
}

