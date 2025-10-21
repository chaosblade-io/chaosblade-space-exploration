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

package com.chaosblade.svc.taskexecutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kubernetes")
public class KubernetesProperties {
    private String apiUrl;
    private String token;
    private boolean verifySsl = true;
    // client timeouts (ms)
    private Integer requestTimeoutMs = 10000;
    private Integer connectionTimeoutMs = 5000;
    // async executor
    private Integer threadPoolSize = 6;
    // label keys to try for selecting pods by service name
    private java.util.List<String> labelKeys = java.util.List.of("app", "app.kubernetes.io/name");
    // cache ttl seconds
    private Integer cacheTtlSeconds = 30;

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public boolean isVerifySsl() { return verifySsl; }
    public void setVerifySsl(boolean verifySsl) { this.verifySsl = verifySsl; }
    public Integer getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(Integer requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
    public Integer getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(Integer connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
    public Integer getThreadPoolSize() { return threadPoolSize; }
    public void setThreadPoolSize(Integer threadPoolSize) { this.threadPoolSize = threadPoolSize; }
    public java.util.List<String> getLabelKeys() { return labelKeys; }
    public void setLabelKeys(java.util.List<String> labelKeys) { this.labelKeys = labelKeys; }
    public Integer getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(Integer cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }
}

