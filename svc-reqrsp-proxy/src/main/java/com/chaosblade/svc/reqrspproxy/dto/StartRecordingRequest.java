package com.chaosblade.svc.reqrspproxy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** 开始录制请求 */
public class StartRecordingRequest {

  @NotBlank(message = "命名空间不能为空") private String namespace;

  @NotBlank(message = "服务名不能为空") private String serviceName;

  @Positive(message = "应用端口必须是正数") private Integer appPort;

  @NotEmpty(message = "录制规则不能为空") @Valid private List<RecordingRule> rules;

  @Positive(message = "持续时间必须是正数") private Integer durationSec;

  public StartRecordingRequest() {}

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

  public Integer getAppPort() {
    return appPort;
  }

  public void setAppPort(Integer appPort) {
    this.appPort = appPort;
  }

  public List<RecordingRule> getRules() {
    return rules;
  }

  public void setRules(List<RecordingRule> rules) {
    this.rules = rules;
  }

  public Integer getDurationSec() {
    return durationSec;
  }

  public void setDurationSec(Integer durationSec) {
    this.durationSec = durationSec;
  }

  @Override
  public String toString() {
    return "StartRecordingRequest{"
        + "namespace='"
        + namespace
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", appPort="
        + appPort
        + ", rules="
        + rules
        + ", durationSec="
        + durationSec
        + '}';
  }
}
