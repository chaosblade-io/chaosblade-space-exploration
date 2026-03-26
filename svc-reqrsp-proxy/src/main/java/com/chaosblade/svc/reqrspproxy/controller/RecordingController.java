package com.chaosblade.svc.reqrspproxy.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.reqrspproxy.dto.*;
import com.chaosblade.svc.reqrspproxy.service.RecordingService;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 录制控制器
 */
@RestController
@RequestMapping("/api/recordings")
public class RecordingController {
    
    private static final Logger logger = LoggerFactory.getLogger(RecordingController.class);
    
    @Autowired
    private RecordingService recordingService;
    
    /**
     * 开始录制
     * POST /api/recordings/start
     */
    @PostMapping("/start")
    public ApiResponse<RecordingResponse> start(@Valid @RequestBody StartRecordingRequest request) {
        logger.info("POST /api/recordings/start - namespace: {}, serviceName: {}, rules: {}", 
                   request.getNamespace(), request.getServiceName(), request.getRules().size());
        
        try {
            RecordingResponse response = recordingService.start(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to start recording: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to start recording: " + e.getMessage());
        }
    }
    
    /**
     * 停止录制
     * POST /api/recordings/{recordingId}/stop
     */
    @PostMapping("/{recordingId}/stop")
    public ApiResponse<RecordingResponse> stop(@PathVariable String recordingId) {
        logger.info("POST /api/recordings/{}/stop", recordingId);
        
        try {
            RecordingResponse response = recordingService.stop(recordingId);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to stop recording {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to stop recording: " + e.getMessage());
        }
    }
    
    /**
     * 获取录制状态
     * GET /api/recordings/{recordingId}
     */
    @GetMapping("/{recordingId}")
    public ApiResponse<RecordingStatusResponse> getStatus(@PathVariable String recordingId) {
        logger.info("GET /api/recordings/{}", recordingId);
        
        try {
            RecordingStatusResponse response = recordingService.getStatus(recordingId);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to get recording status for {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get recording status: " + e.getMessage());
        }
    }
    
    /**
     * 获取录制条目
     * GET /api/recordings/{recordingId}/entries
     */
    @GetMapping("/{recordingId}/entries")
    public ApiResponse<List<RecordedEntry>> getEntries(
            @PathVariable String recordingId,
            @RequestParam(value = "offset", defaultValue = "0") @Min(0) int offset,
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) int limit) {
        
        logger.info("GET /api/recordings/{}/entries - offset: {}, limit: {}", recordingId, offset, limit);
        
        try {
            List<RecordedEntry> entries = recordingService.getEntries(recordingId, offset, limit);
            return ApiResponse.success(entries);
        } catch (Exception e) {
            logger.error("Failed to get entries for recording {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get recording entries: " + e.getMessage());
        }
    }
    
    /**
     * 停止录制但保持 proxy-agent 运行
     * POST /api/recordings/{recordingId}/stop-only
     */
    @PostMapping("/{recordingId}/stop-only")
    public ApiResponse<RecordingResponse> stopOnly(@PathVariable String recordingId) {
        logger.info("POST /api/recordings/{}/stop-only", recordingId);
        try {
            RecordingResponse response = recordingService.stopRecordingOnly(recordingId);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to stop-only recording {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to stop recording: " + e.getMessage());
        }
    }

    /**
     * 切换到 intercept 模式并加载规则
     * POST /api/recordings/{recordingId}/switch-intercept
     */
    @PostMapping("/{recordingId}/switch-intercept")
    public ApiResponse<String> switchIntercept(@PathVariable String recordingId,
                                               @RequestBody java.util.List<java.util.Map<String, Object>> rules) {
        logger.info("POST /api/recordings/{}/switch-intercept - rules: {}", recordingId, rules.size());
        try {
            recordingService.switchToIntercept(recordingId, rules);
            return ApiResponse.success("Switched to intercept mode with " + rules.size() + " rules");
        } catch (Exception e) {
            logger.error("Failed to switch intercept for {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to switch intercept: " + e.getMessage());
        }
    }

    /**
     * 完整清理（恢复 selector + 删除 proxy-agent + 删除影子 Service）
     * POST /api/recordings/{recordingId}/cleanup
     */
    @PostMapping("/{recordingId}/cleanup")
    public ApiResponse<String> cleanup(@PathVariable String recordingId) {
        logger.info("POST /api/recordings/{}/cleanup", recordingId);
        try {
            recordingService.fullCleanup(recordingId);
            return ApiResponse.success("Cleanup completed");
        } catch (Exception e) {
            logger.error("Failed to cleanup recording {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to cleanup: " + e.getMessage());
        }
    }

    /**
     * 获取 proxy-agent 快照数据
     * GET /api/recordings/{recordingId}/snapshots
     */
    @GetMapping("/{recordingId}/snapshots")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> getSnapshots(@PathVariable String recordingId) {
        logger.info("GET /api/recordings/{}/snapshots", recordingId);
        try {
            java.util.List<java.util.Map<String, Object>> snapshots = recordingService.getProxySnapshots(recordingId);
            return ApiResponse.success(snapshots);
        } catch (Exception e) {
            logger.error("Failed to get snapshots for {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get snapshots: " + e.getMessage());
        }
    }

    /**
     * 恢复录制模式
     * POST /api/recordings/{recordingId}/resume
     */
    @PostMapping("/{recordingId}/resume")
    public ApiResponse<String> resume(@PathVariable String recordingId) {
        logger.info("POST /api/recordings/{}/resume", recordingId);
        try {
            recordingService.resumeRecording(recordingId);
            return ApiResponse.success("Recording resumed");
        } catch (Exception e) {
            logger.error("Failed to resume recording {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to resume: " + e.getMessage());
        }
    }

    /**
     * 清空 proxy-agent 快照
     * DELETE /api/recordings/{recordingId}/snapshots
     */
    @DeleteMapping("/{recordingId}/snapshots")
    public ApiResponse<String> clearSnapshots(@PathVariable String recordingId) {
        logger.info("DELETE /api/recordings/{}/snapshots", recordingId);
        try {
            recordingService.clearProxySnapshots(recordingId);
            return ApiResponse.success("Snapshots cleared");
        } catch (Exception e) {
            logger.error("Failed to clear snapshots {}: {}", recordingId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to clear snapshots: " + e.getMessage());
        }
    }

    /**
     * 获取 recordingId 对应的服务名
     * GET /api/recordings/{recordingId}/service-name
     */
    @GetMapping("/{recordingId}/service-name")
    public ApiResponse<String> getServiceName(@PathVariable String recordingId) {
        String name = recordingService.getServiceName(recordingId);
        return ApiResponse.success(name);
    }

    /**
     * 健康检查
     * GET /api/recordings/health
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Recording service is healthy");
    }
}
