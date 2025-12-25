package com.chaosblade.svc.k8sgraph.domain.service;

import com.chaosblade.svc.k8sgraph.domain.GraphNode;

import java.util.List;
import java.util.Map;

/**
 * 服务详情模型 - 包含基本信息、关系、YAML、指标等
 */
public class ServiceDetail {
    
    /** 服务基本信息（来自 K8s Service） */
    private GraphNode serviceNode;
    
    /** 服务名称 */
    private String serviceName;
    
    /** 命名空间 */
    private String namespace;
    
    /** K8s 标签 */
    private Map<String, String> labels;
    
    /** K8s 注解 */
    private Map<String, String> annotations;
    
    /** 服务类型 (ClusterIP, NodePort, LoadBalancer) */
    private String serviceType;
    
    /** Cluster IP */
    private String clusterIP;
    
    /** 端口信息 */
    private List<Map<String, Object>> ports;
    
    /** 服务选择器 */
    private Map<String, String> selector;
    
    /** 关联的 Pod 数量 */
    private Integer podCount;
    
    /** YAML 定义 */
    private String yaml;
    
    /** 健康状态 */
    private String healthStatus;
    
    /** 上游服务（调用者） */
    private List<String> upstreamServices;
    
    /** 下游服务（被调用者） */
    private List<String> downstreamServices;
    
    /** 关联的 K8s 资源 */
    private List<GraphNode> relatedResources;
    
    public ServiceDetail() {}
    
    public ServiceDetail(String serviceName, String namespace) {
        this.serviceName = serviceName;
        this.namespace = namespace;
    }
    
    // Getters and Setters
    public GraphNode getServiceNode() { return serviceNode; }
    public void setServiceNode(GraphNode serviceNode) { this.serviceNode = serviceNode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    public Map<String, String> getAnnotations() { return annotations; }
    public void setAnnotations(Map<String, String> annotations) { this.annotations = annotations; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getClusterIP() { return clusterIP; }
    public void setClusterIP(String clusterIP) { this.clusterIP = clusterIP; }
    public List<Map<String, Object>> getPorts() { return ports; }
    public void setPorts(List<Map<String, Object>> ports) { this.ports = ports; }
    public Map<String, String> getSelector() { return selector; }
    public void setSelector(Map<String, String> selector) { this.selector = selector; }
    public Integer getPodCount() { return podCount; }
    public void setPodCount(Integer podCount) { this.podCount = podCount; }
    public String getYaml() { return yaml; }
    public void setYaml(String yaml) { this.yaml = yaml; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public List<String> getUpstreamServices() { return upstreamServices; }
    public void setUpstreamServices(List<String> upstreamServices) { this.upstreamServices = upstreamServices; }
    public List<String> getDownstreamServices() { return downstreamServices; }
    public void setDownstreamServices(List<String> downstreamServices) { this.downstreamServices = downstreamServices; }
    public List<GraphNode> getRelatedResources() { return relatedResources; }
    public void setRelatedResources(List<GraphNode> relatedResources) { this.relatedResources = relatedResources; }
}

