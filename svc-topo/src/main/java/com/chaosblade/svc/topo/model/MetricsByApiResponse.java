package com.chaosblade.svc.topo.model;

import com.chaosblade.svc.topo.model.ApiQueryRequest.TimeRange;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 按API查询指标响应对象 对应 v1_metrics_byapi_response.json 的结构 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetricsByApiResponse {

  /** 统计信息 */
  @JsonProperty("statistics")
  private Statistics statistics;

  // 构造函数
  public MetricsByApiResponse() {}

  public MetricsByApiResponse(Statistics statistics) {
    this.statistics = statistics;
  }

  // Getter and Setter methods
  public Statistics getStatistics() {
    return statistics;
  }

  public void setStatistics(Statistics statistics) {
    this.statistics = statistics;
  }

  /** 统计信息 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Statistics {
    @JsonProperty("chain")
    private ChainMetrics chain;

    public Statistics() {}

    public Statistics(ChainMetrics chain) {
      this.chain = chain;
    }

    public ChainMetrics getChain() {
      return chain;
    }

    public void setChain(ChainMetrics chain) {
      this.chain = chain;
    }
  }

  /** 链路指标 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ChainMetrics {
    @JsonProperty("timeRange")
    private TimeRange timeRange;

    @JsonProperty("apiId")
    private String apiId;

    @JsonProperty("chainMode")
    private String chainMode;

    @JsonProperty("percentiles")
    private List<Integer> percentiles;

    @JsonProperty("percentileMethod")
    private String percentileMethod;

    @JsonProperty("totalCount")
    private Integer totalCount;

    @JsonProperty("errorCount")
    private Integer errorCount;

    @JsonProperty("errorRate")
    private Double errorRate;

    @JsonProperty("throughputRps")
    private Double throughputRps;

    @JsonProperty("latency")
    private Latency latency;

    public ChainMetrics() {}

    // Getter and Setter methods
    public TimeRange getTimeRange() {
      return timeRange;
    }

    public void setTimeRange(TimeRange timeRange) {
      this.timeRange = timeRange;
    }

    public String getApiId() {
      return apiId;
    }

    public void setApiId(String apiId) {
      this.apiId = apiId;
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

    public String getPercentileMethod() {
      return percentileMethod;
    }

    public void setPercentileMethod(String percentileMethod) {
      this.percentileMethod = percentileMethod;
    }

    public Integer getTotalCount() {
      return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
      this.totalCount = totalCount;
    }

    public Integer getErrorCount() {
      return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
      this.errorCount = errorCount;
    }

    public Double getErrorRate() {
      return errorRate;
    }

    public void setErrorRate(Double errorRate) {
      this.errorRate = errorRate;
    }

    public Double getThroughputRps() {
      return throughputRps;
    }

    public void setThroughputRps(Double throughputRps) {
      this.throughputRps = throughputRps;
    }

    public Latency getLatency() {
      return latency;
    }

    public void setLatency(Latency latency) {
      this.latency = latency;
    }
  }

  /** 延迟指标 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Latency {
    @JsonProperty("p50")
    private Integer p50;

    @JsonProperty("p95")
    private Integer p95;

    @JsonProperty("p99")
    private Integer p99;

    public Latency() {}

    public Latency(Integer p50, Integer p95, Integer p99) {
      this.p50 = p50;
      this.p95 = p95;
      this.p99 = p99;
    }

    // Getter and Setter methods
    public Integer getP50() {
      return p50;
    }

    public void setP50(Integer p50) {
      this.p50 = p50;
    }

    public Integer getP95() {
      return p95;
    }

    public void setP95(Integer p95) {
      this.p95 = p95;
    }

    public Integer getP99() {
      return p99;
    }

    public void setP99(Integer p99) {
      this.p99 = p99;
    }
  }
}
