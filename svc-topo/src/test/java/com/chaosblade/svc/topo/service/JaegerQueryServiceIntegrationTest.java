package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JaegerQueryService 集成测试
 * 
 * 注意：此测试需要运行中的Jaeger实例
 */
@SpringBootTest
class JaegerQueryServiceIntegrationTest {

    private final JaegerQueryService jaegerQueryService = new JaegerQueryService();

    @Test
    void testQueryTracesByOperationWithRealJaeger() {
        // 检查是否可以连接到Jaeger实例
        String jaegerHost = System.getenv().getOrDefault("JAEGER_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("JAEGER_PORT", "16685"));
        
        // 假设环境变量中设置了Jaeger连接信息
        assumeTrue(isJaegerAvailable(jaegerHost, port), "Jaeger服务不可用，跳过集成测试");
        
        // 准备测试数据
        String serviceName = System.getenv().getOrDefault("TEST_SERVICE_NAME", "test-service");
        String operationName = System.getenv().getOrDefault("TEST_OPERATION_NAME", "test-operation");
        
        long endTime = System.currentTimeMillis() * 1000; // 当前时间（微秒）
        long startTime = endTime - 3600000000L; // 1小时前（微秒）

        // 执行测试
        TraceData result = jaegerQueryService.queryTracesByOperation(
                jaegerHost, port, serviceName, operationName, startTime, endTime);

        // 验证结果
        assertNotNull(result);
        // 实际返回的数据取决于Jaeger中存储的内容
    }
    
    /**
     * 检查Jaeger服务是否可用
     * 
     * @param host Jaeger主机
     * @param port Jaeger端口
     * @return 是否可用
     */
    private boolean isJaegerAvailable(String host, int port) {
        // 简单检查，实际应该尝试连接
        return !"unavailable".equals(System.getenv("JAEGER_HOST"));
    }
}