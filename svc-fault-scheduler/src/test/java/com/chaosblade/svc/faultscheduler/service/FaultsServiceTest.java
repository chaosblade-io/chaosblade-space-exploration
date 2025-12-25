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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
        Map<String, Object> experiment = createExperiment("container", "network", "delay");
        Map<String, Object> spec = createSpec(Collections.singletonList(experiment));
        Map<String, Object> faultJson = createFaultJson(spec);

        String name = "test-fault";
        Integer durationSec = 60;

        // Mock 行为
        when(bladeApi.exists(name)).thenReturn(false);

        Map<String, Object> normalized = createNormalized(name, faultJson.get("spec"));
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
        Map<String, Object> experiment = createExperiment("container", "network", "delay");
        Map<String, Object> spec = createSpec(Collections.singletonList(experiment));
        Map<String, Object> faultJson = createFaultJson(spec);

        // Mock 行为
        when(bladeApi.exists(anyString())).thenReturn(false);

        Map<String, Object> normalized = createNormalized("generated-name", faultJson.get("spec"));
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
        Map<String, Object> spec = createSpec(Collections.emptyList());
        Map<String, Object> faultJson = createFaultJson(spec);
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
        Map<String, Object> invalidSpec = new HashMap<>();
        invalidSpec.put("invalid", "spec");
        Map<String, Object> faultJson = new HashMap<>();
        faultJson.put("spec", invalidSpec);
        String name = "test-fault";

        // Mock 行为
        when(bladeApi.exists(name)).thenReturn(false);

        Map<String, Object> normalized = createNormalized(name, faultJson.get("spec"));
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
        mockBlade.setAdditionalProperty("status", createStatus("Running"));

        Map<String, Object> status = createStatus("Running");
        List<Map<String, Object>> events = Collections.singletonList(
                createEvent("Normal", "Created", "Fault created")
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
        java.util.Set<String> faultNames = new java.util.HashSet<>(Arrays.asList("fault1", "fault2", "fault3"));
        
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

    // 辅助方法：创建实验配置 Map
    private Map<String, Object> createExperiment(String scope, String target, String action) {
        Map<String, Object> experiment = new HashMap<>();
        experiment.put("scope", scope);
        experiment.put("target", target);
        experiment.put("action", action);
        return experiment;
    }

    // 辅助方法：创建 spec Map
    private Map<String, Object> createSpec(List<Map<String, Object>> experiments) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("experiments", experiments);
        return spec;
    }

    // 辅助方法：创建 faultJson Map
    private Map<String, Object> createFaultJson(Map<String, Object> spec) {
        Map<String, Object> faultJson = new HashMap<>();
        faultJson.put("spec", spec);
        return faultJson;
    }

    // 辅助方法：创建标准化后的 CR
    private Map<String, Object> createNormalized(String name, Object spec) {
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("apiVersion", "chaosblade.io/v1alpha1");
        normalized.put("kind", "ChaosBlade");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        normalized.put("metadata", metadata);
        normalized.put("spec", spec);
        return normalized;
    }

    // 辅助方法：创建状态 Map
    private Map<String, Object> createStatus(String phase) {
        Map<String, Object> status = new HashMap<>();
        status.put("phase", phase);
        return status;
    }

    // 辅助方法：创建事件 Map
    private Map<String, Object> createEvent(String type, String reason, String message) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("reason", reason);
        event.put("message", message);
        return event;
    }
}
