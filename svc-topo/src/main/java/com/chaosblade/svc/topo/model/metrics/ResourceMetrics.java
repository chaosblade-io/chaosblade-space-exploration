package com.chaosblade.svc.topo.model.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 资源使用指标
 * 包括 CPU、内存、网络、磁盘等资源的使用情况
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceMetrics {

    // CPU 指标
    @JsonProperty("cpuUsagePercent")
    private Double cpuUsagePercent;

    @JsonProperty("cpuUsageCores")
    private Double cpuUsageCores;

    @JsonProperty("cpuThrottlingPercent")
    private Double cpuThrottlingPercent;

    // 内存指标
    @JsonProperty("memoryUsageBytes")
    private Long memoryUsageBytes;

    @JsonProperty("memoryUsagePercent")
    private Double memoryUsagePercent;

    @JsonProperty("memoryWorkingSetBytes")
    private Long memoryWorkingSetBytes;

    @JsonProperty("memoryRssBytes")
    private Long memoryRssBytes;

    @JsonProperty("memoryCacheBytes")
    private Long memoryCacheBytes;

    // 网络指标
    @JsonProperty("networkReceiveBytesPerSec")
    private Double networkReceiveBytesPerSec;

    @JsonProperty("networkTransmitBytesPerSec")
    private Double networkTransmitBytesPerSec;

    @JsonProperty("networkReceivePacketsPerSec")
    private Double networkReceivePacketsPerSec;

    @JsonProperty("networkTransmitPacketsPerSec")
    private Double networkTransmitPacketsPerSec;

    @JsonProperty("networkErrorsPerSec")
    private Double networkErrorsPerSec;

    // 磁盘指标
    @JsonProperty("diskUsageBytes")
    private Long diskUsageBytes;

    @JsonProperty("diskUsagePercent")
    private Double diskUsagePercent;

    @JsonProperty("diskReadBytesPerSec")
    private Double diskReadBytesPerSec;

    @JsonProperty("diskWriteBytesPerSec")
    private Double diskWriteBytesPerSec;

    @JsonProperty("diskIoTimePercent")
    private Double diskIoTimePercent;

    // 资源配额
    @JsonProperty("quota")
    private ResourceQuota quota;

    // Getters and Setters
    public Double getCpuUsagePercent() {
        return cpuUsagePercent;
    }

    public void setCpuUsagePercent(Double cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }

    public Double getCpuUsageCores() {
        return cpuUsageCores;
    }

    public void setCpuUsageCores(Double cpuUsageCores) {
        this.cpuUsageCores = cpuUsageCores;
    }

    public Double getCpuThrottlingPercent() {
        return cpuThrottlingPercent;
    }

    public void setCpuThrottlingPercent(Double cpuThrottlingPercent) {
        this.cpuThrottlingPercent = cpuThrottlingPercent;
    }

    public Long getMemoryUsageBytes() {
        return memoryUsageBytes;
    }

    public void setMemoryUsageBytes(Long memoryUsageBytes) {
        this.memoryUsageBytes = memoryUsageBytes;
    }

    public Double getMemoryUsagePercent() {
        return memoryUsagePercent;
    }

    public void setMemoryUsagePercent(Double memoryUsagePercent) {
        this.memoryUsagePercent = memoryUsagePercent;
    }

    public Long getMemoryWorkingSetBytes() {
        return memoryWorkingSetBytes;
    }

    public void setMemoryWorkingSetBytes(Long memoryWorkingSetBytes) {
        this.memoryWorkingSetBytes = memoryWorkingSetBytes;
    }

    public Long getMemoryRssBytes() {
        return memoryRssBytes;
    }

    public void setMemoryRssBytes(Long memoryRssBytes) {
        this.memoryRssBytes = memoryRssBytes;
    }

    public Long getMemoryCacheBytes() {
        return memoryCacheBytes;
    }

    public void setMemoryCacheBytes(Long memoryCacheBytes) {
        this.memoryCacheBytes = memoryCacheBytes;
    }

    public Double getNetworkReceiveBytesPerSec() {
        return networkReceiveBytesPerSec;
    }

    public void setNetworkReceiveBytesPerSec(Double networkReceiveBytesPerSec) {
        this.networkReceiveBytesPerSec = networkReceiveBytesPerSec;
    }

    public Double getNetworkTransmitBytesPerSec() {
        return networkTransmitBytesPerSec;
    }

    public void setNetworkTransmitBytesPerSec(Double networkTransmitBytesPerSec) {
        this.networkTransmitBytesPerSec = networkTransmitBytesPerSec;
    }

    public Double getNetworkReceivePacketsPerSec() {
        return networkReceivePacketsPerSec;
    }

    public void setNetworkReceivePacketsPerSec(Double networkReceivePacketsPerSec) {
        this.networkReceivePacketsPerSec = networkReceivePacketsPerSec;
    }

    public Double getNetworkTransmitPacketsPerSec() {
        return networkTransmitPacketsPerSec;
    }

    public void setNetworkTransmitPacketsPerSec(Double networkTransmitPacketsPerSec) {
        this.networkTransmitPacketsPerSec = networkTransmitPacketsPerSec;
    }

    public Double getNetworkErrorsPerSec() {
        return networkErrorsPerSec;
    }

    public void setNetworkErrorsPerSec(Double networkErrorsPerSec) {
        this.networkErrorsPerSec = networkErrorsPerSec;
    }

    public Long getDiskUsageBytes() {
        return diskUsageBytes;
    }

    public void setDiskUsageBytes(Long diskUsageBytes) {
        this.diskUsageBytes = diskUsageBytes;
    }

    public Double getDiskUsagePercent() {
        return diskUsagePercent;
    }

    public void setDiskUsagePercent(Double diskUsagePercent) {
        this.diskUsagePercent = diskUsagePercent;
    }

    public Double getDiskReadBytesPerSec() {
        return diskReadBytesPerSec;
    }

    public void setDiskReadBytesPerSec(Double diskReadBytesPerSec) {
        this.diskReadBytesPerSec = diskReadBytesPerSec;
    }

    public Double getDiskWriteBytesPerSec() {
        return diskWriteBytesPerSec;
    }

    public void setDiskWriteBytesPerSec(Double diskWriteBytesPerSec) {
        this.diskWriteBytesPerSec = diskWriteBytesPerSec;
    }

    public Double getDiskIoTimePercent() {
        return diskIoTimePercent;
    }

    public void setDiskIoTimePercent(Double diskIoTimePercent) {
        this.diskIoTimePercent = diskIoTimePercent;
    }

    public ResourceQuota getQuota() {
        return quota;
    }

    public void setQuota(ResourceQuota quota) {
        this.quota = quota;
    }

    /**
     * 资源配额
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceQuota {
        @JsonProperty("cpu")
        private String cpu; // 如 "1000m" 或 "2"

        @JsonProperty("memory")
        private String memory; // 如 "1Gi" 或 "512Mi"

        @JsonProperty("storage")
        private String storage; // 如 "10Gi"

        public String getCpu() {
            return cpu;
        }

        public void setCpu(String cpu) {
            this.cpu = cpu;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public String getStorage() {
            return storage;
        }

        public void setStorage(String storage) {
            this.storage = storage;
        }
    }
}

