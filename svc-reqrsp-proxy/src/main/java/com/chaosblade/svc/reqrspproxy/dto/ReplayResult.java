package com.chaosblade.svc.reqrspproxy.dto;

import java.util.List;
import java.util.Map;

/** 单条请求重放结果（向后兼容） */
public class ReplayResult {
  // 既有字段（保持不变）
  private String url;
  private String method;
  private String responseBody;

  // 新增字段（扩展响应信息）
  private Integer statusCode; // HTTP 状态码
  private Map<String, List<String>> responseHeaders; // 响应头（多值）
  private String errorMessage; // 错误信息

  public ReplayResult() {}

  public ReplayResult(String url, String method, String responseBody) {
    this.url = url;
    this.method = method;
    this.responseBody = responseBody;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(Integer statusCode) {
    this.statusCode = statusCode;
  }

  public Map<String, List<String>> getResponseHeaders() {
    return responseHeaders;
  }

  public void setResponseHeaders(Map<String, List<String>> responseHeaders) {
    this.responseHeaders = responseHeaders;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
