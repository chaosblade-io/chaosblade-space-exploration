package com.chaosblade.svc.taskexecutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "recording")
public class RecordingProperties {
    /**
     * serviceName -> appPort 映射
     */
    private Map<String, Integer> servicePorts = new HashMap<>();

    /**
     * 若未配置某服务端口，使用该默认端口（0 表示占位/未知）
     */
    private int defaultPort = 0;

    public Map<String, Integer> getServicePorts() {
        return servicePorts;
    }

    public void setServicePorts(Map<String, Integer> servicePorts) {
        this.servicePorts = servicePorts;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }
}

