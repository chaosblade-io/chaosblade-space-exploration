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

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 请求模式获取请求参数 */
public class RequestPatternRequest {

  @NotNull(message = "reqDefId不能为空") private Long reqDefId;

  @NotBlank(message = "命名空间不能为空") private String namespace;

  @NotEmpty(message = "服务列表不能为空") private List<String> serviceList;

  /** 执行ID（bigint，必填） */
  @NotNull(message = "execution_id不能为空") @JsonProperty("execution_id")
  @com.fasterxml.jackson.annotation.JsonAlias({"record_id", "excution_id", "detection_task_id"})
  private Long executionId;

  /** 录制持续时间（秒），默认1800秒（30分钟） */
  private Integer durationSec = 1800;

  /** 是否自动发起请求，默认false（手动控制） */
  private Boolean autoTriggerRequest = false;

  /** 请求发起延迟时间（秒），默认60秒 */
  private Integer requestDelaySeconds = 60;

  /** 请求发起次数，默认1次 */
  private Integer requestCount = 1;

  /** 单个请求超时时间（秒），默认120秒 */
  private Integer requestTimeoutSeconds = 120;

  public RequestPatternRequest() {}

  public RequestPatternRequest(Long reqDefId, String namespace, List<String> serviceList) {
    this.reqDefId = reqDefId;
    this.namespace = namespace;
    this.serviceList = serviceList;
  }

  public Long getReqDefId() {
    return reqDefId;
  }

  public void setReqDefId(Long reqDefId) {
    this.reqDefId = reqDefId;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public List<String> getServiceList() {
    return serviceList;
  }

  public void setServiceList(List<String> serviceList) {
    this.serviceList = serviceList;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }

  public Integer getDurationSec() {
    return durationSec;
  }

  public void setDurationSec(Integer durationSec) {
    this.durationSec = durationSec;
  }

  public Boolean getAutoTriggerRequest() {
    return autoTriggerRequest;
  }

  public void setAutoTriggerRequest(Boolean autoTriggerRequest) {
    this.autoTriggerRequest = autoTriggerRequest;
  }

  public Integer getRequestDelaySeconds() {
    return requestDelaySeconds;
  }

  public void setRequestDelaySeconds(Integer requestDelaySeconds) {
    this.requestDelaySeconds = requestDelaySeconds;
  }

  public Integer getRequestCount() {
    return requestCount;
  }

  public void setRequestCount(Integer requestCount) {
    this.requestCount = requestCount;
  }

  public Integer getRequestTimeoutSeconds() {
    return requestTimeoutSeconds;
  }

  public void setRequestTimeoutSeconds(Integer requestTimeoutSeconds) {
    this.requestTimeoutSeconds = requestTimeoutSeconds;
  }

  @Override
  public String toString() {
    return "RequestPatternRequest{"
        + "reqDefId="
        + reqDefId
        + ", namespace='"
        + namespace
        + '\''
        + ", serviceList="
        + serviceList
        + ", executionId="
        + executionId
        + ", durationSec="
        + durationSec
        + ", autoTriggerRequest="
        + autoTriggerRequest
        + ", requestDelaySeconds="
        + requestDelaySeconds
        + ", requestCount="
        + requestCount
        + ", requestTimeoutSeconds="
        + requestTimeoutSeconds
        + '}';
  }
}
