package com.chaosblade.svc.taskresource.dto;

import com.chaosblade.svc.taskresource.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DetectionTaskDtos {

  public static class DetectionTaskDetails {
    public DetectionTask task;
    public com.chaosblade.svc.taskresource.entity.System sys;
    public HttpReqDef apiDefinition;
    public ApiTopology topology;
    public List<ApiTopologyNode> topologyNodes;
    public List<ApiTopologyEdge> topologyEdges;
    public List<FaultConfig> faultConfigs;
    public List<TaskSlo> taskSlos;
    public String latestExecutionStatus;

    // 前端兼容字段：http_req_def（与 apiDefinition 等价）
    @JsonProperty("http_req_def")
    public HttpReqDef getHttpReqDefAlias() {
      return apiDefinition;
    }

    // 前端兼容字段：request_num（来自 DetectionTask.requestNum）
    @JsonProperty("request_num")
    public Integer getRequestNumAlias() {
      return task != null ? task.getRequestNum() : null;
    }

    public DetectionTaskDetails() {}

    public DetectionTaskDetails(DetectionTask task) {
      this.task = task;
    }
  }

  public static class TaskExecutionView {
    public Long id;
    public String status;
    public String namespace;
    public Integer requestNum;
    public String errorCode;
    public String errorMsg;
    public java.time.LocalDateTime startedAt;
    public java.time.LocalDateTime finishedAt;
    public long duration;
    public String taskName; // 新增：探测任务名称
  }
}
