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

package com.chaosblade.svc.taskresource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 远程服务端点统一配置 */
@Component
@ConfigurationProperties(prefix = "endpoints")
public class EndpointsProperties {

  /** svc-task-executor 的基础地址 */
  private String executorBaseUrl = "http://localhost:8102";

  public String getExecutorBaseUrl() {
    return executorBaseUrl;
  }

  public void setExecutorBaseUrl(String executorBaseUrl) {
    this.executorBaseUrl = executorBaseUrl;
  }
}
