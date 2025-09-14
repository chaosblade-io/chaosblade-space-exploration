package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.JaegerSource;
import com.chaosblade.svc.topo.model.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JaegerQueryServiceHttpTest {

    private final JaegerQueryService jaegerQueryService = new JaegerQueryService();

    @Test
    public void testConvertHttpTraceResponse() throws Exception {
        // 准备测试数据 - 使用trace-mock.json的部分内容
        String jsonResponse = "{\n" +
                "  \"data\": [\n" +
                "    {\n" +
                "      \"traceID\": \"2b0b05fdc85d932b5c86887945fb5593\",\n" +
                "      \"spans\": [\n" +
                "        {\n" +
                "          \"traceID\": \"2b0b05fdc85d932b5c86887945fb5593\",\n" +
                "          \"spanID\": \"5ed3557f1c414ffc\",\n" +
                "          \"operationName\": \"oteldemo.ShippingService/ShipOrder\",\n" +
                "          \"references\": [\n" +
                "            {\n" +
                "              \"refType\": \"CHILD_OF\",\n" +
                "              \"traceID\": \"2b0b05fdc85d932b5c86887945fb5593\",\n" +
                "              \"spanID\": \"f886e8c450b97e15\"\n" +
                "            }\n" +
                "          ],\n" +
                "          \"startTime\": 1747797378423935,\n" +
                "          \"duration\": 38,\n" +
                "          \"tags\": [\n" +
                "            {\n" +
                "              \"key\": \"app.shipping.tracking.id\",\n" +
                "              \"type\": \"string\",\n" +
                "              \"value\": \"61d301e3-f663-42f3-b9d2-cf98dbd3a8a5\"\n" +
                "            }\n" +
                "          ],\n" +
                "          \"logs\": [\n" +
                "            {\n" +
                "              \"timestamp\": 1747797378423936,\n" +
                "              \"fields\": [\n" +
                "                {\n" +
                "                  \"key\": \"event\",\n" +
                "                  \"type\": \"string\",\n" +
                "                  \"value\": \"Processing shipping order request\"\n" +
                "                }\n" +
                "              ]\n" +
                "            }\n" +
                "          ],\n" +
                "          \"processID\": \"p1\",\n" +
                "          \"warnings\": null\n" +
                "        }\n" +
                "      ],\n" +
                "      \"processes\": {\n" +
                "        \"p1\": {\n" +
                "          \"serviceName\": \"shippingservice\",\n" +
                "          \"tags\": [\n" +
                "            {\n" +
                "              \"key\": \"client-uuid\",\n" +
                "              \"type\": \"string\",\n" +
                "              \"value\": \"d48e5345-7d1e-4f46-991d-9d135357e9dc\"\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        // 使用反射调用私有方法convertHttpTraceResponse
        Method convertMethod = JaegerQueryService.class.getDeclaredMethod("convertHttpTraceResponse", String.class);
        convertMethod.setAccessible(true);
        TraceData traceData = (TraceData) convertMethod.invoke(jaegerQueryService, jsonResponse);

        // 验证结果
        assertNotNull(traceData);
        assertNotNull(traceData.getData());
        assertEquals(1, traceData.getData().size());
        
        TraceData.TraceRecord traceRecord = traceData.getData().get(0);
        assertEquals("2b0b05fdc85d932b5c86887945fb5593", traceRecord.getTraceId());
        assertEquals(1, traceRecord.getSpans().size());
        
        // 验证span数据
        com.chaosblade.svc.topo.model.trace.SpanData spanData = traceRecord.getSpans().get(0);
        assertEquals("2b0b05fdc85d932b5c86887945fb5593", spanData.getTraceId());
        assertEquals("5ed3557f1c414ffc", spanData.getSpanId());
        assertEquals("oteldemo.ShippingService/ShipOrder", spanData.getOperationName());
        assertEquals("p1", spanData.getProcessId());
        assertEquals(Long.valueOf(1747797378423935L), spanData.getStartTime());
        assertEquals(Long.valueOf(38L), spanData.getDuration());
        
        // 验证tags
        assertEquals(1, spanData.getTags().size());
        com.chaosblade.svc.topo.model.trace.SpanData.Tag tag = spanData.getTags().get(0);
        assertEquals("app.shipping.tracking.id", tag.getKey());
        assertEquals("string", tag.getType());
        assertEquals("61d301e3-f663-42f3-b9d2-cf98dbd3a8a5", tag.getValue());
        
        // 验证references
        assertEquals(1, spanData.getReferences().size());
        com.chaosblade.svc.topo.model.trace.SpanData.SpanReference reference = spanData.getReferences().get(0);
        assertEquals("CHILD_OF", reference.getRefType());
        assertEquals("2b0b05fdc85d932b5c86887945fb5593", reference.getTraceId());
        assertEquals("f886e8c450b97e15", reference.getSpanId());
        
        // 验证logs
        assertEquals(1, spanData.getLogs().size());
        com.chaosblade.svc.topo.model.trace.SpanData.LogEntry logEntry = spanData.getLogs().get(0);
        assertEquals(Long.valueOf(1747797378423936L), logEntry.getTimestamp());
        assertEquals(1, logEntry.getFields().size());
        
        com.chaosblade.svc.topo.model.trace.SpanData.Tag logField = logEntry.getFields().get(0);
        assertEquals("event", logField.getKey());
        assertEquals("string", logField.getType());
        assertEquals("Processing shipping order request", logField.getValue());
        
        // 验证processes
        assertEquals(1, traceRecord.getProcesses().size());
        assertTrue(traceRecord.getProcesses().containsKey("p1"));
        
        com.chaosblade.svc.topo.model.trace.ProcessData processData = traceRecord.getProcesses().get("p1");
        assertEquals("shippingservice", processData.getServiceName());
        assertEquals(1, processData.getTags().size());
        
        com.chaosblade.svc.topo.model.trace.SpanData.Tag processTag = processData.getTags().get(0);
        assertEquals("client-uuid", processTag.getKey());
        assertEquals("string", processTag.getType());
        assertEquals("d48e5345-7d1e-4f46-991d-9d135357e9dc", processTag.getValue());
    }
    
    @Test
    public void testQueryTracesByServiceHttpMethod() {
        // 测试queryTracesByServiceHttp方法的参数验证
        JaegerSource jaegerSource = new JaegerSource();
        
        // 测试null host
        jaegerSource.setHost(null);
        jaegerSource.setHttpPort(16686);
        jaegerSource.setEntryService("frontend");
        assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByServiceHttp(jaegerSource, 0, 1000);
        });
        
        // 测试空host
        jaegerSource.setHost("");
        assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByServiceHttp(jaegerSource, 0, 1000);
        });
        
        // 测试无效端口(0)
        jaegerSource.setHost("localhost");
        jaegerSource.setHttpPort(0);
        assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByServiceHttp(jaegerSource, 0, 1000);
        });
        
        // 测试无效端口(65536)
        jaegerSource.setHttpPort(65536);
        assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByServiceHttp(jaegerSource, 0, 1000);
        });
        
        // 测试null service
        jaegerSource.setHttpPort(16686);
        jaegerSource.setEntryService(null);
        assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByServiceHttp(jaegerSource, 0, 1000);
        });
        
        // 测试空service
        jaegerSource.setEntryService("");
        assertThrows(IllegalArgumentException.class, () -> {
            jaegerQueryService.queryTracesByServiceHttp(jaegerSource, 0, 1000);
        });
        
        // 验证正常参数不会抛出异常（虽然实际调用会因为没有Jaeger服务而失败）
        jaegerSource.setEntryService("frontend");
        try {
            jaegerQueryService.queryTracesByServiceHttp(jaegerSource, 0, 1000);
        } catch (RuntimeException e) {
            // 期望的异常，因为没有Jaeger服务运行
            assertTrue(e.getMessage().contains("Failed to query Jaeger"));
        }
    }
}