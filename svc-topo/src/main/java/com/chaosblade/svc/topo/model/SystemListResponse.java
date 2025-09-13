package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 系统列表响应对象
 * 用于封装系统信息列表的响应数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemListResponse {

    /**
     * 请求是否成功
     */
    @JsonProperty("success")
    private Boolean success;

    /**
     * 响应数据
     */
    @JsonProperty("data")
    private SystemListData data;

    // 构造函数
    public SystemListResponse() {
    }

    public SystemListResponse(Boolean success, SystemListData data) {
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

    public SystemListData getData() {
        return data;
    }

    public void setData(SystemListData data) {
        this.data = data;
    }

    /**
     * 系统列表数据对象
     */
    public static class SystemListData {
        
        /**
         * 系统信息列表
         */
        @JsonProperty("items")
        private List<SystemInfo> items;
        
        /**
         * 总数
         */
        @JsonProperty("total")
        private Integer total;

        // 构造函数
        public SystemListData() {
        }

        public SystemListData(List<SystemInfo> items, Integer total) {
            this.items = items;
            this.total = total;
        }

        // Getter and Setter methods
        public List<SystemInfo> getItems() {
            return items;
        }

        public void setItems(List<SystemInfo> items) {
            this.items = items;
        }

        public Integer getTotal() {
            return total;
        }

        public void setTotal(Integer total) {
            this.total = total;
        }
    }
}