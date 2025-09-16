package com.chaosblade.svc.taskresource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部拓扑服务配置
 */
@Component
@ConfigurationProperties(prefix = "external.topology")
public class ExternalTopologyProperties {
    /**
     * 外部拓扑服务基础地址，例如：http://116.63.51.45:8106
     */
    private String baseUrl;

    /** 超时时间（毫秒），可选 */
    private int timeoutMs = 15000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}

