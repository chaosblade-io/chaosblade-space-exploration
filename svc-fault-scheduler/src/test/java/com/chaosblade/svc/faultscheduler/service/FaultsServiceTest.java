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

package com.chaosblade.svc.faultscheduler.service;

import com.chaosblade.svc.faultscheduler.api.ChaosBladeApi;
import com.chaosblade.svc.faultscheduler.repository.FaultRedisRepo;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FaultsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FaultsServiceTest {
    
    @Mock
    private ChaosBladeApi bladeApi;
    
    @Mock
    private FaultRedisRepo repo;
    
    @Mock
    private ThreadPoolTaskScheduler scheduler;
    
    @Mock
    private SpecNormalizer normalizer;
    
    private FaultsService faultsService;
    
    @BeforeEach
    void setUp() {
        faultsService = new FaultsService(bladeApi, repo, scheduler, normalizer);
        
        // 设置默认配置值
        ReflectionTestUtils.setField(faultsService, "eventsLimit", 50);
        ReflectionTestUtils.setField(faultsService, "defaultTtlSeconds", 0);
        ReflectionTestUtils.setField(faultsService, "namePrefix", "blade-");
    }
    
    @Test
    void testExecuteSuccess() {
        // 准备测试数据
        Map<String, Object> faultJson = Map.of(
                "spec", Map.of(
                        "experiments", List.of(
                                Map.of(
                                        "scope", "container",
                                        "target", "network",
                                        "action", "delay"
                                )
                        )
                )
        );
        
        String name = "test-fault";
        Integer durationSec = 60;
        
        // Mock 行为
        when(bladeApi.exists(name)).thenReturn(false);
        
        Map<String, Object> normalized = Map.of(
                "apiVersion", "chaosblade.io/v1alpha1",
                "kind", "ChaosBlade",
                "metadata", Map.of("name", name),
                "spec", faultJson.get("spec")
        );
        when(normalizer.normalize(eq(faultJson), eq(name), any())).thenReturn(normalized);
        when(normalizer.validateSpec(any())).thenReturn(true);
        try {
            when(normalizer.toYaml(normalized)).thenReturn("yaml content");
        } catch (Exception e) {
            // This won't happen in test, but needed for compilation
        }
        
        GenericKubernetesResource mockResource = new GenericKubernetesResourceBuilder()
                .withApiVersion("chaosblade.io/v1alpha1")
                .withKind("ChaosBlade")
                .build();
        when(bladeApi.create(eq(name), any(), any())).thenReturn(mockResource);
        
        // 执行测试
        Map<String, String> result = faultsService.execute(faultJson, name, durationSec);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.containsKey("faultId"));
        assertEquals(name, result.get("bladeName"));
        
        // 验证调用
        verify(bladeApi).exists(name);
        verify(normalizer).normalize(eq(faultJson), eq(name), any());
        verify(normalizer).validateSpec(any());
        verify(bladeApi).create(eq(name), any(), any());
        verify(repo).save(eq(name), any(), eq(60L));
        verify(scheduler).schedule(any(Runnable.class), any(java.util.Date.class));
    }
    
    @Test
    void testExecuteWithGeneratedName() {
        // 准备测试数据
        Map<String, Object> faultJson = Map.of(
                "spec", Map.of(
                        "experiments", List.of(
                                Map.of(
                                        "scope", "container",
                                        "target", "network",
                                        "action", "delay"
                                )
                        )
                )
        );
        
        // Mock 行为
        when(bladeApi.exists(anyString())).thenReturn(false);
        
        Map<String, Object> normalized = Map.of(
                "apiVersion", "chaosblade.io/v1alpha1",
                "kind", "ChaosBlade",
                "metadata", Map.of("name", "generated-name"),
                "spec", faultJson.get("spec")
        );
        when(normalizer.normalize(eq(faultJson), anyString(), any())).thenReturn(normalized);
        when(normalizer.validateSpec(any())).thenReturn(true);
        try {
            when(normalizer.toYaml(normalized)).thenReturn("yaml content");
        } catch (Exception e) {
            // This won't happen in test, but needed for compilation
        }
        
        GenericKubernetesResource mockResource = new GenericKubernetesResourceBuilder()
                .withApiVersion("chaosblade.io/v1alpha1")
                .withKind("ChaosBlade")
                .build();
        when(bladeApi.create(anyString(), any(), any())).thenReturn(mockResource);
        
        // 执行测试（不传 name）
        Map<String, String> result = faultsService.execute(faultJson, null, null);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.containsKey("faultId"));
        assertTrue(result.get("bladeName").startsWith("blade-"));
        
        // 验证调用
        verify(bladeApi).create(anyString(), any(), any());
        verify(repo).save(anyString(), any(), eq(0L));
        verify(scheduler, never()).schedule(any(Runnable.class), any(java.util.Date.class)); // TTL = 0，不应该调度
    }
    
    @Test
    void testExecuteAlreadyExists() {
        // 准备测试数据
        Map<String, Object> faultJson = Map.of("spec", Map.of("experiments", List.of()));
        String name = "existing-fault";
        
        // Mock 行为
        when(bladeApi.exists(name)).thenReturn(true);
        
        // 执行测试并验证异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            faultsService.execute(faultJson, name, null);
        });
        
        assertTrue(exception.getMessage().contains("already exists"));
        
        // 验证调用
        verify(bladeApi).exists(name);
        verify(normalizer, never()).normalize(any(), any(), any());
        verify(bladeApi, never()).create(any(), any(), any());
    }
    
    @Test
    void testExecuteInvalidSpec() {
        // 准备测试数据
        Map<String, Object> faultJson = Map.of("spec", Map.of("invalid", "spec"));
        String name = "test-fault";
        
        // Mock 行为
        when(bladeApi.exists(name)).thenReturn(false);
        
        Map<String, Object> normalized = Map.of(
                "apiVersion", "chaosblade.io/v1alpha1",
                "kind", "ChaosBlade",
                "metadata", Map.of("name", name),
                "spec", faultJson.get("spec")
        );
        when(normalizer.normalize(eq(faultJson), eq(name), any())).thenReturn(normalized);
        when(normalizer.validateSpec(any())).thenReturn(false); // 验证失败
        
        // 执行测试并验证异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            faultsService.execute(faultJson, name, null);
        });
        
        assertTrue(exception.getMessage().contains("Invalid fault specification"));
        
        // 验证调用
        verify(normalizer).validateSpec(any());
        verify(bladeApi, never()).create(any(), any(), any());
    }
    
    @Test
    void testStatusAndEventsSuccess() {
        // 准备测试数据
        String bladeName = "test-fault";
        
        GenericKubernetesResource mockBlade = new GenericKubernetesResourceBuilder()
                .withApiVersion("chaosblade.io/v1alpha1")
                .withKind("ChaosBlade")
                .build();
        mockBlade.setAdditionalProperty("status", Map.of("phase", "Running"));
        
        Map<String, Object> status = Map.of("phase", "Running");
        List<Map<String, Object>> events = List.of(
                Map.of("type", "Normal", "reason", "Created", "message", "Fault created")
        );
        
        // Mock 行为
        when(bladeApi.get(bladeName)).thenReturn(mockBlade);
        when(bladeApi.status(mockBlade)).thenReturn(status);
        when(bladeApi.eventsForBlade(bladeName, 50)).thenReturn(events);
        
        // 执行测试
        Map<String, Object> result = faultsService.statusAndEvents(bladeName);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(bladeName, result.get("bladeName"));
        assertEquals("Running", result.get("phase"));
        assertEquals(status, result.get("status"));
        assertEquals(events, result.get("events"));
        assertEquals(1, result.get("eventsCount"));
        
        // 验证调用
        verify(bladeApi).get(bladeName);
        verify(bladeApi).status(mockBlade);
        verify(bladeApi).eventsForBlade(bladeName, 50);
        verify(repo).updateStatus(bladeName, "Running");
    }
    
    @Test
    void testStatusAndEventsNotFound() {
        // 准备测试数据
        String bladeName = "non-existent-fault";
        
        // Mock 行为
        when(bladeApi.get(bladeName)).thenReturn(null);
        
        // 执行测试并验证异常
        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> {
            faultsService.statusAndEvents(bladeName);
        });
        
        assertTrue(exception.getMessage().contains("not found"));
        
        // 验证调用
        verify(bladeApi).get(bladeName);
        verify(bladeApi, never()).status(any());
        verify(bladeApi, never()).eventsForBlade(any(), anyInt());
    }
    
    @Test
    void testStopSuccess() {
        // 准备测试数据
        String bladeName = "test-fault";
        
        // Mock 行为
        when(bladeApi.delete(bladeName)).thenReturn(true);
        
        // 执行测试
        assertDoesNotThrow(() -> {
            faultsService.stop(bladeName);
        });
        
        // 验证调用
        verify(bladeApi).delete(bladeName);
        verify(repo).delete(bladeName);
    }
    
    @Test
    void testStopNotFound() {
        // 准备测试数据
        String bladeName = "non-existent-fault";
        
        // Mock 行为
        when(bladeApi.delete(bladeName)).thenReturn(false);
        
        // 执行测试并验证异常
        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> {
            faultsService.stop(bladeName);
        });
        
        assertTrue(exception.getMessage().contains("not found"));
        
        // 验证调用
        verify(bladeApi).delete(bladeName);
        verify(repo, never()).delete(bladeName);
    }
    
    @Test
    void testListAllFaults() {
        // 准备测试数据
        java.util.Set<String> faultNames = java.util.Set.of("fault1", "fault2", "fault3");
        
        // Mock 行为
        when(repo.getAllFaultNames()).thenReturn(faultNames);
        
        // 执行测试
        java.util.Set<String> result = faultsService.listAllFaults();
        
        // 验证结果
        assertEquals(faultNames, result);
        
        // 验证调用
        verify(repo).getAllFaultNames();
    }
    
    @Test
    void testExists() {
        // 准备测试数据
        String bladeName = "test-fault";
        
        // Mock 行为
        when(bladeApi.exists(bladeName)).thenReturn(true);
        
        // 执行测试
        boolean result = faultsService.exists(bladeName);
        
        // 验证结果
        assertTrue(result);
        
        // 验证调用
        verify(bladeApi).exists(bladeName);
    }
}
