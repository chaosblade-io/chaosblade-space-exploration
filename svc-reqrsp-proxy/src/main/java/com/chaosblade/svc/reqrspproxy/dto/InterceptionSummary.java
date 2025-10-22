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

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/** 拦截摘要信息 */
public class InterceptionSummary {

  private String sessionId;
  private String namespace;
  private String serviceName;
  private String mode; // "RECORDING_WITH_INTERCEPTION" 或 "INTERCEPTION_ONLY"
  private Integer recordingRulesCount;
  private Integer interceptionRulesCount;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private LocalDateTime startedAt;

  public InterceptionSummary() {}

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

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

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public Integer getRecordingRulesCount() {
    return recordingRulesCount;
  }

  public void setRecordingRulesCount(Integer recordingRulesCount) {
    this.recordingRulesCount = recordingRulesCount;
  }

  public Integer getInterceptionRulesCount() {
    return interceptionRulesCount;
  }

  public void setInterceptionRulesCount(Integer interceptionRulesCount) {
    this.interceptionRulesCount = interceptionRulesCount;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime startedAt) {
    this.startedAt = startedAt;
  }

  @Override
  public String toString() {
    return "InterceptionSummary{"
        + "sessionId='"
        + sessionId
        + '\''
        + ", namespace='"
        + namespace
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", mode='"
        + mode
        + '\''
        + ", recordingRulesCount="
        + recordingRulesCount
        + ", interceptionRulesCount="
        + interceptionRulesCount
        + ", startedAt="
        + startedAt
        + '}';
  }
}
