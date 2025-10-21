package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 拓扑API查询请求对象 对应 v1_topology_byapi_request.json 的结构 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopologyByApiRequest {

  /** k8s 命名空间 */
  @JsonProperty("namespace")
  private String namespace;

  /** API ID */
  @JsonProperty("apiId")
  private String apiId;

  /** 时间范围 */
  @JsonProperty("timeRange")
  private ApiQueryRequest.TimeRange timeRange;

  // 构造函数
  public TopologyByApiRequest() {}

  // Getter and Setter methods
  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getApiId() {
    return apiId;
  }

  public void setApiId(String apiId) {
    this.apiId = apiId;
  }

  public ApiQueryRequest.TimeRange getTimeRange() {
    return timeRange;
  }

  public void setTimeRange(ApiQueryRequest.TimeRange timeRange) {
    this.timeRange = timeRange;
  }
}
