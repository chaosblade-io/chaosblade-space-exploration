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

package com.chaosblade.common.core.exception;

import com.chaosblade.common.core.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
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

/** 全局异常处理器 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 处理业务异常 */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
    logger.warn("Business exception: {}", e.getMessage(), e);
    ApiResponse<Object> response =
        ApiResponse.error(e.getErrorCode(), e.getMessage(), e.getDetails());
    return ResponseEntity.badRequest().body(response);
  }

  /** 处理参数验证异常 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Object>> handleValidationException(
      MethodArgumentNotValidException e) {
    logger.warn("Validation exception: {}", e.getMessage());

    Map<String, String> errors = new HashMap<>();
    e.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });

    ApiResponse<Object> response = ApiResponse.error("VALIDATION_ERROR", "参数验证失败", errors);
    return ResponseEntity.badRequest().body(response);
  }

  /** 处理绑定异常 */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResponse<Object>> handleBindException(BindException e) {
    logger.warn("Bind exception: {}", e.getMessage());

    Map<String, String> errors = new HashMap<>();
    e.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });

    ApiResponse<Object> response = ApiResponse.error("BIND_ERROR", "参数绑定失败", errors);
    return ResponseEntity.badRequest().body(response);
  }

  /** 处理约束违反异常 */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
      ConstraintViolationException e) {
    logger.warn("Constraint violation exception: {}", e.getMessage());

    String errors =
        e.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining(", "));

    ApiResponse<Object> response = ApiResponse.error("CONSTRAINT_VIOLATION", "约束违反", errors);
    return ResponseEntity.badRequest().body(response);
  }

  /** 处理参数类型不匹配异常 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Object>> handleTypeMismatchException(
      MethodArgumentTypeMismatchException e) {
    logger.warn("Type mismatch exception: {}", e.getMessage());

    String message =
        String.format("参数 '%s' 类型错误，期望类型: %s", e.getName(), e.getRequiredType().getSimpleName());

    ApiResponse<Object> response = ApiResponse.error("TYPE_MISMATCH", message);
    return ResponseEntity.badRequest().body(response);
  }

  /** 处理IllegalArgumentException */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(
      IllegalArgumentException e) {
    logger.warn("Illegal argument exception: {}", e.getMessage());
    ApiResponse<Object> response = ApiResponse.error("ILLEGAL_ARGUMENT", e.getMessage());
    return ResponseEntity.badRequest().body(response);
  }

  /** 处理其他未知异常 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception e) {
    logger.error("Unexpected exception: {}", e.getMessage(), e);
    ApiResponse<Object> response = ApiResponse.error("INTERNAL_ERROR", "系统内部错误");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}
