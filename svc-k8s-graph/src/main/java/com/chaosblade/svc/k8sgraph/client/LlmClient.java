package com.chaosblade.svc.k8sgraph.client;

import com.chaosblade.svc.k8sgraph.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * LLM 客户端 - 调用 AI 大模型服务
 * 支持 OpenAI 兼容格式和 Anthropic Claude 格式
 */
@Component
public class LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(LlmClient.class);
    private static final String PROVIDER_ANTHROPIC = "anthropic";
    private static final String PROVIDER_OPENAI = "openai";

    @Autowired
    private LlmProperties llmProperties;

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(llmProperties.getTimeout().getMs());
        factory.setReadTimeout(llmProperties.getTimeout().getMs());
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 发送聊天请求
     * @param userContent 用户消息内容
     * @return AI 响应内容
     */
    public String chat(String userContent) {
        return chat(userContent, null);
    }

    /**
     * 发送聊天请求（带系统提示）
     * @param userContent 用户消息内容
     * @param systemPrompt 系统提示（可选）
     * @return AI 响应内容
     */
    public String chat(String userContent, String systemPrompt) {
        String apiKey = llmProperties.getApi().getKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("LLM API key not configured, skip LLM call");
            return null;
        }

        String provider = llmProperties.getApi().getProvider();
        if (PROVIDER_ANTHROPIC.equalsIgnoreCase(provider)) {
            return chatAnthropic(userContent, systemPrompt);
        } else {
            return chatOpenAI(userContent, systemPrompt);
        }
    }

    /**
     * Anthropic Claude API 调用
     */
    private String chatAnthropic(String userContent, String systemPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", llmProperties.getApi().getKey());
        headers.set("anthropic-version", "2023-06-01");

        List<Map<String, Object>> messages = new ArrayList<>();

        // 添加用户消息
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmProperties.getApi().getModel());
        body.put("messages", messages);
        body.put("max_tokens", llmProperties.getApi().getMaxTokens());

        // Anthropic 使用 system 参数而非 messages 中的 system role
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            body.put("system", systemPrompt);
        }

        return executeRequest(body, headers, true);
    }

    /**
     * OpenAI 兼容 API 调用
     */
    private String chatOpenAI(String userContent, String systemPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + llmProperties.getApi().getKey());

        List<Map<String, Object>> messages = new ArrayList<>();

        // 添加系统提示
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }

        // 添加用户消息
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmProperties.getApi().getModel());
        body.put("messages", messages);
        body.put("max_tokens", llmProperties.getApi().getMaxTokens());
        body.put("temperature", 0.7);

        return executeRequest(body, headers, false);
    }

    /**
     * 执行 HTTP 请求
     */
    private String executeRequest(Map<String, Object> body, HttpHeaders headers, boolean isAnthropic) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        int attempts = Math.max(1, llmProperties.getRetries());
        Exception lastException = null;

        for (int i = 1; i <= attempts; i++) {
            try {
                logger.info("LLM request attempt {}/{}, provider={}", i, attempts,
                    llmProperties.getApi().getProvider());

                ResponseEntity<String> response = restTemplate.exchange(
                    llmProperties.getApi().getUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String result = parseResponse(response.getBody(), isAnthropic);
                    if (result != null) {
                        logger.info("LLM response received, length: {}", result.length());
                        return result;
                    }
                } else {
                    logger.warn("LLM non-2xx response: status={}", response.getStatusCode());
                }
            } catch (Exception e) {
                lastException = e;
                logger.warn("LLM request failed (attempt {}): {}", i, e.getMessage());
            }
        }

        if (lastException != null) {
            logger.error("LLM request failed after {} attempts: {}", attempts, lastException.getMessage());
        }
        return null;
    }

    /**
     * 解析响应
     */
    private String parseResponse(String responseBody, boolean isAnthropic) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (isAnthropic) {
                // Anthropic 格式: {"content": [{"type": "text", "text": "..."}]}
                JsonNode content = root.path("content");
                if (content.isArray() && content.size() > 0) {
                    JsonNode firstBlock = content.get(0);
                    if ("text".equals(firstBlock.path("type").asText())) {
                        return firstBlock.path("text").asText();
                    }
                }
            } else {
                // OpenAI 格式: {"choices": [{"message": {"content": "..."}}]}
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode contentNode = choices.get(0).path("message").path("content");
                    if (contentNode.isTextual()) {
                        return contentNode.asText();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse LLM response: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查 LLM 是否已配置
     */
    public boolean isConfigured() {
        String apiKey = llmProperties.getApi().getKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * 获取当前配置信息（脱敏）
     */
    public Map<String, Object> getConfigInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("configured", isConfigured());
        info.put("provider", llmProperties.getApi().getProvider());
        info.put("model", llmProperties.getApi().getModel());
        info.put("url", llmProperties.getApi().getUrl());
        info.put("maxTokens", llmProperties.getApi().getMaxTokens());
        info.put("timeoutMs", llmProperties.getTimeout().getMs());
        // API key 脱敏显示
        String key = llmProperties.getApi().getKey();
        if (key != null && key.length() > 8) {
            info.put("apiKey", key.substring(0, 4) + "****" + key.substring(key.length() - 4));
        } else {
            info.put("apiKey", "not configured");
        }
        return info;
    }
}