package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.model.trace.TraceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TopologyAutoRefreshService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TopologyAutoRefreshServiceTest {

    @Mock
    private JaegerQueryService jaegerQueryService;

    @Mock
    private TopologyConverterService topologyConverterService;

    @Mock
    private TraceParserService traceParserService;

    @InjectMocks
    private TopologyAutoRefreshService autoRefreshService;

    @BeforeEach
    void setUp() {
        // 设置测试配置
        ReflectionTestUtils.setField(autoRefreshService, "jaegerHost", "localhost");
        ReflectionTestUtils.setField(autoRefreshService, "jaegerPort", 14250);
        ReflectionTestUtils.setField(autoRefreshService, "serviceName", "frontend");
        ReflectionTestUtils.setField(autoRefreshService, "operationName", "all");
        ReflectionTestUtils.setField(autoRefreshService, "timeRangeMinutes", 15);
        ReflectionTestUtils.setField(autoRefreshService, "autoRefreshEnabled", true);
    }

    @Test
    void testManualRefreshWithValidData() {
        // 准备测试数据
        TraceData mockTraceData = createMockTraceData();
        TopologyGraph mockTopology = createMockTopology();

        // 配置 mock 行为
        when(jaegerQueryService.queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(mockTraceData);
        when(topologyConverterService.convertTraceToTopology(mockTraceData))
                .thenReturn(mockTopology);

        // 执行测试
        assertDoesNotThrow(() -> autoRefreshService.manualRefresh());

        // 验证调用
        verify(jaegerQueryService, times(1))
                .queryTracesByOperation(eq("localhost"), eq(14250), eq("frontend"), eq("all"), anyLong(), anyLong());
        verify(topologyConverterService, times(1))
                .convertTraceToTopology(mockTraceData);
        verify(topologyConverterService, times(1))
                .setCurrentTopology(mockTopology);
    }

    @Test
    void testManualRefreshWithEmptyTraceData() {
        // 配置 mock 行为 - 返回空的 trace 数据
        TraceData emptyTraceData = new TraceData();
        when(jaegerQueryService.queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(emptyTraceData);

        // 执行测试
        assertDoesNotThrow(() -> autoRefreshService.manualRefresh());

        // 验证 - 应该查询了但没有进行转换
        verify(jaegerQueryService, times(1))
                .queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong());
        verify(topologyConverterService, never())
                .convertTraceToTopology(any());
        verify(topologyConverterService, never())
                .setCurrentTopology(any());
    }

    @Test
    void testManualRefreshWithJaegerException() {
        // 配置 mock 行为 - Jaeger 查询抛出异常
        when(jaegerQueryService.queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Jaeger connection failed"));

        // 执行测试
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            autoRefreshService.manualRefresh();
        });

        assertEquals("刷新拓扑数据失败", exception.getMessage());
        
        // 验证调用
        verify(jaegerQueryService, times(1))
                .queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong());
        verify(topologyConverterService, never())
                .convertTraceToTopology(any());
    }

    @Test
    void testGetRefreshStatus() {
        // 执行测试
        TopologyAutoRefreshService.RefreshStatus status = autoRefreshService.getRefreshStatus();

        // 验证结果
        assertNotNull(status);
        assertTrue(status.isEnabled());
        assertEquals("localhost", status.getJaegerHost());
        assertEquals(14250, status.getJaegerPort());
        assertEquals("frontend", status.getServiceName());
        assertEquals("all", status.getOperationName());
        assertEquals(15, status.getTimeRangeSeconds());
        assertEquals(0, status.getSuccessfulRefreshCount());
        assertEquals(0, status.getFailedRefreshCount());
    }

    @Test
    void testEnableAutoRefresh() {
        // 先禁用
        ReflectionTestUtils.setField(autoRefreshService, "autoRefreshEnabled", false);
        
        // 执行启用
        autoRefreshService.enableAutoRefresh();
        
        // 验证状态
        TopologyAutoRefreshService.RefreshStatus status = autoRefreshService.getRefreshStatus();
        assertTrue(status.isEnabled());
    }

    @Test
    void testDisableAutoRefresh() {
        // 执行禁用
        autoRefreshService.disableAutoRefresh();
        
        // 验证状态
        TopologyAutoRefreshService.RefreshStatus status = autoRefreshService.getRefreshStatus();
        assertFalse(status.isEnabled());
    }

    @Test
    void testUpdateJaegerConfig() {
        // 执行配置更新
        autoRefreshService.updateJaegerConfig("new-host", 16685, "new-service", "new-operation", 30);
        
        // 验证配置
        TopologyAutoRefreshService.RefreshStatus status = autoRefreshService.getRefreshStatus();
        assertEquals("new-host", status.getJaegerHost());
        assertEquals(16685, status.getJaegerPort());
        assertEquals("new-service", status.getServiceName());
        assertEquals("new-operation", status.getOperationName());
        assertEquals(30, status.getTimeRangeSeconds());
    }

    @Test
    void testRefreshTopologyPeriodicallySKippedWhenDisabled() {
        // 禁用自动刷新
        ReflectionTestUtils.setField(autoRefreshService, "autoRefreshEnabled", false);
        
        // 执行定时刷新
        autoRefreshService.refreshTopologyPeriodically();
        
        // 验证没有调用 Jaeger 查询
        verify(jaegerQueryService, never())
                .queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void testRefreshTopologyPeriodicallySKippedWhenAlreadyRefreshing() {
        // 设置正在刷新状态
        ReflectionTestUtils.setField(autoRefreshService, "isRefreshing", true);
        
        // 执行定时刷新
        autoRefreshService.refreshTopologyPeriodically();
        
        // 验证没有调用 Jaeger 查询
        verify(jaegerQueryService, never())
                .queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void testRefreshTopologyPeriodicallWithSuccess() {
        // 准备测试数据
        TraceData mockTraceData = createMockTraceData();
        TopologyGraph mockTopology = createMockTopology();

        // 配置 mock 行为
        when(jaegerQueryService.queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(mockTraceData);
        when(topologyConverterService.convertTraceToTopology(mockTraceData))
                .thenReturn(mockTopology);

        // 执行定时刷新
        autoRefreshService.refreshTopologyPeriodically();

        // 验证成功计数增加
        TopologyAutoRefreshService.RefreshStatus status = autoRefreshService.getRefreshStatus();
        assertEquals(1, status.getSuccessfulRefreshCount());
        assertEquals(0, status.getFailedRefreshCount());
        assertTrue(status.getLastRefreshTime() > 0);
    }

    @Test
    void testRefreshTopologyPeriodicallWithFailure() {
        // 配置 mock 行为 - 抛出异常
        when(jaegerQueryService.queryTracesByOperation(anyString(), anyInt(), anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Connection failed"));

        // 执行定时刷新
        autoRefreshService.refreshTopologyPeriodically();

        // 验证失败计数增加
        TopologyAutoRefreshService.RefreshStatus status = autoRefreshService.getRefreshStatus();
        assertEquals(0, status.getSuccessfulRefreshCount());
        assertEquals(1, status.getFailedRefreshCount());
    }

    /**
     * 创建模拟的 TraceData
     */
    private TraceData createMockTraceData() {
        TraceData traceData = new TraceData();
        TraceData.TraceRecord record = new TraceData.TraceRecord();
        record.setTraceId("test-trace-id");
        traceData.setData(java.util.List.of(record));
        return traceData;
    }

    /**
     * 创建模拟的 TopologyGraph
     */
    private TopologyGraph createMockTopology() {
        TopologyGraph topology = new TopologyGraph();
        topology.getMetadata().setTitle("Test Topology");
        return topology;
    }
}