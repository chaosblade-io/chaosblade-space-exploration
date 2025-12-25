package com.chaosblade.svc.k8sgraph.domain.trace;

import java.util.Map;

/**
 * Span 事件模型
 */
public class SpanEvent {
    
    /** 事件名称 */
    private String name;
    
    /** 事件时间戳 */
    private Long timestamp;
    
    /** 事件属性 */
    private Map<String, Object> attributes;
    
    public SpanEvent() {}
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}

