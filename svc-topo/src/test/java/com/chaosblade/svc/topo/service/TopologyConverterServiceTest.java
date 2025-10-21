package com.chaosblade.svc.topo.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chaosblade.svc.topo.model.entity.EntityType;
import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.model.trace.ProcessData;
import com.chaosblade.svc.topo.model.trace.SpanData;
import com.chaosblade.svc.topo.model.trace.TraceData;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TopologyConverterService 单元测试 */
@ExtendWith(MockitoExtension.class)
class TopologyConverterServiceTest {

  @Mock private TraceParserService traceParserService;

  @InjectMocks private TopologyConverterService topologyConverterService;

  private TraceData testTraceData;

  @BeforeEach
  void setUp() {
    testTraceData = createTestTraceData();

    // Mock TraceParserService 方法
    when(traceParserService.extractRpcInterfaces(any())).thenReturn(Collections.emptyList());
    when(traceParserService.extractServiceCalls(any())).thenReturn(Collections.emptyList());
  }

  @Test
  void testConvertTraceToTopology() {
    // 执行转换
    TopologyGraph topology = topologyConverterService.convertTraceToTopology(testTraceData);

    // 验证结果
    assertNotNull(topology);
    assertNotNull(topology.getNodes());
    assertNotNull(topology.getEdges());

    // 验证节点数量（至少应该有命名空间和服务节点）
    assertTrue(topology.getNodes().size() > 0);

    // 验证元数据
    assertNotNull(topology.getMetadata());
    assertNotNull(topology.getMetadata().getTitle());
  }

  @Test
  void testConvertEmptyTraceData() {
    TraceData emptyTrace = new TraceData();
    emptyTrace.setData(Collections.emptyList());

    TopologyGraph topology = topologyConverterService.convertTraceToTopology(emptyTrace);

    assertNotNull(topology);
    assertTrue(topology.getNodes().isEmpty());
    assertTrue(topology.getEdges().isEmpty());
  }

  @Test
  void testConvertNullTraceData() {
    TopologyGraph topology = topologyConverterService.convertTraceToTopology(null);

    assertNotNull(topology);
    assertTrue(topology.getNodes().isEmpty());
    assertTrue(topology.getEdges().isEmpty());
  }

  @Test
  void testServiceNodesCreation() {
    TopologyGraph topology = topologyConverterService.convertTraceToTopology(testTraceData);

    // 查找服务节点
    List<Node> serviceNodes = topology.getNodesByType(EntityType.SERVICE);

    // 应该至少有一个服务节点
    assertTrue(serviceNodes.size() > 0);

    // 验证服务节点属性
    Node serviceNode = serviceNodes.get(0);
    assertNotNull(serviceNode.getEntity());
    assertEquals(EntityType.SERVICE, serviceNode.getEntity().getType());
    assertNotNull(serviceNode.getEntity().getDisplayName());
  }

  @Test
  void testNamespaceNodesCreation() {
    TopologyGraph topology = topologyConverterService.convertTraceToTopology(testTraceData);

    // 查找命名空间节点
    List<Node> namespaceNodes = topology.getNodesByType(EntityType.NAMESPACE);

    // 应该至少有一个命名空间节点（default）
    assertTrue(namespaceNodes.size() > 0);

    // 验证命名空间节点
    Node namespaceNode = namespaceNodes.get(0);
    assertEquals(EntityType.NAMESPACE, namespaceNode.getEntity().getType());
  }

  private TraceData createTestTraceData() {
    TraceData traceData = new TraceData();

    // 创建TraceRecord
    TraceData.TraceRecord record = new TraceData.TraceRecord();
    record.setTraceId("test-trace-id");

    // 创建Process数据
    ProcessData process = new ProcessData();
    process.setServiceName("checkout");

    Map<String, ProcessData> processes = new HashMap<>();
    processes.put("p1", process);
    record.setProcesses(processes);

    // 创建Span数据
    SpanData span = new SpanData();
    span.setSpanId("test-span-id");
    span.setTraceId("test-trace-id");
    span.setOperationName("oteldemo.CheckoutService/PlaceOrder");
    span.setProcessId("p1");
    span.setDuration(100L);

    // 添加service.name标签
    SpanData.Tag serviceTag = new SpanData.Tag();
    serviceTag.setKey("service.name");
    serviceTag.setValue("checkout");

    List<SpanData.Tag> tags = new ArrayList<>();
    tags.add(serviceTag);
    span.setTags(tags);

    List<SpanData> spans = new ArrayList<>();
    spans.add(span);
    record.setSpans(spans);

    List<TraceData.TraceRecord> records = new ArrayList<>();
    records.add(record);
    traceData.setData(records);

    return traceData;
  }
}
