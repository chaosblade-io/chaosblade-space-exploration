package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4: Trace分析结果
 */
public class TraceAnalysisResult {
    
    /** 分析的服务名 */
    private String serviceName;
    
    /** 分析的API摘要列表 */
    private List<ApiTraceSummary> apiSummaries = new ArrayList<>();
    
    /** 选中的代表性Trace列表 */
    private List<SelectedTrace> selectedTraces = new ArrayList<>();
    
    /** LLM分析结论 */
    private String llmAnalysis;
    
    /** 发现的问题列表 */
    private List<TraceIssue> issues = new ArrayList<>();
    
    // Getters and Setters
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public List<ApiTraceSummary> getApiSummaries() { return apiSummaries; }
    public void setApiSummaries(List<ApiTraceSummary> apiSummaries) { this.apiSummaries = apiSummaries; }
    
    public List<SelectedTrace> getSelectedTraces() { return selectedTraces; }
    public void setSelectedTraces(List<SelectedTrace> selectedTraces) { this.selectedTraces = selectedTraces; }
    
    public String getLlmAnalysis() { return llmAnalysis; }
    public void setLlmAnalysis(String llmAnalysis) { this.llmAnalysis = llmAnalysis; }
    
    public List<TraceIssue> getIssues() { return issues; }
    public void setIssues(List<TraceIssue> issues) { this.issues = issues; }
    
    /**
     * API统计摘要
     */
    public static class ApiTraceSummary {
        private String serviceName;
        private String spanName;
        private double totalRps;        // 总请求率
        private double failedRps;       // 错误率
        private double p50Latency;      // P50延迟
        private double p95Latency;      // P95延迟
        private double p99Latency;      // P99延迟
        
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getSpanName() { return spanName; }
        public void setSpanName(String spanName) { this.spanName = spanName; }
        public double getTotalRps() { return totalRps; }
        public void setTotalRps(double totalRps) { this.totalRps = totalRps; }
        public double getFailedRps() { return failedRps; }
        public void setFailedRps(double failedRps) { this.failedRps = failedRps; }
        public double getP50Latency() { return p50Latency; }
        public void setP50Latency(double p50Latency) { this.p50Latency = p50Latency; }
        public double getP95Latency() { return p95Latency; }
        public void setP95Latency(double p95Latency) { this.p95Latency = p95Latency; }
        public double getP99Latency() { return p99Latency; }
        public void setP99Latency(double p99Latency) { this.p99Latency = p99Latency; }
    }
    
    /**
     * 选中的代表性Trace
     */
    public static class SelectedTrace {
        private String traceId;
        private String serviceName;
        private String spanName;
        private long duration;          // 持续时间(微秒)
        private boolean isError;        // 是否错误trace
        private String selectionReason; // 选择原因: "max_latency" 或 "error_sample"
        private List<SpanInfo> spans = new ArrayList<>();
        
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getSpanName() { return spanName; }
        public void setSpanName(String spanName) { this.spanName = spanName; }
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
        public boolean isError() { return isError; }
        public void setError(boolean error) { isError = error; }
        public String getSelectionReason() { return selectionReason; }
        public void setSelectionReason(String selectionReason) { this.selectionReason = selectionReason; }
        public List<SpanInfo> getSpans() { return spans; }
        public void setSpans(List<SpanInfo> spans) { this.spans = spans; }
    }
    
    /**
     * Span信息（精简版，用于LLM分析）
     */
    public static class SpanInfo {
        private String service;
        private String traceId;
        private String spanId;
        private String parentId;
        private String name;
        private long timestamp;
        private long duration;
        private SpanStatus status;
        private String exceptionType;
        private String exceptionMessage;
        private String exceptionStacktrace; // 只保留第一行
        
        // Getters and Setters
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getSpanId() { return spanId; }
        public void setSpanId(String spanId) { this.spanId = spanId; }
        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
        public SpanStatus getStatus() { return status; }
        public void setStatus(SpanStatus status) { this.status = status; }
        public String getExceptionType() { return exceptionType; }
        public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
        public String getExceptionMessage() { return exceptionMessage; }
        public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }
        public String getExceptionStacktrace() { return exceptionStacktrace; }
        public void setExceptionStacktrace(String exceptionStacktrace) { this.exceptionStacktrace = exceptionStacktrace; }
    }
    
    /**
     * Span状态
     */
    public static class SpanStatus {
        private boolean error;
        private String message;
        
        public boolean isError() { return error; }
        public void setError(boolean error) { this.error = error; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

