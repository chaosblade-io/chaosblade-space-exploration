package com.chaosblade.svc.faultscheduler.integration;

import com.chaosblade.svc.faultscheduler.FaultSchedulerApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 故障调度器集成测试
 * 测试完整的 API 流程和组件集成
 */
@SpringBootTest(classes = FaultSchedulerApplication.class)
@AutoConfigureWebMvc
@ActiveProfiles("test")
class FaultSchedulerIntegrationTest {
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();
    
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }
    
    @Test
    void testHealthEndpoint() throws Exception {
        setUp();
        
        mockMvc.perform(get("/api/faults/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("svc-fault-scheduler"));
    }
    
    @Test
    void testListFaultsEndpoint() throws Exception {
        setUp();
        
        mockMvc.perform(get("/api/faults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
    
    @Test
    void testExecuteFaultWithValidSpec() throws Exception {
        setUp();
        
        // 准备测试数据 - 网络延迟故障
        Map<String, Object> faultSpec = Map.of(
                "spec", Map.of(
                        "experiments", List.of(
                                Map.of(
                                        "scope", "container",
                                        "target", "network",
                                        "action", "delay",
                                        "desc", "container network delay test",
                                        "matchers", List.of(
                                                Map.of("name", "names", "value", List.of("test-pod")),
                                                Map.of("name", "namespace", "value", List.of("default")),
                                                Map.of("name", "container-names", "value", List.of("test-container")),
                                                Map.of("name", "interface", "value", List.of("eth0")),
                                                Map.of("name", "time", "value", List.of("100")),
                                                Map.of("name", "offset", "value", List.of("10"))
                                        )
                                )
                        )
                )
        );
        
        String requestBody = objectMapper.writeValueAsString(faultSpec);
        
        mockMvc.perform(post("/api/faults:execute")
                        .param("name", "integration-test-fault")
                        .param("durationSec", "30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.faultId").exists())
                .andExpect(jsonPath("$.data.bladeName").value("integration-test-fault"));
    }
    
    @Test
    void testExecuteFaultWithInvalidSpec() throws Exception {
        setUp();
        
        // 准备无效的测试数据
        Map<String, Object> invalidSpec = Map.of(
                "spec", Map.of(
                        "invalid", "spec"
                )
        );
        
        String requestBody = objectMapper.writeValueAsString(invalidSpec);
        
        mockMvc.perform(post("/api/faults:execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    void testExecuteFaultWithEmptyBody() throws Exception {
        setUp();
        
        mockMvc.perform(post("/api/faults:execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
    
    @Test
    void testGetFaultStatusNotFound() throws Exception {
        setUp();
        
        mockMvc.perform(get("/api/faults/non-existent-fault/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Fault not found: non-existent-fault"));
    }
    
    @Test
    void testStopFaultNotFound() throws Exception {
        setUp();
        
        mockMvc.perform(delete("/api/faults/non-existent-fault"))
                .andExpect(status().isNotFound());
    }
    
    @Test
    void testCheckFaultExists() throws Exception {
        setUp();
        
        mockMvc.perform(get("/api/faults/test-fault/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isBoolean());
    }
    
    @Test
    void testExecuteFaultWithSpecOnly() throws Exception {
        setUp();
        
        // 测试只传 spec 的情况
        Map<String, Object> specOnly = Map.of(
                "experiments", List.of(
                        Map.of(
                                "scope", "pod",
                                "target", "cpu",
                                "action", "fullload",
                                "desc", "pod cpu fullload test",
                                "matchers", List.of(
                                        Map.of("name", "names", "value", List.of("test-pod")),
                                        Map.of("name", "namespace", "value", List.of("default")),
                                        Map.of("name", "cpu-percent", "value", List.of("80"))
                                )
                        )
                )
        );
        
        String requestBody = objectMapper.writeValueAsString(specOnly);
        
        mockMvc.perform(post("/api/faults:execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.faultId").exists())
                .andExpect(jsonPath("$.data.bladeName").exists());
    }
    
    @Test
    void testExecuteFaultWithFullCR() throws Exception {
        setUp();
        
        // 测试传完整 CR 的情况
        Map<String, Object> fullCR = Map.of(
                "apiVersion", "chaosblade.io/v1alpha1",
                "kind", "ChaosBlade",
                "metadata", Map.of(
                        "name", "will-be-overridden",
                        "labels", Map.of("test", "integration")
                ),
                "spec", Map.of(
                        "experiments", List.of(
                                Map.of(
                                        "scope", "container",
                                        "target", "process",
                                        "action", "kill",
                                        "desc", "kill container process test",
                                        "matchers", List.of(
                                                Map.of("name", "names", "value", List.of("test-pod")),
                                                Map.of("name", "namespace", "value", List.of("default")),
                                                Map.of("name", "container-names", "value", List.of("test-container")),
                                                Map.of("name", "process", "value", List.of("java"))
                                        )
                                )
                        )
                )
        );
        
        String requestBody = objectMapper.writeValueAsString(fullCR);
        
        mockMvc.perform(post("/api/faults:execute")
                        .param("name", "full-cr-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.faultId").exists())
                .andExpect(jsonPath("$.data.bladeName").value("full-cr-test"));
    }
    
    @Test
    void testInvalidHttpMethods() throws Exception {
        setUp();
        
        // 测试不支持的 HTTP 方法
        mockMvc.perform(put("/api/faults:execute"))
                .andExpect(status().isMethodNotAllowed());
        
        mockMvc.perform(patch("/api/faults/test-fault/status"))
                .andExpect(status().isMethodNotAllowed());
        
        mockMvc.perform(post("/api/faults/test-fault"))
                .andExpect(status().isMethodNotAllowed());
    }
    
    @Test
    void testInvalidPathParameters() throws Exception {
        setUp();
        
        // 测试空的路径参数
        mockMvc.perform(get("/api/faults//status"))
                .andExpect(status().isNotFound());
        
        mockMvc.perform(delete("/api/faults/"))
                .andExpect(status().isNotFound());
    }
}
