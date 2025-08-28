package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.trace.TraceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JaegerQueryService 集成测试
 *
 * 注意：此测试需要运行中的Jaeger实例
 * 可以通过以下命令启动Jaeger：
 * docker run -d --name jaeger -p 16686:16686 -p 14268:14268 -p 14250:14250 jaegertracing/all-in-one:latest
 */
@SpringBootTest
@TestPropertySource(properties = {
    "logging.level.com.chaosblade.svc.topo.service=DEBUG"
})
class JaegerQueryServiceIntegrationTest {

    private JaegerQueryService jaegerQueryService;

    private String jaegerHost;
    private int jaegerPort;
    private String testServiceName;
    private String testOperationName;
    private String testTraceId;

    @BeforeEach
    void setUp() {
        jaegerQueryService = new JaegerQueryService();

        // 从JVM系统属性或环境变量获取配置
        jaegerHost = System.getProperty("jaeger.host",
                     System.getenv().getOrDefault("JAEGER_HOST", "demo.jaegertracing.io"));
        jaegerPort = Integer.parseInt(System.getProperty("jaeger.port",
                     System.getenv().getOrDefault("JAEGER_PORT", "16685")));

        testServiceName = System.getProperty("test.service.name",
                         System.getenv().getOrDefault("TEST_SERVICE_NAME", "frontend"));
        testOperationName = System.getProperty("test.operation.name",
                           System.getenv().getOrDefault("TEST_OPERATION_NAME", "all"));
        testTraceId = System.getProperty("test.trace.id",
                     System.getenv().getOrDefault("TEST_TRACE_ID", "2adb358e401447ddf3988dc089ac166f"));
    }

    @Test
    void testQueryTracesByOperationWithMockData() {
        // 使用模拟数据进行集成测试
        long endTime = System.currentTimeMillis() * 1000; // 当前时间（微秒）
        long startTime = endTime - Duration.ofHours(1).toNanos() / 1000; // 1小时前（微秒）

        // 执行测试
        TraceData result = jaegerQueryService.queryTracesByOperation(
                jaegerHost, jaegerPort, testServiceName, testOperationName, startTime, endTime);

        // 验证结果
        assertNotNull(result, "返回的TraceData不应该为null");
        assertNotNull(result.getData(), "TraceData.data不应该为null");
        assertFalse(result.getData().isEmpty(), "应该返回至少一个trace记录");

        TraceData.TraceRecord trace = result.getData().get(0);
        assertNotNull(trace.getTraceId(), "TraceID不应该为null");
        assertFalse(trace.getTraceId().isEmpty(), "TraceID不应该为空");

        assertNotNull(trace.getSpans(), "Spans不应该为null");
        assertFalse(trace.getSpans().isEmpty(), "应该有至少一个span");

        assertNotNull(trace.getProcesses(), "Processes不应该为null");
        assertFalse(trace.getProcesses().isEmpty(), "应该有至少一个process");
    }

    @Test
    void testQueryTraceByIdWithMockData() {
        // 测试根据TraceID查询
        TraceData result = jaegerQueryService.queryTraceById(jaegerHost, jaegerPort, testTraceId);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertFalse(result.getData().isEmpty());

        TraceData.TraceRecord trace = result.getData().get(0);
        assertEquals(testTraceId, trace.getTraceId(), "TraceID应该与查询的ID一致");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JAEGER_INTEGRATION_TEST", matches = "false")
    void testQueryTracesByOperationWithRealJaeger() {
        long endTime = System.currentTimeMillis() * 1000;
        long startTime = endTime - Duration.ofHours(24).toNanos() / 1000; // 24小时前

        // 执行测试
        TraceData result = jaegerQueryService.queryTracesByOperation(
                jaegerHost, jaegerPort, testServiceName, testOperationName, startTime, endTime);

        // 验证结果
        assertNotNull(result);
        // 注意：真实的Jaeger查询结果取决于Jaeger中存储的数据
        // 如果没有数据，返回的列表可能为空
        System.out.println("TraceData: " + result);
        System.out.println("Trace Id: " + result.getData().toString());
    }

    @Test
    void testPerformanceWithMultipleQueries() {
        // 性能测试：执行多次查询
        long endTime = System.currentTimeMillis() * 1000;
        long startTime = endTime - Duration.ofMinutes(30).toNanos() / 1000;

        long startTimeMs = System.currentTimeMillis();

        // 执行10次查询
        for (int i = 0; i < 10; i++) {
            TraceData result = jaegerQueryService.queryTracesByOperation(
                    jaegerHost, jaegerPort, testServiceName + "-" + i, testOperationName, startTime, endTime);
            assertNotNull(result);
        }

        long endTimeMs = System.currentTimeMillis();
        long duration = endTimeMs - startTimeMs;

        // 验证性能：10次查询应该在10秒内完成
        assertTrue(duration < 10000,
                  String.format("10次查询花费时间过长: %d ms", duration));
    }

    @Test
    void testConcurrentQueries() throws InterruptedException {
        // 并发测试
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];

        long endTime = System.currentTimeMillis() * 1000;
        long startTime = endTime - Duration.ofMinutes(15).toNanos() / 1000;

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    TraceData result = jaegerQueryService.queryTracesByOperation(
                            jaegerHost, jaegerPort, testServiceName + "-" + index,
                            testOperationName, startTime, endTime);
                    results[index] = (result != null);
                } catch (Exception e) {
                    results[index] = false;
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证所有线程都成功执行
        for (int i = 0; i < threadCount; i++) {
            assertTrue(results[i], "线程 " + i + " 执行失败");
        }
    }

    @Test
    void testResourceCleanup() {
        // 测试资源清理
        // 执行一次查询
        long endTime = System.currentTimeMillis() * 1000;
        long startTime = endTime - Duration.ofMinutes(5).toNanos() / 1000;

        TraceData result = jaegerQueryService.queryTracesByOperation(
                jaegerHost, jaegerPort, testServiceName, testOperationName, startTime, endTime);
        assertNotNull(result);

        // 调用shutdown方法
        assertDoesNotThrow(() -> jaegerQueryService.shutdown(),
                          "资源清理不应该抛出异常");
    }

}
