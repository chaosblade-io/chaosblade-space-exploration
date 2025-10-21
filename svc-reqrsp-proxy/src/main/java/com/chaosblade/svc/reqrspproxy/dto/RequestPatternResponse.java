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

package com.chaosblade.svc.reqrspproxy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 请求模式获取响应
 */
public class RequestPatternResponse {

    private String taskId;
    private String status;
    private String message;
    private Long reqDefId;
    private String namespace;
    private List<String> serviceList;
    private List<ServiceRequestPattern> requestPatterns;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer totalRecordedRequests;
    private Integer analyzedServices;
    private String recordingId;

    // 执行ID，便于在异步流程中持久化
    @com.fasterxml.jackson.annotation.JsonProperty("execution_id")
    private Long executionId;

    public RequestPatternResponse() {}

    public RequestPatternResponse(String taskId, String status, String message) {
        this.taskId = taskId;
        this.status = status;
        this.message = message;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getReqDefId() {
        return reqDefId;
    }

    public void setReqDefId(Long reqDefId) {
        this.reqDefId = reqDefId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public List<String> getServiceList() {
        return serviceList;
    }

    public void setServiceList(List<String> serviceList) {
        this.serviceList = serviceList;
    }

    public List<ServiceRequestPattern> getRequestPatterns() {
        return requestPatterns;
    }

    public void setRequestPatterns(List<ServiceRequestPattern> requestPatterns) {
        this.requestPatterns = requestPatterns;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getTotalRecordedRequests() {
        return totalRecordedRequests;
    }

    public void setTotalRecordedRequests(Integer totalRecordedRequests) {
        this.totalRecordedRequests = totalRecordedRequests;
    }

    public Integer getAnalyzedServices() {
        return analyzedServices;
    }

    public void setAnalyzedServices(Integer analyzedServices) {
        this.analyzedServices = analyzedServices;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    @Override
    public String toString() {
        return "RequestPatternResponse{" +
                "taskId='" + taskId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", reqDefId=" + reqDefId +
                ", namespace='" + namespace + '\'' +
                ", serviceList=" + serviceList +
                ", requestPatterns=" + requestPatterns +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", totalRecordedRequests=" + totalRecordedRequests +
                ", analyzedServices=" + analyzedServices +
                ", recordingId='" + recordingId + '\'' +
                ", executionId=" + executionId +
                '}';
    }
}
