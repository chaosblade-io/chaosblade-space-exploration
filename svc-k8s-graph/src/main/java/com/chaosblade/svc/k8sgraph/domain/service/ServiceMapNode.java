package com.chaosblade.svc.k8sgraph.domain.service;

import java.util.Map;

/**
 * 服务拓扑图节点模型
 */
public class ServiceMapNode {

    /** 服务名 */
    private String serviceName;

    /** 服务类型 */
    private String serviceType;

    /** 命名空间 */
    private String namespace;

    /** 服务状态 */
    private String status;

    /** 请求数 */
    private Long requestCount;

    /** 错误数 */
    private Long errorCount;

    /** 平均延迟 (毫秒) */
    private Double avgLatency;

    /** P99 延迟 (毫秒) */
    private Double p99Latency;

    /** 错误率 */
    private Double errorRate;

    /** 额外属性 */
    private Map<String, Object> attributes;

    public ServiceMapNode() {}

    public ServiceMapNode(String serviceName) {
        this.serviceName = serviceName;
    }

    // Getters and Setters
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRequestCount() { return requestCount; }
    public void setRequestCount(Long requestCount) { this.requestCount = requestCount; }
    public Long getErrorCount() { return errorCount; }
    public void setErrorCount(Long errorCount) { this.errorCount = errorCount; }
    public Double getAvgLatency() { return avgLatency; }
    public void setAvgLatency(Double avgLatency) { this.avgLatency = avgLatency; }
    public Double getP99Latency() { return p99Latency; }
    public void setP99Latency(Double p99Latency) { this.p99Latency = p99Latency; }
    public Double getErrorRate() { return errorRate; }
    public void setErrorRate(Double errorRate) { this.errorRate = errorRate; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}

