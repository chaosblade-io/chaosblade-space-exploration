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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** 开始录制请求 */
public class StartRecordingRequest {

  @NotBlank(message = "命名空间不能为空") private String namespace;

  @NotBlank(message = "服务名不能为空") private String serviceName;

  @Positive(message = "应用端口必须是正数") private Integer appPort;

  @NotEmpty(message = "录制规则不能为空") @Valid private List<RecordingRule> rules;

  @Positive(message = "持续时间必须是正数") private Integer durationSec;

  public StartRecordingRequest() {}

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public Integer getAppPort() {
    return appPort;
  }

  public void setAppPort(Integer appPort) {
    this.appPort = appPort;
  }

  public List<RecordingRule> getRules() {
    return rules;
  }

  public void setRules(List<RecordingRule> rules) {
    this.rules = rules;
  }

  public Integer getDurationSec() {
    return durationSec;
  }

  public void setDurationSec(Integer durationSec) {
    this.durationSec = durationSec;
  }

  @Override
  public String toString() {
    return "StartRecordingRequest{"
        + "namespace='"
        + namespace
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", appPort="
        + appPort
        + ", rules="
        + rules
        + ", durationSec="
        + durationSec
        + '}';
  }
}
