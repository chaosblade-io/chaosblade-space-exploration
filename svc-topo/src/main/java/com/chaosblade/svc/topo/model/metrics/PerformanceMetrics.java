package com.chaosblade.svc.topo.model.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 性能指标
 * 包括请求处理、延迟、吞吐量等性能相关指标
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PerformanceMetrics {

    // 请求指标
    @JsonProperty("requestsPerSecond")
    private Double requestsPerSecond;

    @JsonProperty("requestsTotal")
    private Long requestsTotal;

    @JsonProperty("requestDurationP50")
    private Double requestDurationP50;

    @JsonProperty("requestDurationP95")
    private Double requestDurationP95;

    @JsonProperty("requestDurationP99")
    private Double requestDurationP99;

    // 错误指标
    @JsonProperty("errorRate")
    private Double errorRate;

    @JsonProperty("errorsTotal")
    private Long errorsTotal;

    @JsonProperty("errors4xxTotal")
    private Long errors4xxTotal;

    @JsonProperty("errors5xxTotal")
    private Long errors5xxTotal;

    // 并发指标
    @JsonProperty("concurrentConnections")
    private Integer concurrentConnections;

    @JsonProperty("activeRequests")
    private Integer activeRequests;

    // 队列指标
    @JsonProperty("queueDepth")
    private Integer queueDepth;

    @JsonProperty("queueLatency")
    private Double queueLatency;

    // Getters and Setters
    public Double getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(Double requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public Long getRequestsTotal() {
        return requestsTotal;
    }

    public void setRequestsTotal(Long requestsTotal) {
        this.requestsTotal = requestsTotal;
    }

    public Double getRequestDurationP50() {
        return requestDurationP50;
    }

    public void setRequestDurationP50(Double requestDurationP50) {
        this.requestDurationP50 = requestDurationP50;
    }

    public Double getRequestDurationP95() {
        return requestDurationP95;
    }

    public void setRequestDurationP95(Double requestDurationP95) {
        this.requestDurationP95 = requestDurationP95;
    }

    public Double getRequestDurationP99() {
        return requestDurationP99;
    }

    public void setRequestDurationP99(Double requestDurationP99) {
        this.requestDurationP99 = requestDurationP99;
    }

    public Double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(Double errorRate) {
        this.errorRate = errorRate;
    }

    public Long getErrorsTotal() {
        return errorsTotal;
    }

    public void setErrorsTotal(Long errorsTotal) {
        this.errorsTotal = errorsTotal;
    }

    public Long getErrors4xxTotal() {
        return errors4xxTotal;
    }

    public void setErrors4xxTotal(Long errors4xxTotal) {
        this.errors4xxTotal = errors4xxTotal;
    }

    public Long getErrors5xxTotal() {
        return errors5xxTotal;
    }

    public void setErrors5xxTotal(Long errors5xxTotal) {
        this.errors5xxTotal = errors5xxTotal;
    }

    public Integer getConcurrentConnections() {
        return concurrentConnections;
    }

    public void setConcurrentConnections(Integer concurrentConnections) {
        this.concurrentConnections = concurrentConnections;
    }

    public Integer getActiveRequests() {
        return activeRequests;
    }

    public void setActiveRequests(Integer activeRequests) {
        this.activeRequests = activeRequests;
    }

    public Integer getQueueDepth() {
        return queueDepth;
    }

    public void setQueueDepth(Integer queueDepth) {
        this.queueDepth = queueDepth;
    }

    public Double getQueueLatency() {
        return queueLatency;
    }

    public void setQueueLatency(Double queueLatency) {
        this.queueLatency = queueLatency;
    }
}

