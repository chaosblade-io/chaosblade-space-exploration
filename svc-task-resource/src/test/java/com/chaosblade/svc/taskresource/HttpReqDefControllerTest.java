package com.chaosblade.svc.taskresource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chaosblade.svc.taskresource.entity.HttpReqDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class HttpReqDefControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

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
    mockMvc
        .perform(
            post("/api/http-req-defs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sample)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").exists());

    // get page
    mockMvc
        .perform(get("/api/http-req-defs").param("page", "1").param("size", "10"))
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

    mockMvc
        .perform(
            post("/api/http-req-defs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
        .andExpect(status().isBadRequest());
  }
}
