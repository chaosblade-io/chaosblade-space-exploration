package com.chaosblade.svc.topo.service;

import static org.junit.jupiter.api.Assertions.*;

import com.chaosblade.svc.topo.model.trace.ProcessData;
import com.chaosblade.svc.topo.model.trace.SpanData;
import com.chaosblade.svc.topo.model.trace.TraceData;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Jaeger数据转换器测试
 *
 * <p>测试Jaeger数据模型到项目数据模型的转换逻辑
 */
@ExtendWith(MockitoExtension.class)
class JaegerDataConverterTest {

  @InjectMocks private JaegerQueryService jaegerQueryService;

  private String testServiceName;
  private String testOperationName;
  private long testStartTime;
  private long testEndTime;
  private String testTraceId;

  @BeforeEach
  void setUp() {
    testServiceName = "checkout";
    testOperationName = "oteldemo.CheckoutService/PlaceOrder";
    testEndTime = System.currentTimeMillis() * 1000; // 当前时间（微秒）
    testStartTime = testEndTime - Duration.ofHours(1).toNanos() / 1000; // 1小时前（微秒）
    testTraceId = "abc123def456";
  }

  @Test
  void testMockTraceStructure() {
    // 执行查询
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            "localhost", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

    // 验证结果
    assertNotNull(result);
    assertNotNull(result.getData());
    assertFalse(result.getData().isEmpty());

    TraceData.TraceRecord trace = result.getData().get(0);
    verifyTraceRecord(trace);
  }

  @Test
  void testSpanHierarchy() {
    // 测试span层次结构
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            "localhost", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

    TraceData.TraceRecord trace = result.getData().get(0);

    // 验证span层次结构
    assertEquals(2, trace.getSpans().size(), "应该有2个span（根span + 子span）");

    SpanData rootSpan = null;
    SpanData childSpan = null;

    for (SpanData span : trace.getSpans()) {
      if (span.getParentSpanId() == null) {
        rootSpan = span;
      } else {
        childSpan = span;
      }
    }

    assertNotNull(rootSpan, "应该有一个根span");
    assertNotNull(childSpan, "应该有一个子span");
    assertEquals(rootSpan.getSpanId(), childSpan.getParentSpanId(), "子span应该指向根span");
  }

  @Test
  void testSpanTags() {
    // 测试span标签
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            "localhost", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

    TraceData.TraceRecord trace = result.getData().get(0);

    for (SpanData span : trace.getSpans()) {
      verifySpanTags(span);
    }
  }

  @Test
  void testProcessData() {
    // 测试进程数据
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            "localhost", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

    TraceData.TraceRecord trace = result.getData().get(0);

    Map<String, ProcessData> processes = trace.getProcesses();
    assertNotNull(processes, "Processes不应该为null");
    assertFalse(processes.isEmpty(), "Processes不应该为空");

    // 验证进程信息
    ProcessData process = processes.get("p1");
    assertNotNull(process, "应该有p1进程");
    assertEquals(testServiceName, process.getServiceName(), "服务名应该匹配");
  }

  @Test
  void testTimingData() {
    // 测试时间数据
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            "localhost", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

    TraceData.TraceRecord trace = result.getData().get(0);

    for (SpanData span : trace.getSpans()) {
      assertTrue(span.getStartTime() > 0, "Span开始时间应该大于0");
      assertTrue(span.getDuration() > 0, "Span持续时间应该大于0");

      // 验证时间格式（微秒）
      assertTrue(span.getStartTime() > System.currentTimeMillis() * 100, "开始时间应该是微秒格式");
    }
  }

  @Test
  void testSpanIdGeneration() {
    // 测试SpanID生成
    TraceData result1 =
        jaegerQueryService.queryTracesByOperation(
            "localhost", 16685, testServiceName, testOperationName, testStartTime, testEndTime);
    TraceData result2 =
        jaegerQueryService.queryTracesByOperation(
            "localhost",
            16685,
            testServiceName + "-2",
            testOperationName,
            testStartTime,
            testEndTime);

    TraceData.TraceRecord trace1 = result1.getData().get(0);
    TraceData.TraceRecord trace2 = result2.getData().get(0);

    // 验证不同查询生成不同的ID
    assertNotEquals(trace1.getTraceId(), trace2.getTraceId(), "不同查询应该生成不同的TraceID");

    // 验证SpanID格式
    for (SpanData span : trace1.getSpans()) {
      assertNotNull(span.getSpanId(), "SpanID不应该为null");
      assertTrue(span.getSpanId().length() > 0, "SpanID不应该为空");
      assertEquals(16, span.getSpanId().length(), "SpanID应该是16个字符");
    }
  }

