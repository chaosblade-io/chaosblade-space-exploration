package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 命名空间列表响应对象
 * 用于封装 /topology/namespaces 接口的响应数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamespaceListResponse {

    /**
     * 请求是否成功
     */
    @JsonProperty("success")
    private Boolean success;

    /**
     * 响应数据
     */
    @JsonProperty("data")
    private NamespaceListData data;

    // 构造函数
    public NamespaceListResponse() {
    }

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
        return "NamespaceListResponse{" +
                "success=" + success +
                ", data=" + data +
                '}';
    }

    /**
     * 命名空间列表数据对象
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NamespaceListData {

        /**
         * 命名空间详情列表
         */
        @JsonProperty("items")
        private List<NamespaceDetail> items;

        /**
         * 总数
         */
        @JsonProperty("total")
        private Integer total;

        // 构造函数
        public NamespaceListData() {
        }

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
            return "NamespaceListData{" +
                    "items=" + items +
                    ", total=" + total +
                    '}';
        }
    }
}