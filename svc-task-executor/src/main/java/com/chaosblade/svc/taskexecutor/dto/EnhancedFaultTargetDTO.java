package com.chaosblade.svc.taskexecutor.dto;

import java.util.Map;

/**
 * 增强版故障注入目标：用于 /test-cases/simple 接口的返回。
 * 包含 namespace、serviceName 以及 ChaosBlade 风格的 faultDefinition。
 */
public class EnhancedFaultTargetDTO {
    private String namespace;
    private String serviceName;
    private Map<String, Object> faultDefinition;

    public EnhancedFaultTargetDTO() {}

    public EnhancedFaultTargetDTO(String namespace, String serviceName, Map<String, Object> faultDefinition) {
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.faultDefinition = faultDefinition;
    }

    public String getNamespace() { return namespace; }
    public String getServiceName() { return serviceName; }
    public Map<String, Object> getFaultDefinition() { return faultDefinition; }
}

