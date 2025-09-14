package com.chaosblade.svc.topo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * 统一处理应用中的异常，返回标准的错误响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理文件大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        logger.warn("文件大小超出限制: {}", e.getMessage());

        Map<String, Object> response = createErrorResponse(
            "文件大小超出限制，最大支持50MB",
            "FILE_TOO_LARGE",
            HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("参数错误: {}", e.getMessage());

        Map<String, Object> response = createErrorResponse(
            "参数错误: " + e.getMessage(),
            "INVALID_ARGUMENT",
            HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointer(NullPointerException e) {
        logger.error("空指针异常: {}", e.getMessage(), e);

        Map<String, Object> response = createErrorResponse(
            "系统内部错误",
            "NULL_POINTER",
            HttpStatus.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理IO异常
     */
    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(java.io.IOException e) {
        logger.error("IO异常: {}", e.getMessage(), e);

        Map<String, Object> response = createErrorResponse(
            "文件处理失败: " + e.getMessage(),
            "IO_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理JSON解析异常
     */
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleJsonProcessing(com.fasterxml.jackson.core.JsonProcessingException e) {
        logger.warn("JSON解析异常: {}", e.getMessage());

        Map<String, Object> response = createErrorResponse(
            "JSON格式错误: " + e.getMessage(),
            "JSON_PARSE_ERROR",
            HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        logger.error("运行时异常: {}", e.getMessage(), e);

        Map<String, Object> response = createErrorResponse(
            "处理失败: " + e.getMessage(),
            "RUNTIME_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        logger.error("未知异常: {}", e.getMessage(), e);

        Map<String, Object> response = createErrorResponse(
            "系统内部错误，请稍后重试",
            "INTERNAL_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 创建标准错误响应
     */
    private Map<String, Object> createErrorResponse(String message, String errorCode, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("errorCode", errorCode);
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", status.value());

        return response;
    }
}
