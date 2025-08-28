package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.trace.TraceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JaegerQueryService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class JaegerQueryServiceTest {

    @InjectMocks
    private JaegerQueryService jaegerQueryService;

    private String validJaegerHost;
    private int validPort;
    private String validServiceName;
    private String validOperationName;
    private long validStartTime;
    private long validEndTime;
    private String validTraceId;

    @BeforeEach
    void setUp() {
        validJaegerHost = "demo.jaegertracing.io";
        validPort = 16685;
        validServiceName = "test-service";
        validOperationName = "test-operation";
        validEndTime = System.currentTimeMillis() * 1000; // 当前时间（微秒）
        validStartTime = validEndTime - Duration.ofHours(1).toNanos() / 1000; // 1小时前（微秒）
        validTraceId = "abc123def456";
    }

    @Test
    void testQueryTracesByOperationWithValidParameters() {
        // 执行测试
        TraceData result = jaegerQueryService.queryTracesByOperation(
                validJaegerHost, validPort, validServiceName, validOperationName, validStartTime, validEndTime);

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
    void testQueryTracesByOperationWithNullHost() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(null, validPort, validServiceName, validOperationName, validStartTime, validEndTime);
        });
        assertEquals("Jaeger host cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithEmptyHost() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation("", validPort, validServiceName, validOperationName, validStartTime, validEndTime);
        });
        assertEquals("Jaeger host cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithBlankHost() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation("   ", validPort, validServiceName, validOperationName, validStartTime, validEndTime);
        });
        assertEquals("Jaeger host cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithInvalidPort() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(validJaegerHost, 0, validServiceName, validOperationName, validStartTime, validEndTime);
        });
        assertEquals("Port must be between 1 and 65535", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(validJaegerHost, 70000, validServiceName, validOperationName, validStartTime, validEndTime);
        });
        assertEquals("Port must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithNullService() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(validJaegerHost, validPort, null, validOperationName, validStartTime, validEndTime);
        });
        assertEquals("Service name cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithEmptyService() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(validJaegerHost, validPort, "", validOperationName, validStartTime, validEndTime);
        });
        assertEquals("Service name cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithNullOperation() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(validJaegerHost, validPort, validServiceName, null, validStartTime, validEndTime);
        });
        assertEquals("Operation name cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithEmptyOperation() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(validJaegerHost, validPort, validServiceName, "", validStartTime, validEndTime);
        });
        assertEquals("Operation name cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithInvalidTimeRange() {
        // 开始时间大于结束时间
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(validJaegerHost, validPort, validServiceName, validOperationName, validEndTime, validStartTime);
        });
        assertEquals("Start time must be before end time", exception.getMessage());
    }

    @Test
    void testQueryTracesByOperationWithTooLargeTimeRange() {
        // 时间范围超过7天
        long sevenDaysInMicros = Duration.ofDays(8).toNanos() / 1000;
        long startTime = validEndTime - sevenDaysInMicros;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByOperation(validJaegerHost, validPort, validServiceName, validOperationName, startTime, validEndTime);
        });
        assertEquals("Time range cannot exceed 7 days", exception.getMessage());
    }

    @Test
    void testQueryTraceByIdWithValidParameters() {
        // 执行测试
        TraceData result = jaegerQueryService.queryTraceById(validJaegerHost, validPort, validTraceId);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getData());
        assertFalse(result.getData().isEmpty());

        TraceData.TraceRecord trace = result.getData().get(0);
        assertNotNull(trace.getTraceId());
        assertEquals(validTraceId, trace.getTraceId());
        assertNotNull(trace.getSpans());
        assertFalse(trace.getSpans().isEmpty());
    }

    @Test
    void testQueryTraceByIdWithNullHost() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTraceById(null, validPort, validTraceId);
        });
        assertEquals("Jaeger host cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTraceByIdWithEmptyHost() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTraceById("", validPort, validTraceId);
        });
        assertEquals("Jaeger host cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTraceByIdWithInvalidPort() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTraceById(validJaegerHost, -1, validTraceId);
        });
        assertEquals("Port must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void testQueryTraceByIdWithNullTraceId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTraceById(validJaegerHost, validPort, null);
        });
        assertEquals("Trace ID cannot be null or empty", exception.getMessage());
    }

    @Test
    void testQueryTraceByIdWithEmptyTraceId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTraceById(validJaegerHost, validPort, "");
        });
        assertEquals("Trace ID cannot be null or empty", exception.getMessage());
    }

    @Test
    void testMockDataStructure() {
        // 测试模拟数据的结构是否正确
        TraceData result = jaegerQueryService.queryTracesByOperation(
                validJaegerHost, validPort, validServiceName, validOperationName, validStartTime, validEndTime);

        assertNotNull(result.getData());
        assertEquals(1, result.getData().size());

        TraceData.TraceRecord trace = result.getData().get(0);
        assertNotNull(trace.getTraceId());
        assertEquals(2, trace.getSpans().size()); // 主span + 子span
        assertEquals(1, trace.getProcesses().size());

        // 验证span的父子关系
        boolean hasRootSpan = trace.getSpans().stream().anyMatch(span -> span.getParentSpanId() == null);
        boolean hasChildSpan = trace.getSpans().stream().anyMatch(span -> span.getParentSpanId() != null);
        assertTrue(hasRootSpan, "应该有一个根span");
        assertTrue(hasChildSpan, "应该有一个子span");
    }

    @Test
    void testSpanTagsStructure() {
        TraceData result = jaegerQueryService.queryTracesByOperation(
                validJaegerHost, validPort, validServiceName, validOperationName, validStartTime, validEndTime);

        TraceData.TraceRecord trace = result.getData().get(0);

        // 验证每个span都有标签
        trace.getSpans().forEach(span -> {
            assertNotNull(span.getTags());
            assertFalse(span.getTags().isEmpty());

            // 验证必须的标签
            boolean hasServiceName = span.getTags().stream()
                    .anyMatch(tag -> "service.name".equals(tag.getKey()));
            assertTrue(hasServiceName, "span应该有service.name标签");
        });
    }
}
