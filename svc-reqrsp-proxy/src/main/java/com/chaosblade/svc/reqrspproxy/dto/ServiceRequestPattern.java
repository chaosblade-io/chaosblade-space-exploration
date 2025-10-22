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

package com.chaosblade.svc.reqrspproxy.dto;

import java.util.List;
import java.util.Map;

/** 服务请求模式 */
public class ServiceRequestPattern {

  private String serviceName;
  private List<RequestMode> requestMode;

  public ServiceRequestPattern() {}

  public ServiceRequestPattern(String serviceName, List<RequestMode> requestMode) {
    this.serviceName = serviceName;
    this.requestMode = requestMode;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public List<RequestMode> getRequestMode() {
    return requestMode;
  }

  public void setRequestMode(List<RequestMode> requestMode) {
    this.requestMode = requestMode;
  }

  @Override
  public String toString() {
    return "ServiceRequestPattern{"
        + "serviceName='"
        + serviceName
        + '\''
        + ", requestMode="
        + requestMode
        + '}';
  }

  /** 请求模式内部类 */
  public static class RequestMode {
    private String method;
    private String url;
    private Map<String, String> requestHeaders;
    private String requestBody;
    private Map<String, String> responseHeaders;
    private String responseBody;
    private Integer responseStatus;
    private Long responseTime; // 毫秒，可选

    public RequestMode() {}

    public RequestMode(String method, String url) {
      this.method = method;
      this.url = url;
    }

    public RequestMode(
        String method,
        String url,
        Map<String, String> requestHeaders,
        String requestBody,
        Map<String, String> responseHeaders,
        String responseBody,
        Integer responseStatus,
        Long responseTime) {
      this.method = method;
      this.url = url;
      this.requestHeaders = requestHeaders;
      this.requestBody = requestBody;
      this.responseHeaders = responseHeaders;
      this.responseBody = responseBody;
      this.responseStatus = responseStatus;
      this.responseTime = responseTime;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public Map<String, String> getRequestHeaders() {
      return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
      this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
      return requestBody;
    }

    public void setRequestBody(String requestBody) {
      this.requestBody = requestBody;
    }

    public Map<String, String> getResponseHeaders() {
      return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
      this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
      return responseBody;
    }

    public void setResponseBody(String responseBody) {
      this.responseBody = responseBody;
    }

    public Integer getResponseStatus() {
      return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
      this.responseStatus = responseStatus;
    }

    public Long getResponseTime() {
      return responseTime;
    }

    public void setResponseTime(Long responseTime) {
      this.responseTime = responseTime;
    }

    @Override
    public String toString() {
      // 为避免日志中泄露敏感信息，仅打印 method 与 url
      return "RequestMode{" + "method='" + method + '\'' + ", url='" + url + '\'' + '}';
    }
  }
}
