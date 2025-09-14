package com.chaosblade.svc.reqrspproxy.controller;

import com.chaosblade.common.core.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器 - 用于验证路由是否正确工作
 */
@RestController
@RequestMapping("/api/test")
public class TestController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    
    /**
     * 测试基本连接
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        logger.info("GET /api/test/ping");
        return ApiResponse.success("pong");
    }
    
    /**
     * 测试拦截路由
     */
    @PostMapping("/interceptions/start")
    public ApiResponse<Map<String, Object>> testInterceptionStart(@RequestBody Map<String, Object> request) {
        logger.info("POST /api/test/interceptions/start - request: {}", request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Test interception start route works");
        response.put("receivedRequest", request);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 测试获取状态路由
     */
    @GetMapping("/interceptions/session/{sessionId}/status")
    public ApiResponse<Map<String, Object>> testGetStatus(@PathVariable String sessionId) {
        logger.info("GET /api/test/interceptions/session/{}/status", sessionId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Test get status route works");
        response.put("sessionId", sessionId);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 测试停止拦截路由
     */
    @PostMapping("/interceptions/session/{sessionId}/stop")
    public ApiResponse<Map<String, Object>> testStopInterception(@PathVariable String sessionId) {
        logger.info("POST /api/test/interceptions/session/{}/stop", sessionId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Test stop interception route works");
        response.put("sessionId", sessionId);
        
        return ApiResponse.success(response);
    }
}
