package com.chaosblade.svc.reqrspproxy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.Map;

/** 录制的请求-响应条目 */
public class RecordedEntry {

  private String recordingId;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  private LocalDateTime timestamp;

  private String namespace;
  private String serviceName;
  private String pod;
  private String path;
  private String method;
  private Integer status;
  private String xRequestId;
  private String traceparent;

  private Map<String, String> requestHeaders;
  private Map<String, String> responseHeaders;

  private String requestBody;
  private String responseBody;

  private Long reqBytes;
  private Long respBytes;

  private Boolean requestTruncated;
  private Boolean responseTruncated;

  public RecordedEntry() {}

  public String getRecordingId() {
    return recordingId;
  }

  public void setRecordingId(String recordingId) {
    this.recordingId = recordingId;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getPod() {
    return pod;
  }

  public void setPod(String pod) {
    this.pod = pod;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public String getxRequestId() {
    return xRequestId;
  }

  public void setxRequestId(String xRequestId) {
    this.xRequestId = xRequestId;
  }

  public String getTraceparent() {
    return traceparent;
  }

  public void setTraceparent(String traceparent) {
    this.traceparent = traceparent;
  }

  public Map<String, String> getRequestHeaders() {
    return requestHeaders;
  }

  public void setRequestHeaders(Map<String, String> requestHeaders) {
    this.requestHeaders = requestHeaders;
  }

  public Map<String, String> getResponseHeaders() {
    return responseHeaders;
  }

  public void setResponseHeaders(Map<String, String> responseHeaders) {
    this.responseHeaders = responseHeaders;
  }

  public String getRequestBody() {
    return requestBody;
  }

  public void setRequestBody(String requestBody) {
    this.requestBody = requestBody;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public Long getReqBytes() {
    return reqBytes;
  }

  public void setReqBytes(Long reqBytes) {
    this.reqBytes = reqBytes;
  }

  public Long getRespBytes() {
    return respBytes;
  }

  public void setRespBytes(Long respBytes) {
    this.respBytes = respBytes;
  }

  public Boolean getRequestTruncated() {
    return requestTruncated;
  }

  public void setRequestTruncated(Boolean requestTruncated) {
    this.requestTruncated = requestTruncated;
  }

  public Boolean getResponseTruncated() {
    return responseTruncated;
  }

  public void setResponseTruncated(Boolean responseTruncated) {
    this.responseTruncated = responseTruncated;
  }

  @Override
  public String toString() {
    return "RecordedEntry{"
        + "recordingId='"
        + recordingId
        + '\''
        + ", timestamp="
        + timestamp
        + ", namespace='"
        + namespace
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", pod='"
        + pod
        + '\''
        + ", path='"
        + path
        + '\''
        + ", method='"
        + method
        + '\''
        + ", status="
        + status
        + ", xRequestId='"
        + xRequestId
        + '\''
        + ", reqBytes="
        + reqBytes
        + ", respBytes="
        + respBytes
        + '}';
  }
}
