package com.chaosblade.svc.reqrspproxy.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.reqrspproxy.dto.RequestPatternRequest;
import com.chaosblade.svc.reqrspproxy.dto.RequestPatternResponse;
import com.chaosblade.svc.reqrspproxy.dto.ReplayRequest;
import com.chaosblade.svc.reqrspproxy.dto.ReplayResult;
import com.chaosblade.svc.reqrspproxy.service.RequestPatternService;
import com.chaosblade.svc.reqrspproxy.service.ReplayService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求模式获取控制器
 * 提供请求模式获取的REST API接口
 */
@RestController
@RequestMapping("/api/request-patterns")
public class RequestPatternController {

    private static final Logger logger = LoggerFactory.getLogger(RequestPatternController.class);

    @Autowired
    private RequestPatternService requestPatternService;
    @Autowired
    private ReplayService replayService;

    /**
     * 获取请求模式
     * POST /api/request-patterns/analyze
     * 
     * 请求参数示例：
     * {
     *   "reqDefId": 1,
     *   "namespace": "train-ticket",
     *   "serviceList": [
     *     "ts-preserve-service",
     *     "ts-security-service",
     *     "ts-order-service",
     *     "ts-order-other-service",
     *     "ts-contacts-service",
     *     "ts-travel-service",
     *     "ts-ticketinfo-service",
     *     "ts-basic-service",
     *     "ts-station-service",
     *     "ts-route-service",
     *     "ts-train-service",
     *     "ts-price-service",
     *     "ts-seat-service",
     *     "ts-config-service",
     *     "ts-food-service",
     *     "ts-consign-service",
     *     "ts-consign-price-service",
     *     "ts-user-service"
     *   ]
     * }
     * 
     * 响应格式：
     * {
     *   "code": "200",
     *   "message": "success",
     *   "data": {
     *     "taskId": "task_1693123456789_abc12345",
     *     "status": "PROCESSING",
     *     "message": "任务已启动",
     *     "reqDefId": 1,
     *     "namespace": "train-ticket",
     *     "serviceList": [...],
     *     "requestPatterns": [
     *       {
     *         "serviceName": "ts-preserve-service",
     *         "requestMode": [
     *           {
     *             "method": "POST",
     *             "url": "/api/v1/preserveservice/preserve"
     *           }
     *         ]
     *       }
     *     ],
     *     "startTime": "2023-08-27 14:30:00",
     *     "endTime": "2023-08-27 14:35:00",
     *     "totalRecordedRequests": 25,
     *     "analyzedServices": 18,
     *     "recordingId": "rec_20230827_143000_xyz"
     *   }
     * }
     */
    @PostMapping("/analyze")
    public ApiResponse<RequestPatternResponse> analyzeRequestPatterns(
            @Valid @RequestBody RequestPatternRequest request) {
        
        logger.info("POST /api/request-patterns/analyze - reqDefId: {}, execution_id: {}, namespace: {}, services: {}",
                   request.getReqDefId(), request.getExecutionId(), request.getNamespace(), request.getServiceList() == null ? 0 : request.getServiceList().size());
        
        try {
            RequestPatternResponse response = requestPatternService.getRequestPattern(request);
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to analyze request patterns: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to analyze request patterns: " + e.getMessage());
        }
    }
    
