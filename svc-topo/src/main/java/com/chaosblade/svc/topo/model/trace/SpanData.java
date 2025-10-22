/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.topo.model.trace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenTelemetry Span数据模型
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpanData {

    /**
     * Trace ID
     */
    @JsonProperty("traceID")
    private String traceId;

    /**
     * Span ID
     */
    @JsonProperty("spanID")
    private String spanId;

    /**
     * 操作名称
     */
    @JsonProperty("operationName")
    private String operationName;

    /**
     * 引用关系
     */
    @JsonProperty("references")
    private List<SpanReference> references;

    /**
     * 开始时间（微秒时间戳）
     */
    @JsonProperty("startTime")
    private Long startTime;

    /**
     * 持续时间（微秒）
     */
    @JsonProperty("duration")
    private Long duration;

    /**
     * 标签
     */
    @JsonProperty("tags")
    private List<Tag> tags;

    /**
     * 日志
     */
    @JsonProperty("logs")
    private List<LogEntry> logs;

    /**
     * Process ID
     */
    @JsonProperty("processID")
    private String processId;

    /**
     * 警告信息
     */
    @JsonProperty("warnings")
    private List<String> warnings;

    public SpanData() {}

    // Getter and Setter methods
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public List<SpanReference> getReferences() { return references; }
    public void setReferences(List<SpanReference> references) { this.references = references; }

    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }

    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }

    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }

    public List<LogEntry> getLogs() { return logs; }
    public void setLogs(List<LogEntry> logs) { this.logs = logs; }

    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    /**
     * 获取标签值
     */
    public String getTagValue(String key) {
        if (tags == null) return null;
        return tags.stream()
                .filter(tag -> key.equals(tag.getKey()))
                .findFirst()
                .map(Tag::getValue)
                .map(Object::toString)
                .orElse(null);
    }

    /**
     * 获取所有标签的Map
     */
    public Map<String, Object> getTagsAsMap() {
        Map<String, Object> tagMap = new HashMap<>();
        if (tags != null) {
            for (Tag tag : tags) {
                tagMap.put(tag.getKey(), tag.getValue());
            }
        }
        return tagMap;
    }

    /**
     * 获取父Span ID
     */
    public String getParentSpanId() {
        if (references == null) return null;
        return references.stream()
                .filter(ref -> "CHILD_OF".equals(ref.getRefType()))
                .findFirst()
                .map(SpanReference::getSpanId)
                .orElse(null);
    }

    /**
     * 获取服务名称
     */
    public String getServiceName() {
        return getTagValue("service.name");
    }

    /**
     * 获取Span类型
     */
    public String getSpanKind() {
        return getTagValue("span.kind");
    }

    /**
     * 获取RPC服务
     */
    public String getRpcService() {
        return getTagValue("rpc.service");
    }

    /**
     * 获取RPC方法
     */
    public String getRpcMethod() {
        return getTagValue("rpc.method");
    }

    /**
     * 获取HTTP方法
     */
    public String getHttpMethod() {
        return getTagValue("http.method");
    }

    /**
     * 获取HTTP URL
     */
    public String getHttpUrl() {
        return getTagValue("http.url");
    }

    /**
     * 判断是否为根Span
     */
    public boolean isRootSpan() {
        return getParentSpanId() == null;
    }

    /**
     * 判断是否为错误Span
     */
    public boolean isError() {
        String statusCode = getTagValue("otel.status_code");
        return "ERROR".equals(statusCode) ||
               getTagValue("error") != null ||
               (warnings != null && !warnings.isEmpty());
    }

    @Override
    public String toString() {
        return "SpanData{" +
                "spanId='" + spanId + '\'' +
                ", operationName='" + operationName + '\'' +
                ", duration=" + duration +
                ", processId='" + processId + '\'' +
                '}';
    }

    /**
     * Span引用关系
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpanReference {
        @JsonProperty("refType")
        private String refType;

        @JsonProperty("traceID")
        private String traceId;

        @JsonProperty("spanID")
        private String spanId;

        public SpanReference() {}

        public String getRefType() { return refType; }
        public void setRefType(String refType) { this.refType = refType; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getSpanId() { return spanId; }
        public void setSpanId(String spanId) { this.spanId = spanId; }
    }

    /**
     * 标签
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tag {
        @JsonProperty("key")
        private String key;

        @JsonProperty("type")
        private String type;

        @JsonProperty("value")
        private Object value;

        public Tag() {}

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    /**
     * 日志条目
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LogEntry {
        @JsonProperty("timestamp")
        private Long timestamp;

        @JsonProperty("fields")
        private List<Tag> fields;

        public LogEntry() {}

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public List<Tag> getFields() { return fields; }
        public void setFields(List<Tag> fields) { this.fields = fields; }
    }
}
