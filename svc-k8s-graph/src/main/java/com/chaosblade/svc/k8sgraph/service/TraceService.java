package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.client.ObservabilityApiClient;
import com.chaosblade.svc.k8sgraph.domain.trace.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Trace 服务 - 处理 trace 相关的业务逻辑
 */
@Service
public class TraceService {
    
    private static final Logger logger = LoggerFactory.getLogger(TraceService.class);
    
    @Autowired
    private ObservabilityApiClient apiClient;
    
    /**
     * 获取服务的 Trace 列表
     */
    public TraceListResponse getTraceList(String serviceName, long fromMs, long toMs) {
        logger.info("Getting trace list for service: {}, from: {}, to: {}", serviceName, fromMs, toMs);
        
        JsonNode traceArray = apiClient.getTraceList(serviceName, fromMs, toMs);
        List<TraceInfo> traces = parseTraceList(traceArray);
        
        return new TraceListResponse(traces);
    }
    
    /**
     * 获取 Trace 详情（不需要时间范围，traceId 唯一标识 trace）
     */
    public TraceInfo getTraceDetail(String traceId) {
        logger.info("Getting trace detail for traceId: {}", traceId);

        JsonNode traceArray = apiClient.getTraceDetail(traceId);

        if (traceArray.isArray() && traceArray.size() > 0) {
            TraceInfo trace = parseTraceNode(traceArray.get(0));
            // 解析 spans 并构建树形结构
            trace.setSpans(parseSpans(traceArray.get(0)));
            return trace;
        }

        return null;
    }

    /**
     * 获取 Trace 详情（带时间范围，向后兼容）
     */
    public TraceInfo getTraceDetail(String traceId, long fromMs, long toMs) {
        return getTraceDetail(traceId);
    }
    
    /**
     * 解析 Trace 列表
     */
    private List<TraceInfo> parseTraceList(JsonNode traceArray) {
        List<TraceInfo> traces = new ArrayList<>();
        
        if (traceArray == null || !traceArray.isArray()) {
            return traces;
        }
        
        for (JsonNode node : traceArray) {
            traces.add(parseTraceNode(node));
        }
        
        return traces;
    }
    
    /**
     * 解析单个 Trace 节点
     */
    private TraceInfo parseTraceNode(JsonNode node) {
        TraceInfo trace = new TraceInfo();

        trace.setTraceId(getTextValue(node, "traceId", "trace_id", "TraceId", "TraceID"));
        trace.setDuration(getLongValue(node, "duration", "Duration", "DurationNs", "duration_ns"));
        trace.setStartTime(getLongValue(node, "startTime", "start_time", "StartTime", "Timestamp", "timestamp"));
        trace.setEndTime(getLongValue(node, "endTime", "end_time", "EndTime"));
        trace.setStatusCode(getIntValue(node, "statusCode", "status_code", "StatusCode", "Status", "status"));
        trace.setServiceName(getTextValue(node, "serviceName", "service_name", "ServiceName", "Service", "service"));
        trace.setOperationName(getTextValue(node, "operationName", "operation_name", "OperationName", "name", "Name", "SpanName"));
        trace.setSpanCount(getIntValue(node, "spanCount", "span_count", "SpanCount", "Spans", "spans"));

        // 错误状态判断
        Boolean hasError = getBoolValue(node, "hasError", "has_error", "HasError", "error", "Error", "failed", "Failed");
        if (hasError == null) {
            // 尝试通过状态码判断
            Integer statusCode = trace.getStatusCode();
            if (statusCode != null && statusCode >= 400) {
                hasError = true;
            } else {
                hasError = false;
            }
        }
        trace.setHasError(hasError);

        trace.setHttpMethod(getTextValue(node, "httpMethod", "http_method", "HttpMethod", "Method", "method"));
        trace.setHttpUrl(getTextValue(node, "httpUrl", "http_url", "HttpUrl", "Url", "url", "Path", "path"));

        return trace;
    }
    
    /**
     * 解析 Spans
     */
    private List<SpanInfo> parseSpans(JsonNode traceNode) {
        List<SpanInfo> spans = new ArrayList<>();
        
        JsonNode spansNode = traceNode.path("spans");
        if (spansNode.isMissingNode()) {
            spansNode = traceNode.path("Spans");
        }
        
        if (spansNode.isArray()) {
            for (JsonNode spanNode : spansNode) {
                spans.add(parseSpanNode(spanNode));
            }
        }
        
        // 构建树形结构
        return buildSpanTree(spans);
    }
    
    /**
     * 解析单个 Span 节点
     */
    private SpanInfo parseSpanNode(JsonNode node) {
        SpanInfo span = new SpanInfo();
        
        span.setSpanId(getTextValue(node, "spanId", "span_id", "SpanId"));
        span.setParentSpanId(getTextValue(node, "parentSpanId", "parent_span_id", "ParentSpanId"));
        span.setTraceId(getTextValue(node, "traceId", "trace_id", "TraceId"));
        span.setOperationName(getTextValue(node, "operationName", "operation_name", "OperationName", "name"));
        span.setServiceName(getTextValue(node, "serviceName", "service_name", "ServiceName"));
        span.setStartTime(getLongValue(node, "startTime", "start_time", "StartTime"));
        span.setEndTime(getLongValue(node, "endTime", "end_time", "EndTime"));
        span.setDuration(getLongValue(node, "duration", "Duration"));
        span.setStatusCode(getIntValue(node, "statusCode", "status_code", "StatusCode"));
        span.setStatusMessage(getTextValue(node, "statusMessage", "status_message", "StatusMessage"));
        span.setSpanKind(getTextValue(node, "spanKind", "span_kind", "SpanKind", "kind"));
        
        return span;
    }
    
    /**
     * 构建 Span 树形结构
     */
    private List<SpanInfo> buildSpanTree(List<SpanInfo> spans) {
        Map<String, SpanInfo> spanMap = new HashMap<>();
        List<SpanInfo> roots = new ArrayList<>();
        
        for (SpanInfo span : spans) {
            spanMap.put(span.getSpanId(), span);
            span.setChildren(new ArrayList<>());
        }
        
        for (SpanInfo span : spans) {
            String parentId = span.getParentSpanId();
            if (parentId == null || parentId.isEmpty() || !spanMap.containsKey(parentId)) {
                roots.add(span);
            } else {
                spanMap.get(parentId).getChildren().add(span);
            }
        }
        
        return roots;
    }
    
    // 辅助方法
    private String getTextValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return null;
    }
    
    private Long getLongValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asLong();
            }
        }
        return null;
    }

    private Integer getIntValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asInt();
            }
        }
        return null;
    }

    private Boolean getBoolValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asBoolean();
            }
        }
        return null;
    }
}

