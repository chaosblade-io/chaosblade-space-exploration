package com.chaosblade.svc.topo.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chaosblade.svc.topo.model.SystemApiListResponse;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.service.ApiQueryService;
import com.chaosblade.svc.topo.service.TopologyConverterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

class ApiQueryControllerTest {

  @Mock private ApiQueryService apiQueryService;

  @Mock private TopologyConverterService topologyConverterService;

  @InjectMocks private ApiQueryController apiQueryController;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testGetApisBySystemId() {
    // 准备测试数据
    TopologyGraph mockTopology = new TopologyGraph();
    when(topologyConverterService.getCurrentTopology()).thenReturn(mockTopology);

    // 调用被测试的方法
    ResponseEntity<SystemApiListResponse> response = apiQueryController.getApisBySystemId(1L);

    // 验证结果
    assertNotNull(response);
    assertEquals(200, response.getStatusCodeValue());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().getSuccess());
    assertNotNull(response.getBody().getData());

    // 验证mock被调用
    verify(topologyConverterService, times(1)).getCurrentTopology();
  }

  @Test
  void testGetApisBySystemIdWithNullTopology() {
    // 准备测试数据
    when(topologyConverterService.getCurrentTopology()).thenReturn(null);

    // 调用被测试的方法
    ResponseEntity<SystemApiListResponse> response = apiQueryController.getApisBySystemId(1L);

    // 验证结果
    assertNotNull(response);
    assertEquals(200, response.getStatusCodeValue());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().getSuccess());
    assertNotNull(response.getBody().getData());
    assertEquals(0, response.getBody().getData().getTotal().intValue());

    // 验证mock被调用
    verify(topologyConverterService, times(1)).getCurrentTopology();
  }
}
