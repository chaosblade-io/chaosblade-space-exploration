package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.ApiQueryRequest;
import com.chaosblade.svc.topo.model.TopologyByApiRequest;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.service.ApiQueryService;
import com.chaosblade.svc.topo.service.TopologyConverterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiQueryControllerTest {

    @Mock
    private ApiQueryService apiQueryService;

    @Mock
    private TopologyConverterService topologyConverterService;

    @InjectMocks
    private ApiQueryController apiQueryController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testQueryApisWithNullTopology() {
        // 模拟返回null的拓扑图
        when(topologyConverterService.getCurrentTopology()).thenReturn(null);

        // 创建请求
        ApiQueryRequest request = new ApiQueryRequest();
        request.setNamespace("default");

        // 调用方法
        ResponseEntity<?> response = apiQueryController.queryApis(request);

        // 验证结果
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        verify(topologyConverterService).getCurrentTopology();
    }

    @Test
    void testQueryTopologyByApiWithNullTopology() {
        // 模拟返回null的拓扑图
        when(topologyConverterService.getCurrentTopology()).thenReturn(null);

        // 创建请求
        TopologyByApiRequest request = new TopologyByApiRequest();
        request.setNamespace("default");
        request.setApiId("test-api");

        // 调用方法
        ResponseEntity<?> response = apiQueryController.queryTopologyByApi(request);

        // 验证结果
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        verify(topologyConverterService).getCurrentTopology();
    }

    @Test
    void testQueryApisWithValidTopology() {
        // 创建一个模拟的拓扑图
        TopologyGraph mockTopology = new TopologyGraph();
        
        // 模拟返回有效的拓扑图
        when(topologyConverterService.getCurrentTopology()).thenReturn(mockTopology);

        // 创建请求
        ApiQueryRequest request = new ApiQueryRequest();
        request.setNamespace("default");

        // 调用方法
        ResponseEntity<?> response = apiQueryController.queryApis(request);

        // 验证结果
        assertNotNull(response);
        verify(topologyConverterService).getCurrentTopology();
    }

    @Test
    void testQueryTopologyByApiWithValidTopology() {
        // 创建一个模拟的拓扑图
        TopologyGraph mockTopology = new TopologyGraph();
        
        // 模拟返回有效的拓扑图
        when(topologyConverterService.getCurrentTopology()).thenReturn(mockTopology);

        // 创建请求
        TopologyByApiRequest request = new TopologyByApiRequest();
        request.setNamespace("default");
        request.setApiId("test-api");

        // 调用方法
        ResponseEntity<?> response = apiQueryController.queryTopologyByApi(request);

        // 验证结果
        assertNotNull(response);
        verify(topologyConverterService).getCurrentTopology();
    }
}