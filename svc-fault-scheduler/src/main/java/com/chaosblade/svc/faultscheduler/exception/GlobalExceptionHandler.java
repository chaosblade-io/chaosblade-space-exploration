package com.chaosblade.svc.faultscheduler.exception;

import com.chaosblade.common.core.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 全局异常处理器 统一处理应用中的各种异常，提供友好的错误响应 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 处理资源不存在异常 */
  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoSuchElementException(NoSuchElementException e) {
    logger.warn("Resource not found: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error("RESOURCE_NOT_FOUND", e.getMessage()));
  }

  /** 处理参数验证异常 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
      IllegalArgumentException e) {
    logger.warn("Invalid argument: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("INVALID_ARGUMENT", "Invalid request: " + e.getMessage()));
  }

  /** 处理方法参数验证异常 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException e) {
    String errorMessage =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

    logger.warn("Method argument validation failed: {}", errorMessage);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("VALIDATION_FAILED", "Validation failed: " + errorMessage));
  }

  /** 处理绑定异常 */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
    String errorMessage =
        e.getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

    logger.warn("Bind validation failed: {}", errorMessage);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("BIND_ERROR", "Validation failed: " + errorMessage));
  }

  /** 处理约束验证异常 */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
      ConstraintViolationException e) {
    String errorMessage =
        e.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining(", "));

    logger.warn("Constraint validation failed: {}", errorMessage);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("CONSTRAINT_VIOLATION", "Validation failed: " + errorMessage));
  }

  /** 处理方法参数类型不匹配异常 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException e) {
    String errorMessage =
        String.format(
            "Parameter '%s' should be of type '%s'",
            e.getName(),
            e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown");

    logger.warn("Method argument type mismatch: {}", errorMessage);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("TYPE_MISMATCH", errorMessage));
  }

  /** 处理 Kubernetes 相关异常 */
  @ExceptionHandler(io.fabric8.kubernetes.client.KubernetesClientException.class)
  public ResponseEntity<ApiResponse<Void>> handleKubernetesClientException(
      io.fabric8.kubernetes.client.KubernetesClientException e) {
    logger.error("Kubernetes client error: {}", e.getMessage(), e);

    // 根据 HTTP 状态码返回相应的错误
    int statusCode = e.getCode();
    String message = "Kubernetes operation failed: " + e.getMessage();

    if (statusCode == 404) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.error(
                  "K8S_RESOURCE_NOT_FOUND", "Resource not found in Kubernetes cluster"));
    } else if (statusCode == 403) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(
              ApiResponse.error(
                  "K8S_PERMISSION_DENIED", "Insufficient permissions for Kubernetes operation"));
    } else if (statusCode == 401) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(ApiResponse.error("K8S_AUTH_FAILED", "Kubernetes authentication failed"));
    } else {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ApiResponse.error("K8S_ERROR", message));
    }
  }

  /** 处理 Redis 连接异常 */
  @ExceptionHandler(org.springframework.data.redis.RedisConnectionFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleRedisConnectionFailureException(
      org.springframework.data.redis.RedisConnectionFailureException e) {
    logger.error("Redis connection failed: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("REDIS_CONNECTION_FAILED", "Database connection failed"));
  }

  /** 处理 JSON 处理异常 */
  @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class)
  public ResponseEntity<ApiResponse<Void>> handleJsonProcessingException(
      com.fasterxml.jackson.core.JsonProcessingException e) {
    logger.warn("JSON processing error: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("JSON_PROCESSING_ERROR", "Invalid JSON format: " + e.getMessage()));
  }

  /** 处理故障调度相关的运行时异常 */
  @ExceptionHandler(FaultSchedulerException.class)
  public ResponseEntity<ApiResponse<Void>> handleFaultSchedulerException(
      FaultSchedulerException e) {
    logger.error("Fault scheduler error: {}", e.getMessage(), e);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("FAULT_SCHEDULER_ERROR", e.getMessage()));
  }

  /** 处理所有其他未捕获的异常 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
    logger.error("Unexpected error occurred", e);

    // 在生产环境中，不应该暴露详细的错误信息
    String message = "An unexpected error occurred. Please contact support.";

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("INTERNAL_ERROR", message));
  }

  /** 处理运行时异常 */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
    logger.error("Runtime error: {}", e.getMessage(), e);

    // 检查是否是已知的业务异常
    if (e.getMessage() != null && e.getMessage().contains("Failed to")) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ApiResponse.error("RUNTIME_ERROR", e.getMessage()));
    }

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("RUNTIME_ERROR", "Internal server error"));
  }
}
