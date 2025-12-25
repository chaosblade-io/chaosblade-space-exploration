package com.chaosblade.svc.k8sgraph.domain.service;

/**
 * 服务性能指标模型
 */
public class ServiceMetrics {
    
    /** 服务名 */
    private String serviceName;
    
    /** 请求速率 (requests per second) */
    private Double requestsPerSecond;
    
    /** 错误率 (0-100%) */
    private Double errorRate;
    
    /** P50 延迟 (毫秒) */
    private Double p50Latency;
    
    /** P95 延迟 (毫秒) */
    private Double p95Latency;
    
    /** P99 延迟 (毫秒) */
    private Double p99Latency;
    
    /** 平均延迟 (毫秒) */
    private Double avgLatency;
    
    /** 总请求数 */
    private Long totalRequests;
    
    /** 错误请求数 */
    private Long errorRequests;
    
    /** 成功请求数 */
    private Long successRequests;
    
    /** 时间区间开始 (毫秒时间戳) */
    private Long fromTime;
    
    /** 时间区间结束 (毫秒时间戳) */
    private Long toTime;
    
    public ServiceMetrics() {}
    
    public ServiceMetrics(String serviceName) {
        this.serviceName = serviceName;
    }
    
    // Getters and Setters
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public Double getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(Double requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }
    public Double getErrorRate() { return errorRate; }
    public void setErrorRate(Double errorRate) { this.errorRate = errorRate; }
    public Double getP50Latency() { return p50Latency; }
    public void setP50Latency(Double p50Latency) { this.p50Latency = p50Latency; }
    public Double getP95Latency() { return p95Latency; }
    public void setP95Latency(Double p95Latency) { this.p95Latency = p95Latency; }
    public Double getP99Latency() { return p99Latency; }
    public void setP99Latency(Double p99Latency) { this.p99Latency = p99Latency; }
    public Double getAvgLatency() { return avgLatency; }
    public void setAvgLatency(Double avgLatency) { this.avgLatency = avgLatency; }
    public Long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(Long totalRequests) { this.totalRequests = totalRequests; }
    public Long getErrorRequests() { return errorRequests; }
    public void setErrorRequests(Long errorRequests) { this.errorRequests = errorRequests; }
    public Long getSuccessRequests() { return successRequests; }
    public void setSuccessRequests(Long successRequests) { this.successRequests = successRequests; }
    public Long getFromTime() { return fromTime; }
    public void setFromTime(Long fromTime) { this.fromTime = fromTime; }
    public Long getToTime() { return toTime; }
    public void setToTime(Long toTime) { this.toTime = toTime; }
}

