package com.chaosblade.svc.reqrspproxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 请求重放请求参数
 */
public class ReplayRequest {

    @NotNull(message = "execution_id不能为空")
    @JsonProperty("execution_id")
    @com.fasterxml.jackson.annotation.JsonAlias({"record_id", "excution_id", "detection_task_id"})
    private Long executionId;

    @NotBlank(message = "namespace不能为空")
    private String namespace;

    @NotBlank(message = "service_name不能为空")
    @JsonProperty("service_name")
    private String serviceName;

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}

