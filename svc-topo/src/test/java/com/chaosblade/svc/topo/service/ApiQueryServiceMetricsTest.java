package com.chaosblade.svc.topo.service;

import static org.junit.jupiter.api.Assertions.*;

import com.chaosblade.svc.topo.model.ApiQueryRequest;
import com.chaosblade.svc.topo.model.MetricsByApiRequest;
import com.chaosblade.svc.topo.model.MetricsByApiResponse;
import com.chaosblade.svc.topo.model.entity.*;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiQueryServiceMetricsTest {

  private ApiQueryService apiQueryService;
  private TopologyGraph testTopology;

  @BeforeEach
  void setUp() {
    apiQueryService = new ApiQueryService();

    // 创建测试拓扑图
    testTopology = new TopologyGraph();

    // 创建实体
    Entity targetEntity = new Entity();
    targetEntity.setEntityId("rpc-oteldemo-checkoutservice-placeorder");
    targetEntity.setAppId("checkout@default");
    targetEntity.setType(EntityType.RPC);
    targetEntity.setDisplayName("rpc-oteldemo-checkoutservice-placeorder");

    // 创建节点
    Node targetNode = new Node("node-1", targetEntity);

    // 设置RED指标
    RedMetrics redMetrics = new RedMetrics(12450, 20, 370.0, "success");
    targetNode.setRedMetrics(redMetrics);

    // 添加节点到拓扑图
    testTopology.addNode(targetNode);
  }

  @Test
  void testQueryMetricsByApiId() {
    // 创建请求
    MetricsByApiRequest request = new MetricsByApiRequest();
    request.setApiId("rpc-oteldemo-checkoutservice-placeorder");

    // 设置时间范围
    ApiQueryRequest.TimeRange timeRange = new ApiQueryRequest.TimeRange();
    timeRange.setStart(1756114800000L);
    timeRange.setEnd(1756118400000L);
    request.setTimeRange(timeRange);

    request.setChainMode("END_TO_END");
    request.setPercentiles(Arrays.asList(50, 95, 99));

    // 执行查询
    MetricsByApiResponse result = apiQueryService.queryMetricsByApiId(testTopology, request);

    // 验证结果
    assertNotNull(result);
    assertNotNull(result.getStatistics());
    assertNotNull(result.getStatistics().getChain());

    MetricsByApiResponse.ChainMetrics chain = result.getStatistics().getChain();
    assertEquals("rpc-oteldemo-checkoutservice-placeorder", chain.getApiId());
    assertEquals("END_TO_END", chain.getChainMode());
    assertEquals(Arrays.asList(50, 95, 99), chain.getPercentiles());
    assertEquals("TDIGEST", chain.getPercentileMethod());
    assertEquals(Integer.valueOf(12450), chain.getTotalCount());
    assertEquals(Integer.valueOf(20), chain.getErrorCount());
    assertEquals(Double.valueOf(20.0 / 12450), chain.getErrorRate());

    // 验证延迟信息
    assertNotNull(chain.getLatency());
    MetricsByApiResponse.Latency latency = chain.getLatency();
    assertEquals(Integer.valueOf(370), latency.getP99()); // rt保存的是p99
    assertTrue(latency.getP50() > 0);
    assertTrue(latency.getP95() > 0);
  }

  @Test
  void testQueryMetricsByApiIdWithNonExistentApi() {
    // 创建请求
    MetricsByApiRequest request = new MetricsByApiRequest();
    request.setApiId("non-existent-api");

    // 设置时间范围
    ApiQueryRequest.TimeRange timeRange = new ApiQueryRequest.TimeRange();
    timeRange.setStart(1756114800000L);
    timeRange.setEnd(1756118400000L);
    request.setTimeRange(timeRange);

    request.setChainMode("END_TO_END");
    request.setPercentiles(Arrays.asList(50, 95, 99));

    // 执行查询
    MetricsByApiResponse result = apiQueryService.queryMetricsByApiId(testTopology, request);

    // 验证结果
    assertNotNull(result);
    assertNotNull(result.getStatistics());
    assertNotNull(result.getStatistics().getChain());

    // 验证默认值
    MetricsByApiResponse.ChainMetrics chain = result.getStatistics().getChain();
    assertEquals("non-existent-api", chain.getApiId());
    assertEquals(Integer.valueOf(0), chain.getTotalCount());
    assertEquals(Integer.valueOf(0), chain.getErrorCount());
    assertEquals(Double.valueOf(0.0), chain.getErrorRate());

    // 验证延迟信息
    assertNotNull(chain.getLatency());
    MetricsByApiResponse.Latency latency = chain.getLatency();
    assertEquals(Integer.valueOf(0), latency.getP50());
    assertEquals(Integer.valueOf(0), latency.getP95());
    assertEquals(Integer.valueOf(0), latency.getP99());
  }
}
