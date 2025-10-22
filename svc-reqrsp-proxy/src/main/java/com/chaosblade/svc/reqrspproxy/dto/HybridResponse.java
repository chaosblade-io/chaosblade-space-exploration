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

/** 混合模式响应 */
public class HybridResponse {

  private String hybridId;
  private String status;
  private Integer recordingRulesCount;
  private Integer interceptionRulesCount;
  private String message;

  public HybridResponse() {}

  public HybridResponse(
      String hybridId, String status, Integer recordingRulesCount, Integer interceptionRulesCount) {
    this.hybridId = hybridId;
    this.status = status;
    this.recordingRulesCount = recordingRulesCount;
    this.interceptionRulesCount = interceptionRulesCount;
  }

  public HybridResponse(
      String hybridId,
      String status,
      Integer recordingRulesCount,
      Integer interceptionRulesCount,
      String message) {
    this.hybridId = hybridId;
    this.status = status;
    this.recordingRulesCount = recordingRulesCount;
    this.interceptionRulesCount = interceptionRulesCount;
    this.message = message;
  }

  public String getHybridId() {
    return hybridId;
  }

  public void setHybridId(String hybridId) {
    this.hybridId = hybridId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return "HybridResponse{"
        + "hybridId='"
        + hybridId
        + '\''
        + ", status='"
        + status
        + '\''
        + ", recordingRulesCount="
        + recordingRulesCount
        + ", interceptionRulesCount="
        + interceptionRulesCount
        + ", message='"
        + message
        + '\''
        + '}';
  }
}
