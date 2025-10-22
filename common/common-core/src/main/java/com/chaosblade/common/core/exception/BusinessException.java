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

/** 业务异常类 */
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
