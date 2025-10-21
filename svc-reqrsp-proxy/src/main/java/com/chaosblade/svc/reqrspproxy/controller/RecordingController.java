package com.chaosblade.svc.reqrspproxy.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.reqrspproxy.dto.*;
import com.chaosblade.svc.reqrspproxy.service.RecordingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 录制控制器 */
@RestController
@RequestMapping("/api/recordings")
public class RecordingController {

  private static final Logger logger = LoggerFactory.getLogger(RecordingController.class);

  @Autowired private RecordingService recordingService;

  /** 开始录制 POST /api/recordings/start */
  @PostMapping("/start")
  public ApiResponse<RecordingResponse> start(@Valid @RequestBody StartRecordingRequest request) {
    logger.info(
        "POST /api/recordings/start - namespace: {}, serviceName: {}, rules: {}",
        request.getNamespace(),
        request.getServiceName(),
        request.getRules().size());

    try {
      RecordingResponse response = recordingService.start(request);
      return ApiResponse.success(response);
    } catch (Exception e) {
      logger.error("Failed to start recording: {}", e.getMessage(), e);
      return ApiResponse.error("500", "Failed to start recording: " + e.getMessage());
    }
  }

  /** 停止录制 POST /api/recordings/{recordingId}/stop */
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

  /** 获取录制状态 GET /api/recordings/{recordingId} */
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

  /** 获取录制条目 GET /api/recordings/{recordingId}/entries */
  @GetMapping("/{recordingId}/entries")
  public ApiResponse<List<RecordedEntry>> getEntries(
      @PathVariable String recordingId,
      @RequestParam(value = "offset", defaultValue = "0") @Min(0) int offset,
      @RequestParam(value = "limit", defaultValue = "50") @Min(1) int limit) {

    logger.info(
        "GET /api/recordings/{}/entries - offset: {}, limit: {}", recordingId, offset, limit);

    try {
      List<RecordedEntry> entries = recordingService.getEntries(recordingId, offset, limit);
      return ApiResponse.success(entries);
    } catch (Exception e) {
      logger.error("Failed to get entries for recording {}: {}", recordingId, e.getMessage(), e);
      return ApiResponse.error("500", "Failed to get recording entries: " + e.getMessage());
    }
  }

  /** 健康检查 GET /api/recordings/health */
  @GetMapping("/health")
  public ApiResponse<String> health() {
    return ApiResponse.success("Recording service is healthy");
  }
}
