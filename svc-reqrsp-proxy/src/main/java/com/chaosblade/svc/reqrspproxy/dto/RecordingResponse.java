package com.chaosblade.svc.reqrspproxy.dto;

/** 录制响应基类 */
public class RecordingResponse {

  private String recordingId;
  private String status;
  private String message;

  public RecordingResponse() {}

  public RecordingResponse(String recordingId, String status) {
    this.recordingId = recordingId;
    this.status = status;
  }

  public RecordingResponse(String recordingId, String status, String message) {
    this.recordingId = recordingId;
    this.status = status;
    this.message = message;
  }

  public String getRecordingId() {
    return recordingId;
  }

  public void setRecordingId(String recordingId) {
    this.recordingId = recordingId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return "RecordingResponse{"
        + "recordingId='"
        + recordingId
        + '\''
        + ", status='"
        + status
        + '\''
        + ", message='"
        + message
        + '\''
        + '}';
  }
}