  @Test
  void testTagTypes() {
    // 测试标签类型转换
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            "localhost", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

    TraceData.TraceRecord trace = result.getData().get(0);

    for (SpanData span : trace.getSpans()) {
      for (SpanData.Tag tag : span.getTags()) {
        assertNotNull(tag.getKey(), "Tag key不应该为null");
        assertNotNull(tag.getType(), "Tag type不应该为null");
        assertNotNull(tag.getValue(), "Tag value不应该为null");

        // 验证类型和值的匹配
        switch (tag.getType()) {
          case "string":
            assertTrue(tag.getValue() instanceof String, "String类型的tag值应该是String");
            break;
          case "int64":
            assertTrue(
                tag.getValue() instanceof Integer || tag.getValue() instanceof Long,
                "Int64类型的tag值应该是数字");
            break;
          default:
            // 其他类型暂不验证
            break;
        }
      }
    }
  }

  @Test
  void testMultipleServicesInTrace() {
    // 测试包含多个服务的trace
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            "localhost", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

    TraceData.TraceRecord trace = result.getData().get(0);

    // 验证不同的服务名
    boolean hasMainService = false;
    boolean hasDbService = false;

    for (SpanData span : trace.getSpans()) {
      String serviceName = getServiceNameFromSpanTags(span);
      if (testServiceName.equals(serviceName)) {
        hasMainService = true;
      } else if ((testServiceName + "-db").equals(serviceName)) {
        hasDbService = true;
      }
    }

    assertTrue(hasMainService, "应该有主服务的span");
    assertTrue(hasDbService, "应该有数据库服务的span");
  }

  /** 验证TraceRecord的基本结构 */
  private void verifyTraceRecord(TraceData.TraceRecord trace) {
    assertNotNull(trace.getTraceId(), "TraceID不应该为null");
    assertFalse(trace.getTraceId().isEmpty(), "TraceID不应该为空");

    assertNotNull(trace.getSpans(), "Spans不应该为null");
    assertFalse(trace.getSpans().isEmpty(), "Spans不应该为空");

    assertNotNull(trace.getProcesses(), "Processes不应该为null");
    assertFalse(trace.getProcesses().isEmpty(), "Processes不应该为空");

    // 验证每个span的基本字段
    for (SpanData span : trace.getSpans()) {
      verifySpanBasicFields(span);
    }
  }

  /** 验证Span的基本字段 */
  private void verifySpanBasicFields(SpanData span) {
    assertNotNull(span.getTraceId(), "Span TraceID不应该为null");
    assertNotNull(span.getSpanId(), "Span SpanID不应该为null");
    assertNotNull(span.getOperationName(), "Span OperationName不应该为null");
    assertNotNull(span.getProcessId(), "Span ProcessID不应该为null");

    assertTrue(span.getStartTime() > 0, "Span开始时间应该大于0");
    assertTrue(span.getDuration() >= 0, "Span持续时间应该大于等于0");

    // 验证引用关系
    if (span.getReferences() != null) {
      for (SpanData.SpanReference ref : span.getReferences()) {
        assertNotNull(ref.getRefType(), "引用类型不应该为null");
        assertNotNull(ref.getTraceId(), "引用TraceID不应该为null");
        assertNotNull(ref.getSpanId(), "引用SpanID不应该为null");
      }
    }

    // 验证标签
    if (span.getTags() != null) {
      for (SpanData.Tag tag : span.getTags()) {
        assertNotNull(tag.getKey(), "标签key不应该为null");
        assertNotNull(tag.getType(), "标签type不应该为null");
        assertNotNull(tag.getValue(), "标签value不应该为null");
      }
    }

    // 验证日志
    if (span.getLogs() != null) {
      for (SpanData.LogEntry log : span.getLogs()) {
        assertTrue(log.getTimestamp() > 0, "日志时间戳应该大于0");
        if (log.getFields() != null) {
          for (SpanData.Tag field : log.getFields()) {
            assertNotNull(field.getKey(), "日志字段key不应该为null");
            assertNotNull(field.getType(), "日志字段type不应该为null");
            assertNotNull(field.getValue(), "日志字段value不应该为null");
          }
        }
      }
    }
  }

  /** 验证Span标签 */
  private void verifySpanTags(SpanData span) {
    List<SpanData.Tag> tags = span.getTags();
    assertNotNull(tags, "标签列表不应该为null");

    // 验证必须的标签存在
    boolean hasServiceName = false;
    boolean hasOperationName = false;

    for (SpanData.Tag tag : tags) {
      if ("service.name".equals(tag.getKey())) {
        hasServiceName = true;
        assertEquals(testServiceName, tag.getValue().toString(), "服务名应该匹配");
      } else if ("operation.name".equals(tag.getKey())) {
        hasOperationName = true;
        assertEquals(testOperationName, tag.getValue().toString(), "操作名应该匹配");
      }
    }

    assertTrue(hasServiceName, "应该有service.name标签");
    assertTrue(hasOperationName, "应该有operation.name标签");
  }

  /** 从span标签中获取服务名 */
  private String getServiceNameFromSpanTags(SpanData span) {
    if (span.getTags() != null) {
      for (SpanData.Tag tag : span.getTags()) {
        if ("service.name".equals(tag.getKey())) {
          return tag.getValue().toString();
        }
      }
    }
    return null;
  }
}
