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

/** 命名空间列表响应对象 用于封装 /topology/namespaces 接口的响应数据 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamespaceListResponse {

  /** 请求是否成功 */
  @JsonProperty("success")
  private Boolean success;

  /** 响应数据 */
  @JsonProperty("data")
  private NamespaceListData data;

  // 构造函数
  public NamespaceListResponse() {}

  public NamespaceListResponse(Boolean success, NamespaceListData data) {
    this.success = success;
    this.data = data;
  }

  // Getter and Setter methods
  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public NamespaceListData getData() {
    return data;
  }

  public void setData(NamespaceListData data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "NamespaceListResponse{" + "success=" + success + ", data=" + data + '}';
  }

  /** 命名空间列表数据对象 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class NamespaceListData {

    /** 命名空间详情列表 */
    @JsonProperty("items")
    private List<NamespaceDetail> items;

    /** 总数 */
    @JsonProperty("total")
    private Integer total;

    // 构造函数
    public NamespaceListData() {}

    public NamespaceListData(List<NamespaceDetail> items, Integer total) {
      this.items = items;
      this.total = total;
    }

    // Getter and Setter methods
    public List<NamespaceDetail> getItems() {
      return items;
    }

    public void setItems(List<NamespaceDetail> items) {
      this.items = items;
    }

    public Integer getTotal() {
      return total;
    }

    public void setTotal(Integer total) {
      this.total = total;
    }

    @Override
    public String toString() {
      return "NamespaceListData{" + "items=" + items + ", total=" + total + '}';
    }
  }
}
