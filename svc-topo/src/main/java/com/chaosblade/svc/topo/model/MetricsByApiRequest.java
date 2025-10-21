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
