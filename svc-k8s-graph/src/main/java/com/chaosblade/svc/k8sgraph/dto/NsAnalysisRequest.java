package com.chaosblade.svc.k8sgraph.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 命名空间分析请求DTO
 */
public class NsAnalysisRequest {

    @NotBlank(message = "命名空间不能为空")
    private String namespace;

    private Long systemId;

    @Min(value = 1, message = "topN最小值为1")
    @Max(value = 20, message = "topN最大值为20")
    private Integer topN = 5;

    private Map<String, Object> config;

    private String createdBy;

    public NsAnalysisRequest() {}

    public NsAnalysisRequest(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Integer getTopN() {
        return topN;
    }

    public void setTopN(Integer topN) {
        this.topN = topN;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}

