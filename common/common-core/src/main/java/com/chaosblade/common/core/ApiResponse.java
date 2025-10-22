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

package com.chaosblade.common.core;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 统一API响应格式 符合文档要求：{ success, data, error } */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
  private boolean success;
  private T data;
  private ErrorInfo error;

  public ApiResponse() {}

  public ApiResponse(boolean success, T data, ErrorInfo error) {
    this.success = success;
    this.data = data;
    this.error = error;
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static <T> ApiResponse<T> success() {
    return new ApiResponse<>(true, null, null);
  }

  public static <T> ApiResponse<T> ok(T data) {
    return success(data);
  }

  public static <T> ApiResponse<T> ok() {
    return success();
  }

  public static <T> ApiResponse<T> error(String code, String message) {
    return new ApiResponse<>(false, null, new ErrorInfo(code, message, null));
  }

  public static <T> ApiResponse<T> error(String code, String message, Object details) {
    return new ApiResponse<>(false, null, new ErrorInfo(code, message, details));
  }

  public static <T> ApiResponse<T> error(ErrorInfo error) {
    return new ApiResponse<>(false, null, error);
  }

  // Getters and Setters
  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public ErrorInfo getError() {
    return error;
  }

  public void setError(ErrorInfo error) {
    this.error = error;
  }

  /** 错误信息类 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ErrorInfo {
    private String code;
    private String message;
    private Object details;

    public ErrorInfo() {}

    public ErrorInfo(String code, String message, Object details) {
      this.code = code;
      this.message = message;
      this.details = details;
    }

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public Object getDetails() {
      return details;
    }

    public void setDetails(Object details) {
      this.details = details;
    }
  }
}
