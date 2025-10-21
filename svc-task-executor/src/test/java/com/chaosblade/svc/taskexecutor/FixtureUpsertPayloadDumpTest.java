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

package com.chaosblade.svc.taskexecutor;

import com.chaosblade.svc.taskexecutor.service.ExecutionOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 快速导出 /api/fixtures/upsert 的请求体，用于离线检查生成内容。
 * 注意：仅做本地验证，不会调用远端接口，也不会改动主流程。
 */
@SpringBootTest(classes = TaskExecutorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@org.springframework.test.context.ActiveProfiles("default")
public class FixtureUpsertPayloadDumpTest {

    @Autowired
    private ExecutionOrchestrator orchestrator;

    private final ObjectWriter pretty = new ObjectMapper().writerWithDefaultPrettyPrinter();

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> buildItemsViaReflection(Long executionId) throws Exception {
        Method m = ExecutionOrchestrator.class.getDeclaredMethod("buildInterceptorItems", Long.class);
        m.setAccessible(true);
        return (List<Map<String, Object>>) m.invoke(orchestrator, executionId);
    }

    @Test
    public void dumpExec8() throws Exception {
        Long executionId = 8L;
        String namespace = "train-ticket"; // 如需更改，改这里
        int ttlSec = 600;

        List<Map<String,Object>> items = buildItemsViaReflection(executionId);

        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("namespace", namespace);
        payload.put("recordId", String.valueOf(executionId));
        payload.put("ttlSec", ttlSec);
        payload.put("items", items);

        String out = pretty.writeValueAsString(payload);
        Path outPath = Path.of("fixtures-upsert-exec-" + executionId + ".json");
        Files.writeString(outPath, out, StandardCharsets.UTF_8);

        System.out.println("[TestDump] Wrote payload to: " + outPath.toAbsolutePath());
        System.out.println("[TestDump] Items count: " + (items == null ? 0 : items.size()));
    }
}

