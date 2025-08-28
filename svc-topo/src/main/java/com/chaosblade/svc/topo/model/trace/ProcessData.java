package com.chaosblade.svc.topo.model.trace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenTelemetry Process数据模型
 * 代表产生trace的进程信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessData {

    /**
     * 服务名称
     */
    @JsonProperty("serviceName")
    private String serviceName;

    /**
     * 标签列表
     */
    @JsonProperty("tags")
    private List<SpanData.Tag> tags;

    public ProcessData() {}

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public List<SpanData.Tag> getTags() {
        return tags;
    }

    public void setTags(List<SpanData.Tag> tags) {
        this.tags = tags;
    }

    /**
     * 获取标签值
     */
    public String getTagValue(String key) {
        if (tags == null) return null;
        return tags.stream()
                .filter(tag -> key.equals(tag.getKey()))
                .findFirst()
                .map(SpanData.Tag::getValue)
                .map(Object::toString)
                .orElse(null);
    }

    /**
     * 获取所有标签的Map
     */
    public Map<String, Object> getTagsAsMap() {
        Map<String, Object> tagMap = new HashMap<>();
        if (tags != null) {
            for (SpanData.Tag tag : tags) {
                tagMap.put(tag.getKey(), tag.getValue());
            }
        }
        return tagMap;
    }

    /**
     * 获取主机名
     */
    public String getHostName() {
        return getTagValue("hostname");
    }

    /**
     * 获取IP地址
     */
    public String getIpAddress() {
        return getTagValue("ip");
    }

    /**
     * 获取Jaeger版本
     */
    public String getJaegerVersion() {
        return getTagValue("jaeger.version");
    }

    /**
     * 获取进程所在的Kubernetes Pod名称
     */
    public String getKubernetesPodName() {
        return getTagValue("k8s.pod.name");
    }

    /**
     * 获取进程所在的Kubernetes命名空间
     */
    public String getKubernetesNamespace() {
        return getTagValue("k8s.namespace.name");
    }

    /**
     * 获取Kubernetes容器名称
     */
    public String getKubernetesContainerName() {
        return getTagValue("k8s.container.name");
    }

    /**
     * 获取telemetry SDK名称
     */
    public String getTelemetrySdkName() {
        return getTagValue("telemetry.sdk.name");
    }

    /**
     * 获取telemetry SDK版本
     */
    public String getTelemetrySdkVersion() {
        return getTagValue("telemetry.sdk.version");
    }

    /**
     * 获取telemetry SDK语言
     */
    public String getTelemetrySdkLanguage() {
        return getTagValue("telemetry.sdk.language");
    }

    /**
     * 获取进程的部署区域
     */
    public String getRegion() {
        // 尝试多种可能的标签
        String region = getTagValue("cloud.region");
        if (region == null) {
            region = getTagValue("region");
        }
        if (region == null) {
            region = getTagValue("deployment.environment");
        }
        return region != null ? region : "default";
    }

    /**
     * 判断是否为Kubernetes环境
     */
    public boolean isKubernetesEnvironment() {
        return getKubernetesPodName() != null || getKubernetesNamespace() != null;
    }

    @Override
    public String toString() {
        return "ProcessData{" +
                "serviceName='" + serviceName + '\'' +
                ", hostname='" + getHostName() + '\'' +
                ", ip='" + getIpAddress() + '\'' +
                ", namespace='" + getKubernetesNamespace() + '\'' +
                ", podName='" + getKubernetesPodName() + '\'' +
                '}';
    }
}
