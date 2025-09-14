package com.chaosblade.svc.faultscheduler.exception;

/**
 * 故障调度器自定义异常
 * 用于封装故障调度过程中的业务异常
 */
public class FaultSchedulerException extends RuntimeException {
    
    private final String errorCode;
    
    public FaultSchedulerException(String message) {
        super(message);
        this.errorCode = "FAULT_SCHEDULER_ERROR";
    }
    
    public FaultSchedulerException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "FAULT_SCHEDULER_ERROR";
    }
    
    public FaultSchedulerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public FaultSchedulerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // 常见的错误代码常量
    public static class ErrorCodes {
        public static final String KUBERNETES_CONNECTION_FAILED = "K8S_CONNECTION_FAILED";
        public static final String REDIS_CONNECTION_FAILED = "REDIS_CONNECTION_FAILED";
        public static final String INVALID_FAULT_SPEC = "INVALID_FAULT_SPEC";
        public static final String FAULT_ALREADY_EXISTS = "FAULT_ALREADY_EXISTS";
        public static final String FAULT_NOT_FOUND = "FAULT_NOT_FOUND";
        public static final String FAULT_CREATION_FAILED = "FAULT_CREATION_FAILED";
        public static final String FAULT_DELETION_FAILED = "FAULT_DELETION_FAILED";
        public static final String TTL_SCHEDULING_FAILED = "TTL_SCHEDULING_FAILED";
    }
}
