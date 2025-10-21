package com.chaosblade.svc.faultscheduler.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.faultscheduler.service.FaultsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 故障管理 REST API 控制器 提供故障执行、状态查询、停止等核心接口 */
@RestController
@RequestMapping("/api/faults")
@Validated
public class FaultsController {

  private static final Logger logger = LoggerFactory.getLogger(FaultsController.class);

  private final FaultsService faultsService;

  public FaultsController(FaultsService faultsService) {
    this.faultsService = faultsService;
  }

  /**
   * 执行故障 接收完整CR或仅spec(JSON)；可选 name/durationSec
   *
   * @param name 故障名称（可选）
   * @param durationSec TTL秒数（可选）
   * @param faultJson 故障定义JSON
   * @return 执行结果
   */
  @PostMapping("/execute")
  public ApiResponse<Map<String, String>> execute(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) @Positive Integer durationSec,
      @RequestBody @Valid @NotNull Map<String, Object> faultJson) {

    logger.info("Received fault execution request: name={}, durationSec={}", name, durationSec);
    logger.debug("Fault JSON payload: {}", faultJson);

    Map<String, String> result = faultsService.execute(faultJson, name, durationSec);

    logger.info("Successfully executed fault: {}", result);
    return ApiResponse.ok(result);
  }

  /**
   * 查看故障状态和事件
   *
   * @param bladeName 故障名称
   * @return 故障状态和事件信息
   */
  @GetMapping("/{bladeName}/status")
  public ApiResponse<Map<String, Object>> status(@PathVariable @NotEmpty String bladeName) {

    logger.info("Received status query request for bladeName: {}", bladeName);

    Map<String, Object> result = faultsService.statusAndEvents(bladeName);

    logger.debug("Successfully retrieved status for bladeName: {}", bladeName);
    return ApiResponse.ok(result);
  }

  /**
   * 停止故障（删除 CR）
   *
   * @param bladeName 故障名称
   * @return 删除结果
   */
  @DeleteMapping("/{bladeName}")
  public ApiResponse<String> stop(@PathVariable @NotEmpty String bladeName) {

    logger.info("Received fault stop request for bladeName: {}", bladeName);

    faultsService.stop(bladeName);

    logger.info("Successfully stopped fault: {}", bladeName);
    return ApiResponse.ok("Fault stopped successfully: " + bladeName);
  }

  /**
   * 获取单个故障的详细信息
   *
   * @param bladeName 故障名称
   * @return 故障详细信息
   */
  @GetMapping("/{bladeName}")
  public ApiResponse<Map<String, Object>> getFault(@PathVariable @NotEmpty String bladeName) {

    logger.debug("Received get fault request for bladeName: {}", bladeName);

    Map<String, Object> fault = faultsService.getFaultDetails(bladeName);

    logger.debug("Successfully retrieved fault details for bladeName: {}", bladeName);
    return ApiResponse.ok(fault);
  }

  /**
   * 列出所有故障
   *
   * @return 故障名称列表
   */
  @GetMapping
  public ApiResponse<Set<String>> listFaults() {

    logger.debug("Received list faults request");

    Set<String> faults = faultsService.listAllFaults();

    logger.debug("Successfully retrieved {} faults", faults.size());
    return ApiResponse.ok(faults);
  }

  /**
   * 检查故障是否存在
   *
   * @param bladeName 故障名称
   * @return 是否存在
   */
  @GetMapping("/{bladeName}/exists")
  public ApiResponse<Boolean> exists(@PathVariable @NotEmpty String bladeName) {

    logger.debug("Received existence check request for bladeName: {}", bladeName);

    boolean exists = faultsService.exists(bladeName);

    logger.debug("Fault existence check for bladeName: {}, exists: {}", bladeName, exists);
    return ApiResponse.ok(exists);
  }

  /**
   * 健康检查接口
   *
   * @return 服务状态
   */
  @GetMapping("/health")
  public ApiResponse<Map<String, String>> health() {

    logger.debug("Received health check request");

    Map<String, String> health =
        Map.of(
            "status", "UP",
            "service", "svc-fault-scheduler",
            "timestamp", String.valueOf(System.currentTimeMillis()));

    return ApiResponse.ok(health);
  }
}
