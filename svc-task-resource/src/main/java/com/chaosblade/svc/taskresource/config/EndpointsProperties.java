package com.chaosblade.svc.taskresource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 远程服务端点统一配置
 */
@Component
@ConfigurationProperties(prefix = "endpoints")
public class EndpointsProperties {

    /**
     * svc-task-executor 的基础地址
     */
    private String executorBaseUrl;

    public String getExecutorBaseUrl() {
        return executorBaseUrl;
    }

    public void setExecutorBaseUrl(String executorBaseUrl) {
        this.executorBaseUrl = executorBaseUrl;
    }
}

