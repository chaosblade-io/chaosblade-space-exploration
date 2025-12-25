package com.chaosblade.svc.k8sgraph.domain.service;

/**
 * 服务拓扑图边模型 - 服务间调用关系
 */
public class ServiceMapEdge {
    
    /** 调用方服务名 */
    private String sourceService;
    
    /** 被调用方服务名 */
    private String targetService;
    
    /** 调用次数 */
    private Long callCount;
    
    /** 错误次数 */
    private Long errorCount;
    
    /** 平均延迟 (毫秒) */
    private Double avgLatency;
    
    /** P99 延迟 (毫秒) */
    private Double p99Latency;
    
    /** 错误率 */
    private Double errorRate;
    
    public ServiceMapEdge() {}
    
    public ServiceMapEdge(String sourceService, String targetService) {
        this.sourceService = sourceService;
        this.targetService = targetService;
    }
    
    // Getters and Setters
    public String getSourceService() { return sourceService; }
    public void setSourceService(String sourceService) { this.sourceService = sourceService; }
    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }
    public Long getCallCount() { return callCount; }
    public void setCallCount(Long callCount) { this.callCount = callCount; }
    public Long getErrorCount() { return errorCount; }
    public void setErrorCount(Long errorCount) { this.errorCount = errorCount; }
    public Double getAvgLatency() { return avgLatency; }
    public void setAvgLatency(Double avgLatency) { this.avgLatency = avgLatency; }
    public Double getP99Latency() { return p99Latency; }
    public void setP99Latency(Double p99Latency) { this.p99Latency = p99Latency; }
    public Double getErrorRate() { return errorRate; }
    public void setErrorRate(Double errorRate) { this.errorRate = errorRate; }
}

