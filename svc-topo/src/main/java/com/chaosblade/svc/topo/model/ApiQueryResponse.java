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

import java.util.List;
import java.util.Map;

/**
 * API查询响应对象
 * 对应 v1_apis_end2end_response.json 的结构
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiQueryResponse {

    @JsonProperty("items")
    private List<ApiItem> items;

    public ApiQueryResponse() {
    }

    public ApiQueryResponse(List<ApiItem> items) {
        this.items = items;
    }

    public List<ApiItem> getItems() {
        return items;
    }

    public void setItems(List<ApiItem> items) {
        this.items = items;
    }

    /**
     * API项
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiItem {
        @JsonProperty("apiId")
        private String apiId;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("namespace")
        private String namespace;

        @JsonProperty("providerService")
        private ProviderService providerService;

        @JsonProperty("method")
        private String method;

        @JsonProperty("url")
        private UrlInfo url;

        @JsonProperty("params")
        private Params params;

        @JsonProperty("body")
        private Body body;

        @JsonProperty("firstSeen")
        private Long firstSeen;

        @JsonProperty("lastSeen")
        private Long lastSeen;

        @JsonProperty("labels")
        private Map<String, String> labels;

        public ApiItem() {
        }

        // Getter and Setter methods
        public String getApiId() {
            return apiId;
        }

        public void setApiId(String apiId) {
            this.apiId = apiId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public ProviderService getProviderService() {
            return providerService;
        }

        public void setProviderService(ProviderService providerService) {
            this.providerService = providerService;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public UrlInfo getUrl() {
            return url;
        }

        public void setUrl(UrlInfo url) {
            this.url = url;
        }

        public Params getParams() {
            return params;
        }

        public void setParams(Params params) {
            this.params = params;
        }

        public Body getBody() {
            return body;
        }

        public void setBody(Body body) {
            this.body = body;
        }

        public Long getFirstSeen() {
            return firstSeen;
        }

        public void setFirstSeen(Long firstSeen) {
            this.firstSeen = firstSeen;
        }

        public Long getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(Long lastSeen) {
            this.lastSeen = lastSeen;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }
    }

    /**
     * 提供者服务信息
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProviderService {
        @JsonProperty("serviceId")
        private String serviceId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("namespace")
        private String namespace;

        public ProviderService() {
        }

        public ProviderService(String serviceId, String name, String namespace) {
            this.serviceId = serviceId;
            this.name = name;
            this.namespace = namespace;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }
    }

    /**
     * URL信息
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UrlInfo {
        @JsonProperty("template")
        private String template;

        @JsonProperty("example")
        private String example;

        @JsonProperty("scheme")
        private String scheme;

        @JsonProperty("host")
        private String host;

        @JsonProperty("port")
        private Integer port;

        @JsonProperty("fullExample")
        private String fullExample;

        public UrlInfo() {
        }

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String template) {
            this.template = template;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getFullExample() {
            return fullExample;
        }

        public void setFullExample(String fullExample) {
            this.fullExample = fullExample;
        }
    }

    /**
     * 参数信息
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Params {
        @JsonProperty("path")
        private List<Param> path;

        @JsonProperty("query")
        private List<Param> query;

        @JsonProperty("headers")
        private List<Param> headers;

        public Params() {
        }

        public List<Param> getPath() {
            return path;
        }

        public void setPath(List<Param> path) {
            this.path = path;
        }

        public List<Param> getQuery() {
            return query;
        }

        public void setQuery(List<Param> query) {
            this.query = query;
        }

        public List<Param> getHeaders() {
            return headers;
        }

        public void setHeaders(List<Param> headers) {
            this.headers = headers;
        }
    }

    /**
     * 参数定义
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Param {
        @JsonProperty("name")
        private String name;

        @JsonProperty("in")
        private String in;

        @JsonProperty("type")
        private String type;

        @JsonProperty("required")
        private Boolean required;

        @JsonProperty("example")
        private Object example;

        @JsonProperty("description")
        private String description;

        public Param() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIn() {
            return in;
        }

        public void setIn(String in) {
            this.in = in;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }

        public Object getExample() {
            return example;
        }

        public void setExample(Object example) {
            this.example = example;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * 请求体信息
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Body {
        @JsonProperty("present")
        private Boolean present;

        @JsonProperty("contentType")
        private String contentType;

        @JsonProperty("schema")
        private Object schema;

        @JsonProperty("example")
        private Object example;

        public Body() {
        }

        public Boolean getPresent() {
            return present;
        }

        public void setPresent(Boolean present) {
            this.present = present;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public Object getSchema() {
            return schema;
        }

        public void setSchema(Object schema) {
            this.schema = schema;
        }

        public Object getExample() {
            return example;
        }

        public void setExample(Object example) {
            this.example = example;
        }
    }
}