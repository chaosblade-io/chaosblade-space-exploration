package com.chaosblade.svc.reqrspproxy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

/** 服务录制状态响应 */
public class ServiceRecordingStatusResponse {

  private String namespace;
  private String serviceName;
  private Boolean isRecording;
  private String recordingId;
  private List<RecordingRule> recordingRules;
  private List<InterceptionRule> interceptionRules;
  private String message;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private LocalDateTime startedAt;

  public ServiceRecordingStatusResponse() {}

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

  public Boolean getIsRecording() {
    return isRecording;
  }

  public void setIsRecording(Boolean isRecording) {
    this.isRecording = isRecording;
  }

  public String getRecordingId() {
    return recordingId;
  }

  public void setRecordingId(String recordingId) {
    this.recordingId = recordingId;
  }

  public List<RecordingRule> getRecordingRules() {
    return recordingRules;
  }

  public void setRecordingRules(List<RecordingRule> recordingRules) {
    this.recordingRules = recordingRules;
  }

  public List<InterceptionRule> getInterceptionRules() {
    return interceptionRules;
  }

  public void setInterceptionRules(List<InterceptionRule> interceptionRules) {
    this.interceptionRules = interceptionRules;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime startedAt) {
    this.startedAt = startedAt;
  }

  @Override
  public String toString() {
    return "ServiceRecordingStatusResponse{"
        + "namespace='"
        + namespace
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", isRecording="
        + isRecording
        + ", recordingId='"
        + recordingId
        + '\''
        + ", recordingRules="
        + recordingRules
        + ", interceptionRules="
        + interceptionRules
        + ", message='"
        + message
        + '\''
        + ", startedAt="
        + startedAt
        + '}';
  }
}
