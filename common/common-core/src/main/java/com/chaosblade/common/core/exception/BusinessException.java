package com.chaosblade.common.core.exception;

/**
 * 业务异常类
 */
public class BusinessException extends RuntimeException {
    
    private final String errorCode;
    private final Object details;
    
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public BusinessException(String errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public BusinessException(String errorCode, String message, Object details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Object getDetails() {
        return details;
    }
}
