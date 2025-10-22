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

package com.chaosblade.svc.topo.model;

import com.chaosblade.svc.topo.model.ApiQueryRequest.TimeRange;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 按API查询指标请求对象 对应 v1_metrics_byapi_request.json 的结构 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetricsByApiRequest {

  /** API ID */
  @JsonProperty("apiId")
  private String apiId;

  /** 时间范围 */
  @JsonProperty("timeRange")
  private TimeRange timeRange;

  /** 代理标签列表 */
  @JsonProperty("proxy-tags")
  private List<String> proxyTags;

  /** 链路模式 */
  @JsonProperty("chainMode")
  private String chainMode;

  /** 百分位数列表 */
  @JsonProperty("percentiles")
  private List<Integer> percentiles;

  // 构造函数
  public MetricsByApiRequest() {}

  // Getter and Setter methods
  public String getApiId() {
    return apiId;
  }

  public void setApiId(String apiId) {
    this.apiId = apiId;
  }

  public TimeRange getTimeRange() {
    return timeRange;
  }

  public void setTimeRange(TimeRange timeRange) {
    this.timeRange = timeRange;
  }

  public List<String> getProxyTags() {
    return proxyTags;
  }

  public void setProxyTags(List<String> proxyTags) {
    this.proxyTags = proxyTags;
  }

  public String getChainMode() {
    return chainMode;
  }

  public void setChainMode(String chainMode) {
    this.chainMode = chainMode;
  }

  public List<Integer> getPercentiles() {
    return percentiles;
  }

  public void setPercentiles(List<Integer> percentiles) {
    this.percentiles = percentiles;
  }
}
