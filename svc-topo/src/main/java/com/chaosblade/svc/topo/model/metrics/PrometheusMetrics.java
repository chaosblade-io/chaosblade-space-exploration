package com.chaosblade.svc.topo.model.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * Prometheus 指标数据模型
 * 用于存储从 Prometheus 查询的指标数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrometheusMetrics {

    /**
     * 资源使用指标
     */
    @JsonProperty("resources")
    private ResourceMetrics resources;

    /**
     * 性能指标
     */
    @JsonProperty("performance")
    private PerformanceMetrics performance;

    /**
     * 健康状态指标
     */
    @JsonProperty("health")
    private HealthMetrics health;

    /**
     * 自定义业务指标
     */
    @JsonProperty("custom")
    private Map<String, Object> custom;

    /**
     * 指标采集时间戳
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    public PrometheusMetrics() {
        this.custom = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public ResourceMetrics getResources() {
        return resources;
    }

    public void setResources(ResourceMetrics resources) {
        this.resources = resources;
    }

    public PerformanceMetrics getPerformance() {
        return performance;
    }

    public void setPerformance(PerformanceMetrics performance) {
        this.performance = performance;
    }

    public HealthMetrics getHealth() {
        return health;
    }

    public void setHealth(HealthMetrics health) {
        this.health = health;
    }

    public Map<String, Object> getCustom() {
        return custom;
    }

    public void setCustom(Map<String, Object> custom) {
        this.custom = custom;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public void addCustomMetric(String key, Object value) {
        if (this.custom == null) {
            this.custom = new HashMap<>();
        }
        this.custom.put(key, value);
    }
}

