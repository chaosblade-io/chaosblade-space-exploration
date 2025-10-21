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
