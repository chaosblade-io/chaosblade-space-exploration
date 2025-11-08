package com.chaosblade.svc.topo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 应用集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
class TopoVisualizerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCompleteWorkflow() throws Exception {
        // 1. 测试健康检查
        mockMvc.perform(get("/api/trace/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // 2. 测试文件格式查询
        mockMvc.perform(get("/api/trace/formats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.supportedFormats").isArray());

        // 3. 测试文件上传和处理
        String traceJson = createSampleTraceJson();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-trace.json",
            "application/json",
            traceJson.getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/trace/upload")
                .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.topology").exists())
                .andExpect(jsonPath("$.mermaidDiagram").exists())
                .andReturn();

        // 解析响应获取拓扑数据
        String responseContent = uploadResult.getResponse().getContentAsString();
        java.util.Map<String, Object> response = objectMapper.readValue(responseContent, java.util.Map.class);
        Object topologyObj = response.get("topology");

        // 4. 测试Mermaid图生成
        mockMvc.perform(post("/api/visualization/mermaid/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(topologyObj)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.diagram").exists());

        // 5. 测试统计信息
        mockMvc.perform(post("/api/visualization/statistics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(topologyObj)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.nodeCount").exists())
                .andExpect(jsonPath("$.edgeCount").exists());
    }

    @Test
    void testFileValidation() throws Exception {
        // 测试空文件
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file",
            "empty.json",
            "application/json",
            new byte[0]
        );

        mockMvc.perform(multipart("/api/trace/upload")
                .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("EMPTY_FILE"));

        // 测试无效JSON
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file",
            "invalid.json",
            "application/json",
            "invalid json".getBytes()
        );

        mockMvc.perform(multipart("/api/trace/upload")
                .file(invalidFile))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testErrorHandling() throws Exception {
        // 测试文件格式验证
        MockMultipartFile txtFile = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "not a json file".getBytes()
        );

        mockMvc.perform(multipart("/api/trace/upload")
                .file(txtFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }


    @Test
    void testJsonBasedGeneration() throws Exception {
        String traceJson = createSampleTraceJson();

        mockMvc.perform(post("/api/trace/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(traceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.topology").exists())
                .andExpect(jsonPath("$.mermaidDiagram").exists());
    }

    private String createSampleTraceJson() {
        return "{\n" +
            "  \"data\": [\n" +
            "    {\n" +
            "      \"traceID\": \"2b0b05fdc85d932b5c86887945fb5593\",\n" +
            "      \"spans\": [\n" +
            "        {\n" +
            "          \"traceID\": \"2b0b05fdc85d932b5c86887945fb5593\",\n" +
            "          \"spanID\": \"5ed3557f1c414ffc\",\n" +
            "          \"operationName\": \"oteldemo.ShippingService/ShipOrder\",\n" +
            "          \"references\": [],\n" +
            "          \"startTime\": 1747797378423935,\n" +
            "          \"duration\": 38,\n" +
            "          \"tags\": [\n" +
            "            {\n" +
            "              \"key\": \"service.name\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"shipping\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"key\": \"rpc.service\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"oteldemo.ShippingService\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"key\": \"rpc.method\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"ShipOrder\"\n" +
            "            }\n" +
            "          ],\n" +
            "          \"logs\": [],\n" +
            "          \"processID\": \"p1\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"traceID\": \"2b0b05fdc85d932b5c86887945fb5593\",\n" +
            "          \"spanID\": \"4b103ecc62341fcc\",\n" +
            "          \"operationName\": \"Currency/Convert\",\n" +
            "          \"references\": [],\n" +
            "          \"startTime\": 1747797378413556,\n" +
            "          \"duration\": 972,\n" +
            "          \"tags\": [\n" +
            "            {\n" +
            "              \"key\": \"service.name\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"currency\"\n" +
            "            }\n" +
            "          ],\n" +
            "          \"logs\": [],\n" +
            "          \"processID\": \"p2\"\n" +
            "        }\n" +
            "      ],\n" +
            "      \"processes\": {\n" +
            "        \"p1\": {\n" +
            "          \"serviceName\": \"shipping\",\n" +
            "          \"tags\": [\n" +
            "            {\n" +
            "              \"key\": \"hostname\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"shipping-pod\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"key\": \"k8s.pod.name\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"shipping-68654fd7fb-jnxff\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"key\": \"k8s.namespace.name\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"default\"\n" +
            "            }\n" +
            "          ]\n" +
            "        },\n" +
            "        \"p2\": {\n" +
            "          \"serviceName\": \"currency\",\n" +
            "          \"tags\": [\n" +
            "            {\n" +
            "              \"key\": \"hostname\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"currency-pod\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"key\": \"k8s.pod.name\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"currency-74b7b67479-r7nr9\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"key\": \"k8s.namespace.name\",\n" +
            "              \"type\": \"string\",\n" +
            "              \"value\": \"default\"\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }
}
