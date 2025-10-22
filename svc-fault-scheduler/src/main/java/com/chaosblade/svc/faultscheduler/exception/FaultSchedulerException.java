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
