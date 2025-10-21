package com.chaosblade.svc.topo.service;

import static org.junit.jupiter.api.Assertions.*;

import com.chaosblade.svc.topo.config.JaegerTestConfig;
import com.chaosblade.svc.topo.model.trace.TraceData;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/** JaegerQueryService 单元测试 */
@ExtendWith(MockitoExtension.class)
class JaegerQueryServiceTest {

  @InjectMocks private JaegerQueryService jaegerQueryService;

  private String validJaegerHost;
  private int validPort;
  private String validServiceName;
  private String validOperationName;
  private long validStartTime;
  private long validEndTime;

  @BeforeEach
  void setUp() {
    // 从配置类中读取参数
    validJaegerHost = JaegerTestConfig.JAEGER_HOST;
    validPort = JaegerTestConfig.JAEGER_PORT;
    validServiceName = JaegerTestConfig.SERVICE_NAME;
    validOperationName = JaegerTestConfig.OPERATION_NAME;
    validEndTime = System.currentTimeMillis(); // 当前时间（毫秒）
    validStartTime = validEndTime - Duration.ofMinutes(1).toMillis(); // 1小时前（毫秒）
  }

  @Test
  void testQueryTracesByOperationWithValidParameters() {
    // 执行测试
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            validJaegerHost,
            validPort,
            validServiceName,
            validOperationName,
            validStartTime,
            validEndTime);

    // 验证结果
    assertNotNull(result);
    assertNotNull(result.getData());
    assertFalse(result.getData().isEmpty());

    TraceData.TraceRecord trace = result.getData().get(0);
    assertNotNull(trace.getTraceId());
    assertNotNull(trace.getSpans());
    assertFalse(trace.getSpans().isEmpty());
    assertNotNull(trace.getProcesses());
  }

  @Test
  void testQueryTracesByOperationWithLimit() {
    // 测试带limit参数的查询
    int limit = 5;
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            validJaegerHost,
            validPort,
            validServiceName,
            validOperationName,
            validStartTime,
            validEndTime,
            limit);

    // 验证结果
    assertNotNull(result);
    assertNotNull(result.getData());
    assertFalse(result.getData().isEmpty());
    assertTrue(result.getData().size() <= limit, "返回的trace数量应该不超过limit值");

    TraceData.TraceRecord trace = result.getData().get(0);
    assertNotNull(trace.getTraceId());
    assertNotNull(trace.getSpans());
    assertFalse(trace.getSpans().isEmpty());
    assertNotNull(trace.getProcesses());
  }

  @Test
  void testQueryTracesByOperationWithNullHost() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  null,
                  validPort,
                  validServiceName,
                  validOperationName,
                  validStartTime,
                  validEndTime);
            });
    assertEquals("Jaeger host cannot be null or empty", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithEmptyHost() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  "",
                  validPort,
                  validServiceName,
                  validOperationName,
                  validStartTime,
                  validEndTime);
            });
    assertEquals("Jaeger host cannot be null or empty", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithBlankHost() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  "   ",
                  validPort,
                  validServiceName,
                  validOperationName,
                  validStartTime,
                  validEndTime);
            });
    assertEquals("Jaeger host cannot be null or empty", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithInvalidPort() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  validJaegerHost,
                  0,
                  validServiceName,
                  validOperationName,
                  validStartTime,
                  validEndTime);
            });
    assertEquals("Port must be between 1 and 65535", exception.getMessage());

    exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  validJaegerHost,
                  70000,
                  validServiceName,
                  validOperationName,
                  validStartTime,
                  validEndTime);
            });
    assertEquals("Port must be between 1 and 65535", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithNullService() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  validJaegerHost,
                  validPort,
                  null,
                  validOperationName,
                  validStartTime,
                  validEndTime);
            });
    assertEquals("Service name cannot be null or empty", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithEmptyService() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  validJaegerHost, validPort, "", validOperationName, validStartTime, validEndTime);
            });
    assertEquals("Service name cannot be null or empty", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithNullOperation() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  validJaegerHost, validPort, validServiceName, null, validStartTime, validEndTime);
            });
    assertEquals("Operation name cannot be null or empty", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithEmptyOperation() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  validJaegerHost, validPort, validServiceName, "", validStartTime, validEndTime);
            });
    assertEquals("Operation name cannot be null or empty", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithInvalidTimeRange() {
    // 开始时间大于结束时间
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  validJaegerHost,
                  validPort,
                  validServiceName,
                  validOperationName,
                  validEndTime,
                  validStartTime);
            });
    assertEquals("Start time must be before end time", exception.getMessage());
  }

  @Test
  void testQueryTracesByOperationWithTooLargeTimeRange() {
    // 时间范围超过7天 (使用毫秒)
    long sevenDaysInMillis = Duration.ofDays(8).toMillis();
    long startTime = validEndTime - sevenDaysInMillis;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              jaegerQueryService.queryTracesByOperation(
                  validJaegerHost,
                  validPort,
                  validServiceName,
                  validOperationName,
                  startTime,
                  validEndTime);
            });
    assertEquals("Time range cannot exceed 7 days", exception.getMessage());
  }

  @Test
  void testMockDataStructure() {
    // 测试模拟数据的结构是否正确
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            validJaegerHost,
            validPort,
            validServiceName,
            validOperationName,
            validStartTime,
            validEndTime);

    assertNotNull(result.getData());
    assertEquals(1, result.getData().size());

    TraceData.TraceRecord trace = result.getData().get(0);
    assertNotNull(trace.getTraceId());
    assertEquals(2, trace.getSpans().size()); // 主span + 子span
    assertEquals(1, trace.getProcesses().size());

    // 验证span的父子关系
    boolean hasRootSpan =
        trace.getSpans().stream().anyMatch(span -> span.getParentSpanId() == null);
    boolean hasChildSpan =
        trace.getSpans().stream().anyMatch(span -> span.getParentSpanId() != null);
    assertTrue(hasRootSpan, "应该有一个根span");
    assertTrue(hasChildSpan, "应该有一个子span");
  }

  @Test
  void testSpanTagsStructure() {
    TraceData result =
        jaegerQueryService.queryTracesByOperation(
            validJaegerHost,
            validPort,
            validServiceName,
            validOperationName,
            validStartTime,
            validEndTime);

    TraceData.TraceRecord trace = result.getData().get(0);

    // 验证每个span都有标签
    trace
        .getSpans()
        .forEach(
            span -> {
              assertNotNull(span.getTags());
              assertFalse(span.getTags().isEmpty());
            });
  }
}
