package com.chaosblade.svc.taskexecutor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 最简版测试用例：仅包含 faults 列表；每个 fault 仅含 service_name 与 fault_config_id。
 */
public class MinimalSimplifiedTestCaseDTO {
    private List<Fault> faults; // 0/1/2 个

    public static class Fault {
        @JsonProperty("service_name")
        private String serviceName;
        @JsonProperty("fault_config_id")
        private Long faultConfigId; // baseline 情况下缺省
        public Fault() {}
        public Fault(String serviceName, Long faultConfigId) {
            this.serviceName = serviceName;
            this.faultConfigId = faultConfigId;
        }
        public String getServiceName() { return serviceName; }
        public Long getFaultConfigId() { return faultConfigId; }
    }

    public MinimalSimplifiedTestCaseDTO() {}
    public MinimalSimplifiedTestCaseDTO(List<Fault> faults) {
        this.faults = faults;
    }

    public List<Fault> getFaults() { return faults; }
}

