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
import java.util.List;

/** 混合模式状态响应 */
public class HybridStatusResponse {

  private String hybridId;
  private String namespace;
  private String serviceName;
  private String status;
  private List<RecordingRule> recordingRules;
  private List<InterceptionRule> interceptionRules;
  private Integer durationSec;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private LocalDateTime startedAt;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private LocalDateTime stoppedAt;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private LocalDateTime expiresAt;

  private String errorMessage;
  private Integer recordedCount;
  private Integer interceptedCount;

  public HybridStatusResponse() {}

  public String getHybridId() {
    return hybridId;
  }

  public void setHybridId(String hybridId) {
    this.hybridId = hybridId;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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
    this.interceptionRules = interceptionRules;
  }

  public Integer getDurationSec() {
    return durationSec;
  }

  public void setDurationSec(Integer durationSec) {
    this.durationSec = durationSec;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public LocalDateTime getStoppedAt() {
    return stoppedAt;
  }

  public void setStoppedAt(LocalDateTime stoppedAt) {
    this.stoppedAt = stoppedAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Integer getRecordedCount() {
    return recordedCount;
  }

  public void setRecordedCount(Integer recordedCount) {
    this.recordedCount = recordedCount;
  }

  public Integer getInterceptedCount() {
    return interceptedCount;
  }

  public void setInterceptedCount(Integer interceptedCount) {
    this.interceptedCount = interceptedCount;
  }

  @Override
  public String toString() {
    return "HybridStatusResponse{"
        + "hybridId='"
        + hybridId
        + '\''
        + ", namespace='"
        + namespace
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", status='"
        + status
        + '\''
        + ", recordingRules="
        + recordingRules
        + ", interceptionRules="
        + interceptionRules
        + ", startedAt="
        + startedAt
        + ", stoppedAt="
        + stoppedAt
        + ", recordedCount="
        + recordedCount
        + ", interceptedCount="
        + interceptedCount
        + '}';
  }
}
