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

package com.chaosblade.svc.reqrspproxy.exception;

import com.chaosblade.common.core.ApiResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理器 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 处理参数验证异常 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<String> handleValidationException(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

    logger.warn("Validation error: {}", message);
    return ApiResponse.error("400", "参数验证失败: " + message);
  }

  /** 处理绑定异常 */
  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<String> handleBindException(BindException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

    logger.warn("Bind error: {}", message);
    return ApiResponse.error("400", "参数绑定失败: " + message);
  }

  /** 处理非法参数异常 */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<String> handleIllegalArgumentException(IllegalArgumentException e) {
    logger.warn("Illegal argument: {}", e.getMessage());
    return ApiResponse.error("400", "参数错误: " + e.getMessage());
  }

  /** JSON 反序列化错误（定位具体字段路径） */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<String> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
    Throwable cause = e.getCause();
    String detail = e.getMessage();
    String pathStr = null;
    String targetType = null;
    if (cause instanceof MismatchedInputException mie) {
      // 解析出错字段路径，如: items[0].respBody
      if (mie.getPath() != null && !mie.getPath().isEmpty()) {
        pathStr =
            mie.getPath().stream()
                .map(
                    ref ->
                        ref.getFieldName() != null
                            ? ref.getFieldName()
                            : (ref.getIndex() >= 0 ? "[" + ref.getIndex() + "]" : "?"))
                .collect(Collectors.joining("."));
      }
      targetType = mie.getTargetType() != null ? mie.getTargetType().getTypeName() : null;
    } else if (cause instanceof InvalidFormatException ife) {
      if (ife.getPath() != null && !ife.getPath().isEmpty()) {
        pathStr =
            ife.getPath().stream()
                .map(
                    ref ->
                        ref.getFieldName() != null
                            ? ref.getFieldName()
                            : (ref.getIndex() >= 0 ? "[" + ref.getIndex() + "]" : "?"))
                .collect(Collectors.joining("."));
      }
      targetType = ife.getTargetType() != null ? ife.getTargetType().getTypeName() : null;
    }
    String msg =
        "请求体JSON解析失败"
            + (pathStr != null ? ("，字段路径: " + pathStr) : "")
            + (targetType != null ? ("，目标类型: " + targetType) : "")
            + (detail != null ? ("，原始错误: " + detail) : "");
    logger.warn("JSON parse error: {}", msg, e);
    return ApiResponse.error("400", msg);
  }

  /** 处理运行时异常 */
  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<String> handleRuntimeException(RuntimeException e) {
    logger.error("Runtime error: {}", e.getMessage(), e);
    return ApiResponse.error("500", "服务器内部错误: " + e.getMessage());
  }

  /** 处理通用异常 */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<String> handleException(Exception e) {
    logger.error("Unexpected error: {}", e.getMessage(), e);
    return ApiResponse.error("500", "服务器内部错误: " + e.getMessage());
  }
}
