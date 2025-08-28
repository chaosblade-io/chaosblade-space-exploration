package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.trace.ProcessData;
import com.chaosblade.svc.topo.model.trace.SpanData;
import com.chaosblade.svc.topo.model.trace.TraceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jaeger数据转换专项测试
 *
 * 测试JaegerQueryService中数据转换相关的逻辑
 */
@ExtendWith(MockitoExtension.class)
class JaegerDataConverterTest {

    private JaegerQueryService jaegerQueryService;

    private String testServiceName;
    private String testOperationName;
    private long testStartTime;
    private long testEndTime;
    private String testTraceId;

    @BeforeEach
    void setUp() {
        jaegerQueryService = new JaegerQueryService();

        testServiceName = "test-service";
        testOperationName = "test-operation";
        testEndTime = System.currentTimeMillis() * 1000;
        testStartTime = testEndTime - Duration.ofHours(1).toNanos() / 1000;
        testTraceId = "test-trace-id-123";
    }

    @Test
    void testMockTraceDataStructure() {
        // 执行查询获取模拟数据
        TraceData result = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

        // 验证TraceData结构
        assertNotNull(result, "TraceData不应该为null");
        assertNotNull(result.getData(), "TraceData.data不应该为null");
        assertEquals(1, result.getData().size(), "应该有一个trace记录");

        TraceData.TraceRecord trace = result.getData().get(0);
        verifyTraceRecord(trace);
    }

    @Test
    void testMockTraceByIdStructure() {
        // 执行根据TraceID查询
        TraceData result = jaegerQueryService.queryTraceById("demo.jaegertracing.io", 16685, testTraceId);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(1, result.getData().size());

        TraceData.TraceRecord trace = result.getData().get(0);
        assertEquals(testTraceId, trace.getTraceId(), "TraceID应该匹配");
        verifyTraceRecord(trace);
    }

    @Test
    void testSpanHierarchy() {
        // 测试span层次结构
        TraceData result = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

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
        TraceData result = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

        TraceData.TraceRecord trace = result.getData().get(0);

        for (SpanData span : trace.getSpans()) {
            verifySpanTags(span);
        }
    }

    @Test
    void testProcessData() {
        // 测试进程数据
        TraceData result = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

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
        TraceData result = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

        TraceData.TraceRecord trace = result.getData().get(0);

        for (SpanData span : trace.getSpans()) {
            assertTrue(span.getStartTime() > 0, "Span开始时间应该大于0");
            assertTrue(span.getDuration() > 0, "Span持续时间应该大于0");

            // 验证时间格式（微秒）
            assertTrue(span.getStartTime() > System.currentTimeMillis() * 100,
                      "开始时间应该是微秒格式");
        }
    }

    @Test
    void testSpanIdGeneration() {
        // 测试SpanID生成
        TraceData result1 = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName, testOperationName, testStartTime, testEndTime);
        TraceData result2 = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName + "-2", testOperationName, testStartTime, testEndTime);

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
        TraceData result = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

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
                        assertTrue(tag.getValue() instanceof Integer || tag.getValue() instanceof Long,
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
        TraceData result = jaegerQueryService.queryTracesByOperation(
                "demo.jaegertracing.io", 16685, testServiceName, testOperationName, testStartTime, testEndTime);

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

    /**
     * 验证TraceRecord的基本结构
     */
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

    /**
     * 验证Span的基本字段
     */
    private void verifySpanBasicFields(SpanData span) {
        assertNotNull(span.getTraceId(), "Span TraceID不应该为null");
        assertNotNull(span.getSpanId(), "Span SpanID不应该为null");
        assertNotNull(span.getOperationName(), "Span OperationName不应该为null");
        assertNotNull(span.getProcessId(), "Span ProcessID不应该为null");

        assertTrue(span.getStartTime() > 0, "Span开始时间应该大于0");
        assertTrue(span.getDuration() > 0, "Span持续时间应该大于0");
    }

    /**
     * 验证Span标签
     */
    private void verifySpanTags(SpanData span) {
        assertNotNull(span.getTags(), "Span tags不应该为null");
        assertFalse(span.getTags().isEmpty(), "Span tags不应该为空");

        // 验证必须的标签
        boolean hasServiceName = false;
        boolean hasSpanKind = false;

        for (SpanData.Tag tag : span.getTags()) {
            assertNotNull(tag.getKey(), "Tag key不应该为null");
            assertNotNull(tag.getType(), "Tag type不应该为null");
            assertNotNull(tag.getValue(), "Tag value不应该为null");

            if ("service.name".equals(tag.getKey())) {
                hasServiceName = true;
            } else if ("span.kind".equals(tag.getKey())) {
                hasSpanKind = true;
            }
        }

        assertTrue(hasServiceName, "Span应该有service.name标签");
        assertTrue(hasSpanKind, "Span应该有span.kind标签");
    }

    /**
     * 从Span标签中获取服务名
     */
    private String getServiceNameFromSpanTags(SpanData span) {
        for (SpanData.Tag tag : span.getTags()) {
            if ("service.name".equals(tag.getKey())) {
                return tag.getValue().toString();
            }
        }
        return null;
    }
}
