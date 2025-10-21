package com.chaosblade.svc.topo.service;

import static org.junit.jupiter.api.Assertions.*;

import com.chaosblade.svc.topo.model.ApiQueryRequest;
import com.chaosblade.svc.topo.model.ApiQueryResponse;
import com.chaosblade.svc.topo.model.entity.Entity;
import com.chaosblade.svc.topo.model.entity.EntityType;
import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ApiQueryServiceTest {

  private ApiQueryService apiQueryService;

  @BeforeEach
  void setUp() {
    apiQueryService = new ApiQueryService();
  }

  @Test
  void testQueryApisFromTopology() {
    // 创建测试拓扑图
    TopologyGraph topology = createTestTopology();

    // 创建查询请求
    ApiQueryRequest request = new ApiQueryRequest();
    request.setNamespace("default");

    // 执行查询
    ApiQueryResponse response = apiQueryService.queryApisFromTopology(topology, request);

    // 验证结果
    assertNotNull(response);
    assertNotNull(response.getItems());
    assertEquals(2, response.getItems().size());

    // 验证API项的内容
    ApiQueryResponse.ApiItem firstItem = response.getItems().get(0);
    assertEquals("rpc-get-productpage", firstItem.getApiId());
    assertEquals("GET /productpage", firstItem.getDisplayName());
    assertEquals("default", firstItem.getNamespace());
    assertEquals("GET", firstItem.getMethod());
    assertNotNull(firstItem.getProviderService());
    assertEquals("svc-productpage.default", firstItem.getProviderService().getServiceId());
    assertEquals("svc-productpage", firstItem.getProviderService().getName());
  }

  @Test
  void testQueryApisWithServiceFilter() {
    // 创建测试拓扑图
    TopologyGraph topology = createTestTopology();

    // 创建查询请求
    ApiQueryRequest request = new ApiQueryRequest();
    request.setNamespace("default");

    ApiQueryRequest.AppSelector appSelector = new ApiQueryRequest.AppSelector();
    appSelector.setServices(Arrays.asList("svc-productpage"));
    request.setAppSelector(appSelector);

    // 执行查询
    ApiQueryResponse response = apiQueryService.queryApisFromTopology(topology, request);

    // 验证结果
    assertNotNull(response);
    assertNotNull(response.getItems());
    assertEquals(1, response.getItems().size());

    ApiQueryResponse.ApiItem item = response.getItems().get(0);
    assertEquals("svc-productpage", item.getProviderService().getName());
  }

  @Test
  void testQueryApisWithOpenTelemetryDemoNamespace() {
    // 创建测试拓扑图，模拟opentelemetry-demo命名空间
    TopologyGraph topology = createOpenTelemetryDemoTopology();

    // 创建查询请求
    ApiQueryRequest request = new ApiQueryRequest();
    request.setNamespace("opentelemetry-demo");

    // 执行查询
    ApiQueryResponse response = apiQueryService.queryApisFromTopology(topology, request);

    // 验证结果
    assertNotNull(response);
    assertNotNull(response.getItems());
    assertEquals(2, response.getItems().size());

    // 验证API项的内容
    ApiQueryResponse.ApiItem firstItem = response.getItems().get(0);
    assertTrue(firstItem.getApiId().startsWith("rpc-"));
    assertNotNull(firstItem.getProviderService());
    assertTrue(firstItem.getProviderService().getServiceId().startsWith("opentelemetry-demo@"));
    assertNotNull(firstItem.getMethod());
  }

  /** 创建测试用的拓扑图 */
  private TopologyGraph createTestTopology() {
    TopologyGraph topology = new TopologyGraph();

    // 创建RPC节点
    Node rpcNode1 = new Node();
    rpcNode1.setNodeId("rpc-get-productpage");

    Entity entity1 = new Entity();
    entity1.setEntityId("rpc-get-productpage");
    entity1.setType(EntityType.RPC);
    entity1.setDisplayName("GET /productpage");
    entity1.setAppId("svc-productpage.default");
    entity1.getAttributes().put("namespace", "default");
    entity1.getAttributes().put("protocol", "http");
    entity1.setFirstSeen(1756114800000L);
    entity1.setLastSeen(1756118400000L);

    rpcNode1.setEntity(entity1);
    topology.addNode(rpcNode1);

    Node rpcNode2 = new Node();
    rpcNode2.setNodeId("rpc-post-orders");

    Entity entity2 = new Entity();
    entity2.setEntityId("rpc-post-orders");
    entity2.setType(EntityType.RPC);
    entity2.setDisplayName("POST /orders");
    entity2.setAppId("svc-orders.default");
    entity2.getAttributes().put("namespace", "default");
    entity2.getAttributes().put("protocol", "http");
    entity2.setFirstSeen(1756114800000L);
    entity2.setLastSeen(1756118400000L);

    rpcNode2.setEntity(entity2);
    topology.addNode(rpcNode2);

    // 创建非RPC节点（应该被过滤掉）
    Node serviceNode = new Node();
    serviceNode.setNodeId("svc-productpage");

    Entity serviceEntity = new Entity();
    serviceEntity.setEntityId("svc-productpage");
    serviceEntity.setType(EntityType.SERVICE);
    serviceEntity.setDisplayName("productpage service");

    serviceNode.setEntity(serviceEntity);
    topology.addNode(serviceNode);

    return topology;
  }

  /** 创建模拟opentelemetry-demo命名空间的拓扑图 */
  private TopologyGraph createOpenTelemetryDemoTopology() {
    TopologyGraph topology = new TopologyGraph();

    // 创建RPC节点，appId格式为 opentelemetry-demo@{service}.{namespace}
    Node rpcNode1 = new Node();
    rpcNode1.setNodeId("rpc-get-http-localhost-8080-productpage");

    Entity entity1 = new Entity();
    entity1.setEntityId("rpc-get-http-localhost-8080-productpage");
    entity1.setType(EntityType.RPC);
    entity1.setDisplayName("接口【GET http://localhost:8080/productpage】");
    entity1.setAppId("opentelemetry-demo@productpage.default");
    entity1.getAttributes().put("protocol", "http");
    entity1.setFirstSeen(1756114800000L);
    entity1.setLastSeen(1756118400000L);

    rpcNode1.setEntity(entity1);
    topology.addNode(rpcNode1);

    Node rpcNode2 = new Node();
    rpcNode2.setNodeId("rpc-get-http-details-9080-details-0");

    Entity entity2 = new Entity();
    entity2.setEntityId("rpc-get-http-details-9080-details-0");
    entity2.setType(EntityType.RPC);
    entity2.setDisplayName("接口【GET http://details:9080/details/0】");
    entity2.setAppId("opentelemetry-demo@details.default");
    entity2.getAttributes().put("protocol", "http");
    entity2.setFirstSeen(1756114800000L);
    entity2.setLastSeen(1756118400000L);

    rpcNode2.setEntity(entity2);
    topology.addNode(rpcNode2);

    return topology;
  }
}
