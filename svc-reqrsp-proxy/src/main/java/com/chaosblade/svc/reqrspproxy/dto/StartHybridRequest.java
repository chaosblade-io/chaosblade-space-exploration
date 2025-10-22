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
import java.util.ArrayList;
import java.util.List;

/** 启动混合模式请求（录制 + 拦截） */
public class StartHybridRequest {

  @NotBlank(message = "命名空间不能为空") private String namespace;

  @NotBlank(message = "服务名不能为空") private String serviceName;

  @Positive(message = "应用端口必须是正数") private Integer appPort;

  @NotEmpty(message = "录制规则不能为空") @Valid private List<RecordingRule> recordingRules;

  @Valid private List<InterceptionRule> interceptionRules = new ArrayList<>();

  @Positive(message = "持续时间必须是正数") private Integer durationSec;

  public StartHybridRequest() {}

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

  public List<RecordingRule> getRecordingRules() {
    return recordingRules;
  }

  public void setRecordingRules(List<RecordingRule> recordingRules) {
    this.recordingRules = recordingRules;
  }

  public List<InterceptionRule> getInterceptionRules() {
    return interceptionRules;
  }

  public void setInterceptionRules(List<InterceptionRule> interceptionRules) {
    this.interceptionRules = interceptionRules != null ? interceptionRules : new ArrayList<>();
  }

  public Integer getDurationSec() {
    return durationSec;
  }

  public void setDurationSec(Integer durationSec) {
    this.durationSec = durationSec;
  }

  @Override
  public String toString() {
    return "StartHybridRequest{"
        + "namespace='"
        + namespace
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", appPort="
        + appPort
        + ", recordingRules="
        + recordingRules
        + ", interceptionRules="
        + interceptionRules
        + ", durationSec="
        + durationSec
        + '}';
  }
}
