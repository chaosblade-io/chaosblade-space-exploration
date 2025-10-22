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

package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 拓扑API查询请求对象
 * 对应 v1_topology_byapi_request.json 的结构
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopologyByApiRequest {

    /**
     * k8s 命名空间
     */
    @JsonProperty("namespace")
    private String namespace;

    /**
     * API ID
     */
    @JsonProperty("apiId")
    private String apiId;

    /**
     * 时间范围
     */
    @JsonProperty("timeRange")
    private ApiQueryRequest.TimeRange timeRange;

    // 构造函数
    public TopologyByApiRequest() {
    }

    // Getter and Setter methods
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public ApiQueryRequest.TimeRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(ApiQueryRequest.TimeRange timeRange) {
        this.timeRange = timeRange;
    }
}