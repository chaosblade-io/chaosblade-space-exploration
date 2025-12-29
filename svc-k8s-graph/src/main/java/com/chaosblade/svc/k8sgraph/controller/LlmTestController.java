package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.svc.k8sgraph.client.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 测试接口 - 用于测试大模型调用
 */
@RestController
@RequestMapping("/api/llm")
public class LlmTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(LlmTestController.class);
    
    @Autowired
    private LlmClient llmClient;
    
    /**
     * 获取 LLM 配置信息
     * GET /api/llm/config
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(llmClient.getConfigInfo());
    }
    
    /**
     * 简单对话测试
     * POST /api/llm/chat
     * {
     *   "message": "Hello, world"
     * }
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        long startTime = System.currentTimeMillis();
        String message = request.get("message");
        
        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "message is required");
            return ResponseEntity.badRequest().body(error);
        }

        logger.info("LLM test chat request: {}", message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("request", message);
        
        try {
            String result = llmClient.chat(message);
            response.put("response", result);
            response.put("success", result != null);
            response.put("responseTimeMs", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            logger.error("LLM chat failed: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("responseTimeMs", System.currentTimeMillis() - startTime);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 带系统提示的对话测试
     * POST /api/llm/chat-with-system
     * {
     *   "message": "用户消息",
     *   "systemPrompt": "系统提示"
     * }
     */
    @PostMapping("/chat-with-system")
    public ResponseEntity<Map<String, Object>> chatWithSystem(@RequestBody Map<String, String> request) {
        long startTime = System.currentTimeMillis();
        String message = request.get("message");
        String systemPrompt = request.get("systemPrompt");
        
        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "message is required");
            return ResponseEntity.badRequest().body(error);
        }

        logger.info("LLM test chat with system: message={}, systemPrompt length={}",
            message, systemPrompt != null ? systemPrompt.length() : 0);
        
        Map<String, Object> response = new HashMap<>();
        response.put("request", message);
        response.put("systemPrompt", systemPrompt != null ? 
            (systemPrompt.length() > 100 ? systemPrompt.substring(0, 100) + "..." : systemPrompt) : null);
        
        try {
            String result = llmClient.chat(message, systemPrompt);
            response.put("response", result);
            response.put("success", result != null);
            response.put("responseTimeMs", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            logger.error("LLM chat failed: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("responseTimeMs", System.currentTimeMillis() - startTime);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 健康检查 - 测试 LLM 连接
     * GET /api/llm/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("configured", llmClient.isConfigured());
        result.putAll(llmClient.getConfigInfo());
        
        if (llmClient.isConfigured()) {
            // 发送简单测试消息
            long startTime = System.currentTimeMillis();
            try {
                String response = llmClient.chat("Say 'OK' if you can hear me.");
                result.put("connectionTest", response != null ? "success" : "failed");
                result.put("testResponseTimeMs", System.currentTimeMillis() - startTime);
                if (response != null) {
                    result.put("testResponse", response.length() > 100 ? 
                        response.substring(0, 100) + "..." : response);
                }
            } catch (Exception e) {
                result.put("connectionTest", "error");
                result.put("error", e.getMessage());
            }
        }
        
        return ResponseEntity.ok(result);
    }
}

