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

package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.trace.TraceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceParserService 单元测试
 */
class TraceParserServiceTest {

    private TraceParserService traceParserService;

    @BeforeEach
    void setUp() {
        traceParserService = new TraceParserService();
    }

    @Test
    void testParseValidTraceFile() throws IOException {
        // 准备测试数据
        String traceJson = """
            {
              "data": [
                {
                  "traceID": "2b0b05fdc85d932b5c86887945fb5593",
                  "spans": [
                    {
                      "traceID": "2b0b05fdc85d932b5c86887945fb5593",
                      "spanID": "5ed3557f1c414ffc",
                      "operationName": "oteldemo.ShippingService/ShipOrder",
                      "startTime": 1747797378423935,
                      "duration": 38,
                      "tags": [
                        {
                          "key": "service.name",
                          "type": "string",
                          "value": "shipping"
                        }
                      ],
                      "processID": "p1"
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
                        }
                      ]
                    }
                  }
                }
              ]
            }
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-trace.json",
            "application/json",
            traceJson.getBytes()
        );

        // 执行测试
        TraceData result = traceParserService.parseTraceFile(file);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(1, result.getData().size());

        TraceData.TraceRecord record = result.getData().get(0);
        assertEquals("2b0b05fdc85d932b5c86887945fb5593", record.getTraceId());
        assertEquals(1, record.getSpans().size());
        assertEquals(1, record.getProcesses().size());
    }

    @Test
    void testParseEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file",
            "empty.json",
            "application/json",
            new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () -> {
            traceParserService.parseTraceFile(emptyFile);
        });
    }

    @Test
    void testParseInvalidJson() {
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file",
            "invalid.json",
            "application/json",
            "invalid json content".getBytes()
        );

        assertThrows(IOException.class, () -> {
            traceParserService.parseTraceFile(invalidFile);
        });
    }

    @Test
    void testExtractServiceNames() throws IOException {
        String traceJson = """
            {
              "data": [
                {
                  "traceID": "test-trace",
                  "spans": [],
                  "processes": {
                    "p1": {"serviceName": "shipping"},
                    "p2": {"serviceName": "checkout"},
                    "p3": {"serviceName": "currency"}
                  }
                }
              ]
            }
            """;

        TraceData traceData = traceParserService.parseTraceContent(traceJson);
        Set<String> serviceNames = traceParserService.extractServiceNames(traceData);

        assertEquals(3, serviceNames.size());
        assertTrue(serviceNames.contains("shipping"));
        assertTrue(serviceNames.contains("checkout"));
        assertTrue(serviceNames.contains("currency"));
    }

    @Test
    void testValidateTraceFormat() {
        // 有效格式
        MockMultipartFile validFile = new MockMultipartFile(
            "file",
            "valid.json",
            "application/json",
            "{\"data\":[{\"spans\":[],\"traceID\":\"test\"}]}".getBytes()
        );

        assertTrue(traceParserService.validateTraceFormat(validFile));

        // 无效格式
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file",
            "invalid.json",
            "application/json",
            "{\"invalid\": \"format\"}".getBytes()
        );

        assertFalse(traceParserService.validateTraceFormat(invalidFile));
    }
}
