package com.chaosblade.svc.reqrspproxy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 录制规则 - 定义要录制的路径和方法 */
public class RecordingRule {

  @NotBlank(message = "路径不能为空") private String path;

  @NotBlank(message = "HTTP方法不能为空") @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS", message = "HTTP方法必须是有效值") private String method;

  public RecordingRule() {}

  public RecordingRule(String path, String method) {
    this.path = path;
    this.method = method;
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

  @Override
  public String toString() {
    return "RecordingRule{" + "path='" + path + '\'' + ", method='" + method + '\'' + '}';
  }
}
