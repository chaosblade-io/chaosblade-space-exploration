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
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {
  private String baseUrl;
  private int connectTimeoutMs = 10_000;
  private int readTimeoutMs = 30_000;
  private int maxRetries = 3;
  private int backoffMs = 1_000;

  private int ttlSecForInterceptors = 600;
  private int waitAnalyzeTimeoutSec = 600;
  private int waitInterceptorReadySec = 30;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(int readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public int getBackoffMs() {
    return backoffMs;
  }

  public void setBackoffMs(int backoffMs) {
    this.backoffMs = backoffMs;
  }

  public int getTtlSecForInterceptors() {
    return ttlSecForInterceptors;
  }

  public void setTtlSecForInterceptors(int ttlSecForInterceptors) {
    this.ttlSecForInterceptors = ttlSecForInterceptors;
  }

  public int getWaitAnalyzeTimeoutSec() {
    return waitAnalyzeTimeoutSec;
  }

  public void setWaitAnalyzeTimeoutSec(int waitAnalyzeTimeoutSec) {
    this.waitAnalyzeTimeoutSec = waitAnalyzeTimeoutSec;
  }

  public int getWaitInterceptorReadySec() {
    return waitInterceptorReadySec;
  }

  public void setWaitInterceptorReadySec(int waitInterceptorReadySec) {
    this.waitInterceptorReadySec = waitInterceptorReadySec;
  }
}
