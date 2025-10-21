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

package com.chaosblade.svc.reqrspproxy.entity;

import com.chaosblade.svc.reqrspproxy.dto.InterceptionRule;
import com.chaosblade.svc.reqrspproxy.dto.RecordingRule;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 录制状态实体
 */
public class RecordingState {
    
    public enum RecordingStatus {
        STARTING,
        RECORDING,
        STOPPING,
        STOPPED,
        ERROR
    }
    
    private String recordingId;
    private String namespace;
    private String serviceName;
    private Integer appPortOriginal;
    private Integer envoyPort;
    private List<RecordingRule> rules;
    private List<InterceptionRule> interceptionRules = new ArrayList<>();
    private RecordingStatus status;
    private Integer durationSec;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime stoppedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime expiresAt;
    
    private String errorMessage;
    private String configMapName;
    private String deploymentName;
    
    public RecordingState() {}
    
    public RecordingState(String recordingId, String namespace, String serviceName) {
        this.recordingId = recordingId;
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.status = RecordingStatus.STARTING;
        this.startedAt = LocalDateTime.now();
    }
    
    public String getRecordingId() {
        return recordingId;
    }
    
    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
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

    public List<InterceptionRule> getInterceptionRules() {
        return interceptionRules;
    }

    public void setInterceptionRules(List<InterceptionRule> interceptionRules) {
        this.interceptionRules = interceptionRules != null ? interceptionRules : new ArrayList<>();
    }

    public RecordingStatus getStatus() {
        return status;
    }

    public void setStatus(RecordingStatus status) {
        this.status = status;
    }
    
    public Integer getDurationSec() {
        return durationSec;
    }
    
    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(LocalDateTime stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    // 保持向后兼容性的方法
    public LocalDateTime getStartAt() {
        return startedAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startedAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return stoppedAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.stoppedAt = endAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getConfigMapName() {
        return configMapName;
    }
    
    public void setConfigMapName(String configMapName) {
        this.configMapName = configMapName;
    }
    
    public String getDeploymentName() {
        return deploymentName;
    }
    
    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }
    
    @Override
    public String toString() {
        return "RecordingState{" +
                "recordingId='" + recordingId + '\'' +
                ", namespace='" + namespace + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", appPortOriginal=" + appPortOriginal +
                ", envoyPort=" + envoyPort +
                ", status=" + status +
                ", startedAt=" + startedAt +
                ", stoppedAt=" + stoppedAt +
                '}';
    }
}
