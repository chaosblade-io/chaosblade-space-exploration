/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

