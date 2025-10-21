package com.chaosblade.svc.taskresource.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class ProbeTaskDtos {

  public static class ApiDefinitionDTO {
    public String code;
    public String name;
    public String method; // GET/POST/...
    public String urlTemplate;
    public String headers; // JSON string (optional)
    public String queryParams; // JSON string (optional)
    public String bodyMode; // NONE/JSON/FORM/RAW
    public String contentType; // optional
    public String bodyTemplate; // JSON string (optional)
    public Long apiId; // required
  }

  public static class FaultConfigurationItem {
    public Long nodeId; // required
    public String type; // optional but preferred
    public Map<String, Object> faultscript; // preferred key

    // 兼容前端可能的拼写：faulscript（少个 t）
    @JsonProperty("faulscript")
    public void setFaulscript(Map<String, Object> v) {
      this.faultscript = v;
    }
  }

  public static class TaskSloItem {
    @JsonProperty("node_id")
    public Long nodeId; // required

    public Integer p95; // optional
    public Integer p99; // optional
    public Integer errRate; // optional
  }

  public static class ProbeTaskCreateRequest {
    public String name; // required
    public String description; // required
    public Long systemId; // required
    public Long apiId; // required
    public String createdBy; // required
    public String updatedBy; // optional
    public Long createdAt; // epoch millis (optional)
    public Long updatedAt; // epoch millis (optional)
    public Long archivedAt; // epoch millis (optional)
    public Integer requestNum; // required

    public List<FaultConfigurationItem> faultConfigurations; // required (>=0)
    public List<TaskSloItem> taskSlo; // required (>=0)
    public ApiDefinitionDTO apiDefinition; // required
  }

  public static class ProbeTaskCreateResponse {
    public Long taskId;
    public Long apiDefinitionId;
    public int createdFaultConfigCount;
    public int createdTaskSloCount;

    public ProbeTaskCreateResponse() {}

    public ProbeTaskCreateResponse(
        Long taskId, Long apiDefinitionId, int createdFaultConfigCount, int createdTaskSloCount) {
      this.taskId = taskId;
      this.apiDefinitionId = apiDefinitionId;
      this.createdFaultConfigCount = createdFaultConfigCount;
      this.createdTaskSloCount = createdTaskSloCount;
    }
  }
}
