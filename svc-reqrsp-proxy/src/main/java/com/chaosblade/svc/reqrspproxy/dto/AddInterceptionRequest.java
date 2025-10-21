package com.chaosblade.svc.reqrspproxy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 添加拦截请求
 *
 * <p>智能检测目标服务状态： - 如果服务已在录制模式，基于现有配置添加拦截规则 - 如果服务未在录制模式，启动仅拦截模式
 */
public class AddInterceptionRequest {

  @NotBlank(message = "命名空间不能为空") private String namespace;

  @NotBlank(message = "服务名不能为空") private String serviceName;

  @NotEmpty(message = "拦截规则不能为空") @Valid private List<InterceptionRule> interceptionRules;

  /** 是否启用录制功能 true: 拦截的请求会被录制（默认） false: 纯拦截模式，不录制请求 */
  private boolean enableRecording = true;

  public AddInterceptionRequest() {}

  public AddInterceptionRequest(
      String namespace, String serviceName, List<InterceptionRule> interceptionRules) {
    this.namespace = namespace;
    this.serviceName = serviceName;
    this.interceptionRules = interceptionRules;
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

  public List<InterceptionRule> getInterceptionRules() {
    return interceptionRules;
  }

  public void setInterceptionRules(List<InterceptionRule> interceptionRules) {
    this.interceptionRules = interceptionRules;
  }

  public boolean isEnableRecording() {
    return enableRecording;
  }

  public void setEnableRecording(boolean enableRecording) {
    this.enableRecording = enableRecording;
  }

  @Override
  public String toString() {
    return "AddInterceptionRequest{"
        + "namespace='"
        + namespace
        + '\''
        + ", serviceName='"
        + serviceName
        + '\''
        + ", interceptionRules="
        + interceptionRules
        + ", enableRecording="
        + enableRecording
        + '}';
  }
}
