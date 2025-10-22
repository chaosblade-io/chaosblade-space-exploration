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

import java.util.Map;

/**
 * 增强版故障注入目标：用于 /test-cases/simple 接口的返回。 包含 namespace、serviceName 以及 ChaosBlade 风格的
 * faultDefinition。
 */
public class EnhancedFaultTargetDTO {
  private String namespace;
  private String serviceName;
  private Map<String, Object> faultDefinition;

  public EnhancedFaultTargetDTO() {}

  public EnhancedFaultTargetDTO(
      String namespace, String serviceName, Map<String, Object> faultDefinition) {
    this.namespace = namespace;
    this.serviceName = serviceName;
    this.faultDefinition = faultDefinition;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getServiceName() {
    return serviceName;
  }

  public Map<String, Object> getFaultDefinition() {
    return faultDefinition;
  }
}
