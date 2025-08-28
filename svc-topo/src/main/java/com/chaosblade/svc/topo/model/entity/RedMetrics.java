package com.chaosblade.svc.topo.model.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RED指标模型
 * RED = Rate(请求速率), Errors(错误率), Duration(响应时间)
 *
 * 基于topo_schema_design.md的RED指标定义
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RedMetrics {

    /**
     * 请求量计数
     */
    @JsonProperty("count")
    private Integer count;

    /**
     * 错误数
     */
    @JsonProperty("error")
    private Integer error;

    /**
     * 响应时间（毫秒）
     */
    @JsonProperty("rt")
    private Double rt;

    /**
     * 状态（如 success、failure）
     */
    @JsonProperty("status")
    private String status;

    // 构造函数
    public RedMetrics() {
        this.count = 0;
        this.error = 0;
        this.rt = 0.0;
        this.status = "success";
    }

    public RedMetrics(Integer count, Integer error, Double rt, String status) {
        this.count = count;
        this.error = error;
        this.rt = rt;
        this.status = status;
    }

    // Getter and Setter methods
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getError() {
        return error;
    }

    public void setError(Integer error) {
        this.error = error;
    }

    public Double getRt() {
        return rt;
    }

    public void setRt(Double rt) {
        this.rt = rt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 计算错误率
     * @return 错误率百分比 (0-100)
     */
    public double getErrorRate() {
        if (count == null || count == 0) {
            return 0.0;
        }
        return (error != null ? error.doubleValue() : 0.0) / count * 100;
    }

    /**
     * 计算成功率
     * @return 成功率百分比 (0-100)
     */
    public double getSuccessRate() {
        return 100.0 - getErrorRate();
    }

    /**
     * 判断是否为健康状态
     * @return true if error rate < 5%
     */
    public boolean isHealthy() {
        return getErrorRate() < 5.0;
    }

    /**
     * 累加RED指标
     */
    public void addMetrics(RedMetrics other) {
        if (other == null) return;

        this.count = (this.count != null ? this.count : 0) + (other.count != null ? other.count : 0);
        this.error = (this.error != null ? this.error : 0) + (other.error != null ? other.error : 0);

        // 响应时间取平均值
        if (other.rt != null && other.rt > 0) {
            if (this.rt == null || this.rt == 0) {
                this.rt = other.rt;
            } else {
                this.rt = (this.rt + other.rt) / 2;
            }
        }

        // 状态优先级：error > success
        if ("error".equals(other.status) || this.error > 0) {
            this.status = "error";
        }
    }

    /**
     * 创建默认的成功指标
     */
    public static RedMetrics success() {
        return new RedMetrics(1, 0, 0.0, "success");
    }

    /**
     * 创建默认的错误指标
     */
    public static RedMetrics error(double responseTime) {
        return new RedMetrics(1, 1, responseTime, "error");
    }

    @Override
    public String toString() {
        return "RedMetrics{" +
                "count=" + count +
                ", error=" + error +
                ", rt=" + rt +
                ", status='" + status + '\'' +
                ", errorRate=" + String.format("%.2f", getErrorRate()) + "%" +
                '}';
    }
}
