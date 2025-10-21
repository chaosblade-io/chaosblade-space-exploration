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

package com.chaosblade.svc.reqrspproxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;

/**
 * Fixture管理 - 批量更新请求
 */
public class FixtureUpsertRequest {

    /**
     * 命名空间（可选，如果省略则从 x-chaos-orig-authority 头推断）
     */
    private String namespace;

    /**
     * 记录ID（可选，用于分组和清理目的）
     */
    @JsonProperty("recordId")
    private String recordId;

    /**
     * TTL秒数（可选，默认600秒，超时后自动过期）
     */
    @JsonProperty("ttlSec")
    @Positive(message = "ttlSec必须为正数")
    private Integer ttlSec = 600;

    /**
     * 拦截规则项列表
     */
    @NotEmpty(message = "items不能为空")
    @Valid
    private List<FixtureItem> items;

    /**
     * 拦截规则项
     */
    public static class FixtureItem {
        
        /**
         * 服务名称
         */
        @NotNull(message = "serviceName不能为空")
        private String serviceName;

        /**
         * HTTP方法
         */
        @NotNull(message = "method不能为空")
        private String method;

        /**
         * 请求路径
         */
        @NotNull(message = "path不能为空")
        private String path;

        /**
         * 行李令牌匹配条件（任何匹配都会触发规则）
         */
        @NotEmpty(message = "baggageTokens不能为空")
        private List<String> baggageTokens;

        /**
         * 响应状态码
         */
        @NotNull(message = "respStatus不能为空")
        private Integer respStatus;

        /**
         * 响应头
         */
        private Map<String, Object> respHeaders;

        /**
         * 响应体
         */
        @NotNull(message = "respBody不能为空")
        private String respBody;

        // Constructors
        public FixtureItem() {}

        public FixtureItem(String serviceName, String method, String path, List<String> baggageTokens,
                          Integer respStatus, Map<String, Object> respHeaders, String respBody) {
            this.serviceName = serviceName;
            this.method = method;
            this.path = path;
            this.baggageTokens = baggageTokens;
            this.respStatus = respStatus;
            this.respHeaders = respHeaders;
            this.respBody = respBody;
        }

        // Getters and Setters
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public List<String> getBaggageTokens() { return baggageTokens; }
        public void setBaggageTokens(List<String> baggageTokens) { this.baggageTokens = baggageTokens; }

        public Integer getRespStatus() { return respStatus; }
        public void setRespStatus(Integer respStatus) { this.respStatus = respStatus; }

        public Map<String, Object> getRespHeaders() { return respHeaders; }
        public void setRespHeaders(Map<String, Object> respHeaders) { this.respHeaders = respHeaders; }

        public String getRespBody() { return respBody; }
        public void setRespBody(String respBody) { this.respBody = respBody; }

        @Override
        public String toString() {
            return "FixtureItem{" +
                    "serviceName='" + serviceName + '\'' +
                    ", method='" + method + '\'' +
                    ", path='" + path + '\'' +
                    ", baggageTokens=" + baggageTokens +
                    ", respStatus=" + respStatus +
                    ", respHeaders=" + respHeaders +
                    ", respBody='" + respBody + '\'' +
                    '}';
        }
    }

    // Constructors
    public FixtureUpsertRequest() {}

    public FixtureUpsertRequest(String namespace, String recordId, Integer ttlSec, List<FixtureItem> items) {
        this.namespace = namespace;
        this.recordId = recordId;
        this.ttlSec = ttlSec;
        this.items = items;
    }

    // Getters and Setters
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public Integer getTtlSec() { return ttlSec; }
    public void setTtlSec(Integer ttlSec) { this.ttlSec = ttlSec; }

    public List<FixtureItem> getItems() { return items; }
    public void setItems(List<FixtureItem> items) { this.items = items; }

    @Override
    public String toString() {
        return "FixtureUpsertRequest{" +
                "namespace='" + namespace + '\'' +
                ", recordId='" + recordId + '\'' +
                ", ttlSec=" + ttlSec +
                ", items=" + items +
                '}';
    }
}
