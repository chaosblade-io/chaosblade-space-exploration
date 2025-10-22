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

package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** API查询请求对象 对应 v1_apis_end2end_request.json 的结构 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiQueryRequest {

  /** k8s 命名空间 */
  @JsonProperty("namespace")
  private String namespace;

  /** 服务选择器 */
  @JsonProperty("appSelector")
  private AppSelector appSelector;

  /** 时间范围 */
  @JsonProperty("timeRange")
  private TimeRange timeRange;

  /** 排序方式 */
  @JsonProperty("sort")
  private Sort sort;

  // 构造函数
  public ApiQueryRequest() {}

  // Getter and Setter methods
  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public AppSelector getAppSelector() {
    return appSelector;
  }

  public void setAppSelector(AppSelector appSelector) {
    this.appSelector = appSelector;
  }

  public TimeRange getTimeRange() {
    return timeRange;
  }

  public void setTimeRange(TimeRange timeRange) {
    this.timeRange = timeRange;
  }

  public Sort getSort() {
    return sort;
  }

  public void setSort(Sort sort) {
    this.sort = sort;
  }

  /** 服务选择器 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class AppSelector {
    @JsonProperty("services")
    private List<String> services;

    public AppSelector() {}

    public List<String> getServices() {
      return services;
    }

    public void setServices(List<String> services) {
      this.services = services;
    }
  }

  /** 时间范围 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class TimeRange {
    @JsonProperty("start")
    private Long start;

    @JsonProperty("end")
    private Long end;

    public TimeRange() {}

    public Long getStart() {
      return start;
    }

    public void setStart(Long start) {
      this.start = start;
    }

    public Long getEnd() {
      return end;
    }

    public void setEnd(Long end) {
      this.end = end;
    }
  }

  /** 排序方式 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Sort {
    public enum SortBy {
      NAME("name"),
      FIRST_SEEN("firstSeen"),
      LAST_SEEN("lastSeen");

      private final String value;

      SortBy(String value) {
        this.value = value;
      }

      public String getValue() {
        return value;
      }

      public static SortBy fromString(String value) {
        for (SortBy sortBy : SortBy.values()) {
          if (sortBy.value.equalsIgnoreCase(value)) {
            return sortBy;
          }
        }
        return NAME; // 默认值
      }
    }

    public enum SortOrder {
      ASC("ASC"),
      DESC("DESC");

      private final String value;

      SortOrder(String value) {
        this.value = value;
      }

      public String getValue() {
        return value;
      }

      public static SortOrder fromString(String value) {
        for (SortOrder order : SortOrder.values()) {
          if (order.value.equalsIgnoreCase(value)) {
            return order;
          }
        }
        return ASC; // 默认值
      }
    }

    @JsonProperty("by")
    private String by = "name";

    @JsonProperty("order")
    private String order = "ASC";

    public Sort() {}

    public String getBy() {
      return by;
    }

    public void setBy(String by) {
      this.by = by;
    }

    public String getOrder() {
      return order;
    }

    public void setOrder(String order) {
      this.order = order;
    }

    public SortBy getSortBy() {
      return SortBy.fromString(by);
    }

    public SortOrder getSortOrder() {
      return SortOrder.fromString(order);
    }
  }
}
