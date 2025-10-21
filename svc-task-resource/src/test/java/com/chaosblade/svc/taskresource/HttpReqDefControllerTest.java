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

package com.chaosblade.svc.taskresource;

import com.chaosblade.svc.taskresource.entity.HttpReqDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class HttpReqDefControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpReqDef sample;

    @BeforeEach
    void setUp() {
        sample = new HttpReqDef();
        sample.setCode("def-001");
        sample.setName("示例定义");
        sample.setMethod(HttpReqDef.HttpMethod.POST);
        sample.setUrlTemplate("/api/v1/demo/{id}");
        sample.setBodyMode(HttpReqDef.BodyMode.JSON);
        sample.setBodyTemplate("{\"name\":\"{{n}}\"}");
    }

    @Test
    void testCreateAndGet() throws Exception {
        // create
        mockMvc.perform(post("/api/http-req-defs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sample)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists());

        // get page
        mockMvc.perform(get("/api/http-req-defs").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void testValidation() throws Exception {
        HttpReqDef bad = new HttpReqDef();
        bad.setCode("");
        bad.setName("bad");
        bad.setMethod(HttpReqDef.HttpMethod.POST);
        bad.setUrlTemplate("not-start-with-slash");
        bad.setBodyMode(HttpReqDef.BodyMode.RAW);
        bad.setRawBody("");

        mockMvc.perform(post("/api/http-req-defs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }
}

