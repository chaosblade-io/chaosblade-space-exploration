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
        var response = objectMapper.readValue(responseContent, java.util.Map.class);
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
        return """
            {
              "data": [
                {
                  "traceID": "2b0b05fdc85d932b5c86887945fb5593",
                  "spans": [
                    {
                      "traceID": "2b0b05fdc85d932b5c86887945fb5593",
                      "spanID": "5ed3557f1c414ffc",
                      "operationName": "oteldemo.ShippingService/ShipOrder",
                      "references": [],
                      "startTime": 1747797378423935,
                      "duration": 38,
                      "tags": [
                        {
                          "key": "service.name",
                          "type": "string",
                          "value": "shipping"
                        },
                        {
                          "key": "rpc.service",
                          "type": "string",
                          "value": "oteldemo.ShippingService"
                        },
                        {
                          "key": "rpc.method",
                          "type": "string",
                          "value": "ShipOrder"
                        }
                      ],
                      "logs": [],
                      "processID": "p1"
                    },
                    {
                      "traceID": "2b0b05fdc85d932b5c86887945fb5593",
                      "spanID": "4b103ecc62341fcc",
                      "operationName": "Currency/Convert",
                      "references": [],
                      "startTime": 1747797378413556,
                      "duration": 972,
                      "tags": [
                        {
                          "key": "service.name",
                          "type": "string",
                          "value": "currency"
                        }
                      ],
                      "logs": [],
                      "processID": "p2"
                    }
                  ],
                  "processes": {
                    "p1": {
                      "serviceName": "shipping",
                      "tags": [
                        {
                          "key": "hostname",
                          "type": "string",
                          "value": "shipping-pod"
                        },
                        {
                          "key": "k8s.pod.name",
                          "type": "string",
                          "value": "shipping-68654fd7fb-jnxff"
                        },
                        {
                          "key": "k8s.namespace.name",
                          "type": "string",
                          "value": "default"
                        }
                      ]
                    },
                    "p2": {
                      "serviceName": "currency",
                      "tags": [
                        {
                          "key": "hostname",
                          "type": "string",
                          "value": "currency-pod"
                        },
                        {
                          "key": "k8s.pod.name",
                          "type": "string",
                          "value": "currency-74b7b67479-r7nr9"
                        },
                        {
                          "key": "k8s.namespace.name",
                          "type": "string",
                          "value": "default"
                        }
                      ]
                    }
                  }
                }
              ]
            }
            """;
    }
}
