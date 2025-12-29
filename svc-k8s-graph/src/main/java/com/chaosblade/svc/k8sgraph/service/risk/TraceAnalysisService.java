package com.chaosblade.svc.k8sgraph.service.risk;

import com.chaosblade.svc.k8sgraph.client.ObservabilityApiClient;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.TraceAnalysisResult;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.TraceAnalysisResult.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 4: Trace分析服务
 * 获取服务的trace数据，选择代表性trace，提取关键信息供LLM分析
 */
@Service
public class TraceAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TraceAnalysisService.class);

    /** 最多分析的API数量（可通过配置覆盖） */
    @org.springframework.beans.factory.annotation.Value("${risk.trace.max-apis-per-service:5}")
    private int maxApisToAnalyze;

    @Autowired
    private ObservabilityApiClient apiClient;
    
    /**
     * 分析指定服务的Trace
     */
    public TraceAnalysisResult analyzeServiceTraces(String serviceName) {
        TraceAnalysisResult result = new TraceAnalysisResult();
        result.setServiceName(serviceName);
        
        // 获取时间范围：今天0点到当前
        long fromMs = LocalDate.now()
            .atStartOfDay()
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
        long toMs = System.currentTimeMillis();
        
        logger.info("Analyzing traces for service {}: from={}, to={}", serviceName, fromMs, toMs);
        
        // Step 1: 获取API摘要统计
        List<ApiTraceSummary> apiSummaries = fetchApiSummaries(serviceName, fromMs, toMs);
        result.setApiSummaries(apiSummaries);
        
        if (apiSummaries.isEmpty()) {
            logger.warn("No API summaries found for service {}", serviceName);
            return result;
        }
        
        // Step 2: 选择最重要的API进行详细分析（按错误率和延迟排序）
        List<ApiTraceSummary> topApis = selectTopApis(apiSummaries, maxApisToAnalyze);
        
        // Step 3: 对每个API获取并选择代表性Trace
        List<SelectedTrace> selectedTraces = new ArrayList<>();
        for (ApiTraceSummary api : topApis) {
            List<SelectedTrace> traces = selectRepresentativeTraces(
                api.getServiceName(), api.getSpanName(), fromMs, toMs);
            selectedTraces.addAll(traces);
        }
        result.setSelectedTraces(selectedTraces);
        
        logger.info("Selected {} representative traces for service {}", 
            selectedTraces.size(), serviceName);
        
        return result;
    }
    
    /**
     * 获取服务的API摘要统计
     */
    private List<ApiTraceSummary> fetchApiSummaries(String serviceName, long fromMs, long toMs) {
        List<ApiTraceSummary> summaries = new ArrayList<>();
        
        JsonNode statsNode = apiClient.getTraceSummary(serviceName, fromMs, toMs);
        if (!statsNode.isArray()) {
            return summaries;
        }
        
        for (JsonNode stat : statsNode) {
            ApiTraceSummary summary = new ApiTraceSummary();
            summary.setServiceName(stat.path("service_name").asText(serviceName));
            summary.setSpanName(stat.path("span_name").asText(""));
            summary.setTotalRps(stat.path("total").asDouble(0));
            summary.setFailedRps(stat.path("failed").asDouble(0));
            
            // 解析延迟分位数
            JsonNode quantiles = stat.path("duration_quantiles");
            if (quantiles.isArray() && quantiles.size() >= 3) {
                summary.setP50Latency(quantiles.get(0).asDouble(0));
                summary.setP95Latency(quantiles.get(1).asDouble(0));
                summary.setP99Latency(quantiles.get(2).asDouble(0));
            }
            
            summaries.add(summary);
        }
        
        logger.info("Fetched {} API summaries for service {}", summaries.size(), serviceName);
        return summaries;
    }
    
    /**
     * 选择最重要的API（按错误率降序，其次按P99延迟降序）
     */
    private List<ApiTraceSummary> selectTopApis(List<ApiTraceSummary> apis, int limit) {
        return apis.stream()
            .sorted((a, b) -> {
                // 首先按错误率降序
                int cmp = Double.compare(b.getFailedRps(), a.getFailedRps());
                if (cmp != 0) return cmp;
                // 其次按P99延迟降序
                return Double.compare(b.getP99Latency(), a.getP99Latency());
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * 为指定API选择代表性Trace
     */
    private List<SelectedTrace> selectRepresentativeTraces(
            String serviceName, String spanName, long fromMs, long toMs) {
        List<SelectedTrace> selected = new ArrayList<>();
        
        JsonNode tracesNode = apiClient.getTraceItemList(serviceName, spanName, fromMs, toMs);
        if (!tracesNode.isArray() || tracesNode.isEmpty()) {
            logger.warn("No traces found for service={}, spanName={}", serviceName, spanName);
            return selected;
        }
        
        // 分离正常和错误trace
        List<JsonNode> normalTraces = new ArrayList<>();
        List<JsonNode> errorTraces = new ArrayList<>();
        
        for (JsonNode trace : tracesNode) {
            boolean isError = trace.path("status").path("error").asBoolean(false);
            if (isError) {
                errorTraces.add(trace);
            } else {
                normalTraces.add(trace);
            }
        }
        
        // 选择延迟最大的正常trace
        if (!normalTraces.isEmpty()) {
            JsonNode maxLatencyTrace = selectMaxLatencyTrace(normalTraces);
            SelectedTrace st = buildSelectedTrace(maxLatencyTrace, serviceName, spanName, 
                false, "max_latency", fromMs, toMs);
            if (st != null) selected.add(st);
        }
        
        // 随机选择一个错误trace
        if (!errorTraces.isEmpty()) {
            JsonNode errorTrace = selectRandomTrace(errorTraces);
            SelectedTrace st = buildSelectedTrace(errorTrace, serviceName, spanName,
                true, "error_sample", fromMs, toMs);
            if (st != null) selected.add(st);
        }

        return selected;
    }

    /**
     * 选择延迟最大的trace
     */
    private JsonNode selectMaxLatencyTrace(List<JsonNode> traces) {
        return traces.stream()
            .max(Comparator.comparingLong(t -> t.path("duration").asLong(0)))
            .orElse(null);
    }

    /**
     * 随机选择一个trace
     */
    private JsonNode selectRandomTrace(List<JsonNode> traces) {
        if (traces.isEmpty()) return null;
        Random random = new Random();
        return traces.get(random.nextInt(traces.size()));
    }

    /**
     * 构建SelectedTrace对象，包含完整的span详情
     */
    private SelectedTrace buildSelectedTrace(JsonNode traceNode, String serviceName,
            String spanName, boolean isError, String reason, long fromMs, long toMs) {
        if (traceNode == null) return null;

        String traceId = traceNode.path("trace_id").asText("");
        if (traceId.isEmpty()) {
            logger.warn("Trace node missing trace_id");
            return null;
        }

        SelectedTrace st = new SelectedTrace();
        st.setTraceId(traceId);
        st.setServiceName(serviceName);
        st.setSpanName(spanName);
        st.setDuration(traceNode.path("duration").asLong(0));
        st.setError(isError);
        st.setSelectionReason(reason);

        // 获取完整的trace详情（span列表）
        JsonNode traceDetail = apiClient.getTraceDetail(traceId);
        if (traceDetail.isArray()) {
            List<SpanInfo> spans = parseSpans(traceDetail);
            st.setSpans(spans);
        }

        return st;
    }

    /**
     * 解析span数组，提取关键信息
     */
    private List<SpanInfo> parseSpans(JsonNode spansArray) {
        List<SpanInfo> spans = new ArrayList<>();

        for (JsonNode spanNode : spansArray) {
            SpanInfo span = new SpanInfo();
            span.setService(spanNode.path("service").asText(""));
            span.setTraceId(spanNode.path("trace_id").asText(""));
            span.setSpanId(spanNode.path("id").asText(""));
            span.setParentId(spanNode.path("parent_id").asText(""));
            span.setName(spanNode.path("name").asText(""));
            span.setTimestamp(spanNode.path("timestamp").asLong(0));
            span.setDuration(spanNode.path("duration").asLong(0));

            // 解析状态
            SpanStatus status = new SpanStatus();
            JsonNode statusNode = spanNode.path("status");
            status.setError(statusNode.path("error").asBoolean(false));
            status.setMessage(statusNode.path("message").asText(""));
            span.setStatus(status);

            // 解析异常信息
            parseExceptionInfo(spanNode, span);

            spans.add(span);
        }

        return spans;
    }

    /**
     * 解析span中的异常信息
     */
    private void parseExceptionInfo(JsonNode spanNode, SpanInfo span) {
        JsonNode events = spanNode.path("events");
        if (!events.isArray()) return;

        for (JsonNode event : events) {
            String eventName = event.path("name").asText("");
            if ("exception".equalsIgnoreCase(eventName)) {
                JsonNode attrs = event.path("attributes");
                if (attrs.isObject()) {
                    span.setExceptionType(attrs.path("exception.type").asText(""));
                    span.setExceptionMessage(attrs.path("exception.message").asText(""));

                    // 只保留堆栈的第一行
                    String stacktrace = attrs.path("exception.stacktrace").asText("");
                    if (!stacktrace.isEmpty()) {
                        int newlineIdx = stacktrace.indexOf('\n');
                        if (newlineIdx > 0) {
                            stacktrace = stacktrace.substring(0, newlineIdx);
                        }
                        span.setExceptionStacktrace(stacktrace);
                    }
                }
                break; // 只取第一个exception事件
            }
        }
    }
}

