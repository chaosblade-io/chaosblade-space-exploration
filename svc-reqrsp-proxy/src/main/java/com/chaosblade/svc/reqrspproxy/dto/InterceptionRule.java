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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 拦截规则 - 定义要拦截的路径、方法和模拟响应
 */
public class InterceptionRule {
    
    @NotBlank(message = "路径不能为空")
    private String path;
    
    @NotBlank(message = "HTTP方法不能为空")
    @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS", message = "HTTP方法必须是有效值")
    private String method;
    
    @NotNull(message = "模拟响应配置不能为空")
    @Valid
    private MockResponse mockResponse;
    
    public InterceptionRule() {}
    
    public InterceptionRule(String path, String method, MockResponse mockResponse) {
        this.path = path;
        this.method = method;
        this.mockResponse = mockResponse;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public MockResponse getMockResponse() {
        return mockResponse;
    }
    
    public void setMockResponse(MockResponse mockResponse) {
        this.mockResponse = mockResponse;
    }
    
    @Override
    public String toString() {
        return "InterceptionRule{" +
                "path='" + path + '\'' +
                ", method='" + method + '\'' +
                ", mockResponse=" + mockResponse +
                '}';
    }
}
