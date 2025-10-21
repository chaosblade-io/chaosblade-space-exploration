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

/**
 * 命名空间列表响应对象
 * 用于封装 /topology/namespaces 接口的响应数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamespacesResponse {

    /**
     * 命名空间名称列表
     */
    @JsonProperty("namespaces")
    private List<String> namespaces;

    // 构造函数
    public NamespacesResponse() {
    }

    public NamespacesResponse(List<String> namespaces) {
        this.namespaces = namespaces;
    }

    // Getter and Setter methods
    public List<String> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(List<String> namespaces) {
        this.namespaces = namespaces;
    }

    @Override
    public String toString() {
        return "NamespacesResponse{" +
                "namespaces=" + namespaces +
                '}';
    }
}