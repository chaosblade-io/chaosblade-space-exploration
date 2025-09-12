package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.NamespaceListResponse;
import com.chaosblade.svc.topo.model.NamespaceDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ApiQueryController 集成测试类
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ApiQueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetNamespaces() throws Exception {
        // 测试 GET /v1/topology/namespaces 接口
        mockMvc.perform(get("/v1/topology/namespaces")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    public void testGetNamespacesResponseStructure() throws Exception {
        // 测试 GET /v1/topology/namespaces 接口响应结构
        String response = mockMvc.perform(get("/v1/topology/namespaces")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应
        NamespaceListResponse namespaceListResponse = objectMapper.readValue(response, NamespaceListResponse.class);
        
        // 验证响应结构
        assertTrue(namespaceListResponse.getSuccess());
        assertNotNull(namespaceListResponse.getData());
        assertNotNull(namespaceListResponse.getData().getItems());
        assertNotNull(namespaceListResponse.getData().getTotal());
        
        // 如果有命名空间数据，验证字段结构
        if (namespaceListResponse.getData().getTotal() > 0) {
            NamespaceListResponse.NamespaceListData data = namespaceListResponse.getData();
            assertFalse(data.getItems().isEmpty());
            
            // 验证第一个命名空间的字段
            NamespaceDetail firstNamespace = data.getItems().get(0);
            assertNotNull(firstNamespace.getId());
            assertNotNull(firstNamespace.getSystemKey());
            assertNotNull(firstNamespace.getK8sNamespace());
            assertNotNull(firstNamespace.getName());
            assertNotNull(firstNamespace.getDescription());
            assertNotNull(firstNamespace.getOwner());
            assertNotNull(firstNamespace.getDefaultEnvironment());
        }
    }
}