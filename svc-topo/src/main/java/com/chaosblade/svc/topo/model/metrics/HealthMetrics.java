package com.chaosblade.svc.topo.model.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康状态指标
 * 包括健康检查、就绪状态、存活状态等
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthMetrics {

    /**
     * 整体健康状态
     */
    @JsonProperty("status")
    private HealthStatus status;

    /**
     * 健康检查结果
     */
    @JsonProperty("checks")
    private Map<String, Boolean> checks;

    /**
     * 重启次数
     */
    @JsonProperty("restartCount")
    private Integer restartCount;

    /**
     * 运行时长（秒）
     */
    @JsonProperty("uptimeSeconds")
    private Long uptimeSeconds;

    /**
     * 最后重启时间
     */
    @JsonProperty("lastRestartTime")
    private Long lastRestartTime;

    public HealthMetrics() {
        this.checks = new HashMap<>();
        this.status = HealthStatus.UNKNOWN;
    }

    // Getters and Setters
    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public Map<String, Boolean> getChecks() {
        return checks;
    }

    public void setChecks(Map<String, Boolean> checks) {
        this.checks = checks;
    }

    public Integer getRestartCount() {
        return restartCount;
    }

    public void setRestartCount(Integer restartCount) {
        this.restartCount = restartCount;
    }

    public Long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(Long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public Long getLastRestartTime() {
        return lastRestartTime;
    }

    public void setLastRestartTime(Long lastRestartTime) {
        this.lastRestartTime = lastRestartTime;
    }

    public void addCheck(String checkName, boolean passed) {
        if (this.checks == null) {
            this.checks = new HashMap<>();
        }
        this.checks.put(checkName, passed);
    }

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY("healthy"),
        DEGRADED("degraded"),
        UNHEALTHY("unhealthy"),
        UNKNOWN("unknown");

        private final String value;

        HealthStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}

