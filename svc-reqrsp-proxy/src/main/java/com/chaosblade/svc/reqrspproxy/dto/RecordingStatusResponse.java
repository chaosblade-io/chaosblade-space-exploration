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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 录制状态响应
 */
public class RecordingStatusResponse extends RecordingResponse {
    
    private String namespace;
    private String serviceName;
    private Integer appPortOriginal;
    private Integer envoyPort;
    private List<RecordingRule> rules;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer durationSec;
    private Long entryCount;
    private String deploymentStatus;
    private String serviceStatus;
    
    public RecordingStatusResponse() {}
    
    public RecordingStatusResponse(String recordingId, String status) {
        super(recordingId, status);
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
    
    public Integer getAppPortOriginal() {
        return appPortOriginal;
    }
    
    public void setAppPortOriginal(Integer appPortOriginal) {
        this.appPortOriginal = appPortOriginal;
    }
    
    public Integer getEnvoyPort() {
        return envoyPort;
    }
    
    public void setEnvoyPort(Integer envoyPort) {
        this.envoyPort = envoyPort;
    }
    
    public List<RecordingRule> getRules() {
        return rules;
    }
    
    public void setRules(List<RecordingRule> rules) {
        this.rules = rules;
    }
    
    public LocalDateTime getStartAt() {
        return startAt;
    }
    
    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }
    
    public LocalDateTime getEndAt() {
        return endAt;
    }
    
    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }
    
    public Integer getDurationSec() {
        return durationSec;
    }
    
    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }
    
    public Long getEntryCount() {
        return entryCount;
    }
    
    public void setEntryCount(Long entryCount) {
        this.entryCount = entryCount;
    }
    
    public String getDeploymentStatus() {
        return deploymentStatus;
    }
    
    public void setDeploymentStatus(String deploymentStatus) {
        this.deploymentStatus = deploymentStatus;
    }
    
    public String getServiceStatus() {
        return serviceStatus;
    }
    
    public void setServiceStatus(String serviceStatus) {
        this.serviceStatus = serviceStatus;
    }
    
    @Override
    public String toString() {
        return "RecordingStatusResponse{" +
                "recordingId='" + getRecordingId() + '\'' +
                ", status='" + getStatus() + '\'' +
                ", namespace='" + namespace + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", appPortOriginal=" + appPortOriginal +
                ", envoyPort=" + envoyPort +
                ", rules=" + rules +
                ", startAt=" + startAt +
                ", endAt=" + endAt +
                ", durationSec=" + durationSec +
                ", entryCount=" + entryCount +
                ", deploymentStatus='" + deploymentStatus + '\'' +
                ", serviceStatus='" + serviceStatus + '\'' +
                '}';
    }
}
