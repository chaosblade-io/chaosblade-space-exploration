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
        return getTraceList(null, serviceName, fromMs, toMs);
    }

    /**
     * 获取指定命名空间下服务的 Trace 列表
     */
    public TraceListResponse getTraceList(String namespace, String serviceName, long fromMs, long toMs) {
        logger.info("Getting trace list for service: {}, namespace: {}, from: {}, to: {}", serviceName, namespace, fromMs, toMs);
        
        JsonNode spansArray = apiClient.getTraceList(namespace, serviceName, fromMs, toMs);
        List<TraceInfo> traces = parseSpansToTraces(spansArray);
        
        return new TraceListResponse(traces);
    }
    
    /**
     * 获取 Trace 详情（返回原始数据）
     */
    public Object getTraceDetailRaw(String traceId, String namespace, String serviceName, long fromMs, long toMs) {
        logger.info("Getting trace detail for traceId: {}, namespace: {}, serviceName: {}, from: {}, to: {}", 
            traceId, namespace, serviceName, fromMs, toMs);

        JsonNode spansArray = apiClient.getTraceDetail(traceId, namespace, serviceName, fromMs, toMs);

        if (spansArray == null || spansArray.isMissingNode() ||
            (spansArray.isArray() && spansArray.size() == 0)) {
            return null;
        }

        return spansArray;
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
     * 将 Spans 数组转换为 Traces 列表
     * Coroot 返回的是 spans 数组，需要按 trace_id 分组
     */
    private List<TraceInfo> parseSpansToTraces(JsonNode spansArray) {
        List<TraceInfo> traces = new ArrayList<>();
        
        if (spansArray == null || !spansArray.isArray()) {
            return traces;
        }

        Map<String, List<JsonNode>> traceGroups = new HashMap<>();
        for (JsonNode span : spansArray) {
            String traceId = getTextValue(span, "trace_id", "traceId", "TraceId", "TraceID");
            if (traceId == null || traceId.isEmpty()) {
                traceId = "unknown-" + UUID.randomUUID().toString();
            }
            traceGroups.computeIfAbsent(traceId, k -> new ArrayList<>()).add(span);
        }

        for (Map.Entry<String, List<JsonNode>> entry : traceGroups.entrySet()) {
            TraceInfo trace = buildTraceFromSpans(entry.getKey(), entry.getValue());
            traces.add(trace);
        }

        traces.sort((a, b) -> {
            if (a.getStartTime() == null) return 1;
            if (b.getStartTime() == null) return -1;
            return b.getStartTime().compareTo(a.getStartTime());
        });
        
        return traces;
    }

    /**
     * 从一组 spans 构建 TraceInfo
     */
    private TraceInfo buildTraceFromSpans(String traceId, List<JsonNode> spans) {
        TraceInfo trace = new TraceInfo();
        trace.setTraceId(traceId);
        trace.setSpanCount(spans.size());

        long minStartTime = Long.MAX_VALUE;
        long maxEndTime = 0;
        boolean hasError = false;
        String serviceName = null;
        String operationName = null;

        for (JsonNode span : spans) {
            Long startTime = getLongValue(span, "timestamp", "start_time", "startTime", "StartTime");
            Long duration = getLongValue(span, "duration", "Duration", "duration_ns");
            
            if (startTime != null) {
                minStartTime = Math.min(minStartTime, startTime);
                long endTime = startTime + (duration != null ? duration : 0);
                maxEndTime = Math.max(maxEndTime, endTime);
            }

            if (serviceName == null) {
                serviceName = getTextValue(span, "service_name", "serviceName", "ServiceName", "Service");
            }
            if (operationName == null) {
                operationName = getTextValue(span, "name", "span_name", "operationName", "OperationName");
            }

            Boolean spanError = getBoolValue(span, "error", "hasError", "has_error", "Error");
            if (Boolean.TRUE.equals(spanError)) {
                hasError = true;
            }
        }

        trace.setStartTime(minStartTime == Long.MAX_VALUE ? null : minStartTime);
        trace.setEndTime(maxEndTime == 0 ? null : maxEndTime);
        if (minStartTime != Long.MAX_VALUE && maxEndTime > minStartTime) {
            trace.setDuration(maxEndTime - minStartTime);
        }
        trace.setServiceName(serviceName);
        trace.setOperationName(operationName);
        trace.setHasError(hasError);

        return trace;
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

