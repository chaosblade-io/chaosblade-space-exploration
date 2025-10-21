/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.SystemApiListResponse;
import com.chaosblade.svc.topo.service.ApiQueryService;
import com.chaosblade.svc.topo.service.TopologyConverterService;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
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