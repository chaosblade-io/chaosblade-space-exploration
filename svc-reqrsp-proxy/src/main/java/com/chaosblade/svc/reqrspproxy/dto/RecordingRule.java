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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 录制规则 - 定义要录制的路径和方法
 */
public class RecordingRule {
    
    @NotBlank(message = "路径不能为空")
    private String path;
    
    @NotBlank(message = "HTTP方法不能为空")
    @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS", message = "HTTP方法必须是有效值")
    private String method;
    
    public RecordingRule() {}
    
    public RecordingRule(String path, String method) {
        this.path = path;
        this.method = method;
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
    
    @Override
    public String toString() {
        return "RecordingRule{" +
                "path='" + path + '\'' +
                ", method='" + method + '\'' +
                '}';
    }
}
