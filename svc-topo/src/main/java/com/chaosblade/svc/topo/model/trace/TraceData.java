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

package com.chaosblade.svc.topo.model.trace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** OpenTelemetry Trace数据模型 基于trace-*.json文件格式定义 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceData {

  /** Trace数据数组 */
  @JsonProperty("data")
  private List<TraceRecord> data;

  public TraceData() {}

  public List<TraceRecord> getData() {
    return data;
  }

  public void setData(List<TraceRecord> data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "TraceData{" + "data=" + (data != null ? data.size() + " records" : "null") + '}';
  }

  /** Trace记录类 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TraceRecord {

    /** Trace ID */
    @JsonProperty("traceID")
    private String traceId;

    /** Span列表 */
    @JsonProperty("spans")
    private List<SpanData> spans;

    /** Process信息 */
    @JsonProperty("processes")
    private Map<String, ProcessData> processes;

    public TraceRecord() {}

    public String getTraceId() {
      return traceId;
    }

    public void setTraceId(String traceId) {
      this.traceId = traceId;
    }

    public List<SpanData> getSpans() {
      return spans;
    }

    public void setSpans(List<SpanData> spans) {
      this.spans = spans;
    }

    public Map<String, ProcessData> getProcesses() {
      return processes;
    }

    public void setProcesses(Map<String, ProcessData> processes) {
      this.processes = processes;
    }

    @Override
    public String toString() {
      return "TraceRecord{"
          + "traceId='"
          + traceId
          + '\''
          + ", spans="
          + (spans != null ? spans.size() : 0)
          + ", processes="
          + (processes != null ? processes.size() : 0)
          + '}';
    }
  }
}
