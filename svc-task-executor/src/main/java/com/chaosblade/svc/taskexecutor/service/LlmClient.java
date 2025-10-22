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

package com.chaosblade.svc.taskexecutor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    @Value("${llm.api.url:https://api.siliconflow.cn/v1/chat/completions}")
    private String apiUrl;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.model:Qwen/QwQ-32B}")
    private String model;

    @Value("${llm.timeout.ms:20000}")
    private int timeoutMs;

    @Value("${llm.retries:2}")
    private int retries;

    private RestTemplate restTemplate = new RestTemplate();

    public String chat(String userContent) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("LLM apiKey not configured, skip LLM call");
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        List<Map<String,Object>> messages = new ArrayList<>();
        messages.add(Map.of("role","user","content", userContent));
        payload.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer "+ apiKey);
        HttpEntity<Map<String,Object>> entity = new HttpEntity<>(payload, headers);

        int attempts = Math.max(1, retries + 1);
        for (int i=1;i<=attempts;i++) {
            try {
                ResponseEntity<Map> resp = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody()!=null) {
                    Object choices = resp.getBody().get("choices");
                    if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                        Object first = ((List<?>) choices).get(0);
                        if (first instanceof Map) {
                            Object message = ((Map<?,?>) first).get("message");
                            if (message instanceof Map) {
                                Object content = ((Map<?,?>) message).get("content");
                                return (content==null? null : String.valueOf(content));
                            }
                        }
                    }
                } else {
                    log.warn("LLM non-2xx: status={}, body={}", resp.getStatusCode(), resp.getBody());
                }
            } catch (Exception ex) {
                log.warn("LLM call failed (attempt {}/{}): {}", i, attempts, ex.getMessage());
                try { Thread.sleep(500L * i); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        return null;
    }
}