    /**
     * 获取任务状态
     * GET /api/request-patterns/tasks/{taskId}
     *
     * 响应示例：
     * {
     *   "code": "200",
     *   "message": "success",
     *   "data": {
     *     "taskId": "task_1693123456789_abc12345",
     *     "status": "ROLLING_UPDATE",
     *     "message": "等待服务滚动更新完成，应用Envoy配置中...",
     *     "reqDefId": 1,
     *     "namespace": "train-ticket",
     *     "serviceList": [...],
     *     "startTime": "2023-08-27 14:30:00",
     *     "totalRecordedRequests": 0,
     *     "analyzedServices": 0,
     *     "recordingId": "rec_20230827_143000_xyz"
     *   }
     * }
     *
     * 状态说明：
     * - INITIALIZING: 初始化阶段
     * - APPLYING_RULES: 规则应用阶段
     * - ROLLING_UPDATE: 滚动更新阶段
     * - TRIGGERING_REQUESTS: 请求发起阶段
     * - COLLECTING_DATA: 数据收集阶段
     * - ANALYZING_PATTERNS: 模式分析阶段
     * - COMPLETED: 完成阶段
     * - FAILED: 失败阶段
     */
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<RequestPatternResponse> getTaskStatus(@PathVariable String taskId) {
        logger.info("GET /api/request-patterns/tasks/{}", taskId);

        try {
            RequestPatternResponse response = requestPatternService.getTaskStatus(taskId);
            return ApiResponse.success(response);

        } catch (Exception e) {
            logger.error("Failed to get task status for {}: {}", taskId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get task status: " + e.getMessage());
        }
    }
    
    /**
     * 手动触发请求发起
     * POST /api/request-patterns/tasks/{taskId}/trigger
     *
     * 用于在滚动更新完成后手动触发请求发起
     */
    @PostMapping("/tasks/{taskId}/trigger")
    public ApiResponse<String> triggerRequests(@PathVariable String taskId) {
        logger.info("POST /api/request-patterns/tasks/{}/trigger", taskId);

        try {
            boolean success = requestPatternService.triggerRequestsForTask(taskId);
            if (success) {
                return ApiResponse.success("请求已触发");
            } else {
                return ApiResponse.error("400", "任务不存在或状态不正确");
            }

        } catch (Exception e) {
            logger.error("Failed to trigger requests for task {}: {}", taskId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to trigger requests: " + e.getMessage());
        }
    }

    /**
     * 获取请求模式记录详情
     * GET /api/request-patterns/tasks/{taskId}/details
     *
     * 获取任务的详细信息，包括录制数据和分析结果
     */
    @GetMapping("/tasks/{taskId}/details")
    public ApiResponse<RequestPatternResponse> getTaskDetails(@PathVariable String taskId) {
        logger.info("GET /api/request-patterns/tasks/{}/details", taskId);

        try {
            RequestPatternResponse response = requestPatternService.getTaskDetails(taskId);
            return ApiResponse.success(response);

        } catch (Exception e) {
            logger.error("Failed to get task details for {}: {}", taskId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get task details: " + e.getMessage());
        }
    }

    /**
     * 停止请求模式记录
     * POST /api/request-patterns/tasks/{taskId}/stop
     *
     * 手动停止正在进行的请求模式记录任务
     *
     * 响应示例：
     * {
     *   "code": "200",
     *   "message": "success",
     *   "data": "录制已停止，正在分析数据..."
     * }
     */
    @PostMapping("/tasks/{taskId}/stop")
    public ApiResponse<String> stopRequestPatternRecording(@PathVariable String taskId) {
        logger.info("POST /api/request-patterns/tasks/{}/stop", taskId);

        try {
            boolean success = requestPatternService.stopRequestPatternRecording(taskId);
            if (success) {
                return ApiResponse.success("录制已停止，正在分析数据...");
            } else {
                return ApiResponse.error("400", "任务不存在或已经完成");
            }

        } catch (Exception e) {
            logger.error("Failed to stop recording for task {}: {}", taskId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to stop recording: " + e.getMessage());
        }
    }

    /**
     * 健康检查接口
     * GET /api/request-patterns/health
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("service", "Request Pattern Service is running");

        // 检查 Redis 连接
        try {
            requestPatternService.testRedisConnection();
            healthInfo.put("redis", "connected");
        } catch (Exception e) {
            healthInfo.put("redis", "failed: " + e.getMessage());
        }

        return ApiResponse.success(healthInfo);
    }

    /**
     * Redis 数据统计接口
     * GET /api/request-patterns/redis-stats
     */
    @GetMapping("/redis-stats")
    public ApiResponse<Map<String, Object>> getRedisStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            Map<String, Object> redisStats = requestPatternService.getRedisStats();
            stats.putAll(redisStats);

        } catch (Exception e) {
            stats.put("error", "Failed to get Redis stats: " + e.getMessage());
        }

        return ApiResponse.success(stats);
    }

    /**
     * 请求重放
     * POST /api/request-patterns/replay
     */
    @PostMapping("/replay")
    public ApiResponse<List<ReplayResult>> replay(@Valid @RequestBody ReplayRequest request) {
        logger.info("POST /api/request-patterns/replay - execution_id: {}, namespace: {}, service_name: {}",
                request.getExecutionId(), request.getNamespace(), request.getServiceName());
        try {
            List<ReplayResult> results = replayService.replay(request);
            return ApiResponse.success(results);
        } catch (Exception e) {
            logger.error("Failed to replay: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to replay: " + e.getMessage());
        }
    }
}
