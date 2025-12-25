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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
        Map<String, Object> faultSpec = new HashMap<>();
        Map<String, Object> spec = new HashMap<>();
        Map<String, Object> experiment = new HashMap<>();
        experiment.put("scope", "container");
        experiment.put("target", "network");
        experiment.put("action", "delay");
        experiment.put("desc", "container network delay test");
        List<Map<String, Object>> matchers = Arrays.asList(
                createMatcher("names", "test-pod"),
                createMatcher("namespace", "default"),
                createMatcher("container-names", "test-container"),
                createMatcher("interface", "eth0"),
                createMatcher("time", "100"),
                createMatcher("offset", "10")
        );
        experiment.put("matchers", matchers);
        spec.put("experiments", Collections.singletonList(experiment));
        faultSpec.put("spec", spec);
        
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
        Map<String, Object> invalidSpec = new HashMap<>();
        Map<String, Object> invalidSpecContent = new HashMap<>();
        invalidSpecContent.put("invalid", "spec");
        invalidSpec.put("spec", invalidSpecContent);
        
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
        Map<String, Object> specOnly = new HashMap<>();
        Map<String, Object> experiment2 = new HashMap<>();
        experiment2.put("scope", "pod");
        experiment2.put("target", "cpu");
        experiment2.put("action", "fullload");
        experiment2.put("desc", "pod cpu fullload test");
        List<Map<String, Object>> matchers2 = Arrays.asList(
                createMatcher("names", "test-pod"),
                createMatcher("namespace", "default"),
                createMatcher("cpu-percent", "80")
        );
        experiment2.put("matchers", matchers2);
        specOnly.put("experiments", Collections.singletonList(experiment2));
        
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
        Map<String, Object> fullCR = new HashMap<>();
        fullCR.put("apiVersion", "chaosblade.io/v1alpha1");
        fullCR.put("kind", "ChaosBlade");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "will-be-overridden");
        Map<String, Object> labels = new HashMap<>();
        labels.put("test", "integration");
        metadata.put("labels", labels);
        fullCR.put("metadata", metadata);

        Map<String, Object> spec3 = new HashMap<>();
        Map<String, Object> experiment3 = new HashMap<>();
        experiment3.put("scope", "container");
        experiment3.put("target", "process");
        experiment3.put("action", "kill");
        experiment3.put("desc", "kill container process test");
        List<Map<String, Object>> matchers3 = Arrays.asList(
                createMatcher("names", "test-pod"),
                createMatcher("namespace", "default"),
                createMatcher("container-names", "test-container"),
                createMatcher("process", "java")
        );
        experiment3.put("matchers", matchers3);
        spec3.put("experiments", Collections.singletonList(experiment3));
        fullCR.put("spec", spec3);
        
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

    // 辅助方法：创建 matcher Map
    private Map<String, Object> createMatcher(String name, String value) {
        Map<String, Object> matcher = new HashMap<>();
        matcher.put("name", name);
        matcher.put("value", Collections.singletonList(value));
        return matcher;
    }
}
