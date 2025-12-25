package com.chaosblade.svc.k8sgraph.domain.service;

import com.chaosblade.svc.k8sgraph.domain.GraphEdge;
import com.chaosblade.svc.k8sgraph.domain.GraphNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务关系模型 - 包含技术层面和业务层面的关系
 */
public class ServiceRelations {
    
    /** 服务名称 */
    private String serviceName;
    
    /** 命名空间 */
    private String namespace;
    
    /** 服务节点 */
    private GraphNode serviceNode;
    
    // ========== 技术层面关系（K8s 资源） ==========
    
    /** 关联的 Pod 列表 */
    private List<GraphNode> pods;
    
    /** 关联的 Deployment/StatefulSet/DaemonSet */
    private List<GraphNode> workloads;
    
    /** 关联的 Ingress */
    private List<GraphNode> ingresses;
    
    /** 关联的 ConfigMap */
    private List<GraphNode> configMaps;
    
    /** 关联的 Secret */
    private List<GraphNode> secrets;
    
    /** 关联的 Endpoints */
    private List<GraphNode> endpoints;
    
    /** 技术关系边（Service -> Pod, Ingress -> Service 等） */
    private List<GraphEdge> technicalEdges;
    
    // ========== 业务层面关系（Trace 数据） ==========
    
    /** 上游服务（调用本服务的服务） */
    private List<String> upstreamServices;
    
    /** 下游服务（被本服务调用的服务） */
    private List<String> downstreamServices;
    
    /** 业务调用边（服务间调用关系） */
    private List<ServiceMapEdge> businessEdges;
    
    public ServiceRelations() {
        this.pods = new ArrayList<>();
        this.workloads = new ArrayList<>();
        this.ingresses = new ArrayList<>();
        this.configMaps = new ArrayList<>();
        this.secrets = new ArrayList<>();
        this.endpoints = new ArrayList<>();
        this.technicalEdges = new ArrayList<>();
        this.upstreamServices = new ArrayList<>();
        this.downstreamServices = new ArrayList<>();
        this.businessEdges = new ArrayList<>();
    }
    
    public ServiceRelations(String serviceName, String namespace) {
        this();
        this.serviceName = serviceName;
        this.namespace = namespace;
    }
    
    // Getters and Setters
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public GraphNode getServiceNode() { return serviceNode; }
    public void setServiceNode(GraphNode serviceNode) { this.serviceNode = serviceNode; }
    public List<GraphNode> getPods() { return pods; }
    public void setPods(List<GraphNode> pods) { this.pods = pods; }
    public List<GraphNode> getWorkloads() { return workloads; }
    public void setWorkloads(List<GraphNode> workloads) { this.workloads = workloads; }
    public List<GraphNode> getIngresses() { return ingresses; }
    public void setIngresses(List<GraphNode> ingresses) { this.ingresses = ingresses; }
    public List<GraphNode> getConfigMaps() { return configMaps; }
    public void setConfigMaps(List<GraphNode> configMaps) { this.configMaps = configMaps; }
    public List<GraphNode> getSecrets() { return secrets; }
    public void setSecrets(List<GraphNode> secrets) { this.secrets = secrets; }
    public List<GraphNode> getEndpoints() { return endpoints; }
    public void setEndpoints(List<GraphNode> endpoints) { this.endpoints = endpoints; }
    public List<GraphEdge> getTechnicalEdges() { return technicalEdges; }
    public void setTechnicalEdges(List<GraphEdge> technicalEdges) { this.technicalEdges = technicalEdges; }
    public List<String> getUpstreamServices() { return upstreamServices; }
    public void setUpstreamServices(List<String> upstreamServices) { this.upstreamServices = upstreamServices; }
    public List<String> getDownstreamServices() { return downstreamServices; }
    public void setDownstreamServices(List<String> downstreamServices) { this.downstreamServices = downstreamServices; }
    public List<ServiceMapEdge> getBusinessEdges() { return businessEdges; }
    public void setBusinessEdges(List<ServiceMapEdge> businessEdges) { this.businessEdges = businessEdges; }
    
    public void addPod(GraphNode pod) { this.pods.add(pod); }
    public void addWorkload(GraphNode workload) { this.workloads.add(workload); }
    public void addIngress(GraphNode ingress) { this.ingresses.add(ingress); }
    public void addConfigMap(GraphNode configMap) { this.configMaps.add(configMap); }
    public void addSecret(GraphNode secret) { this.secrets.add(secret); }
    public void addTechnicalEdge(GraphEdge edge) { this.technicalEdges.add(edge); }
    public void addBusinessEdge(ServiceMapEdge edge) { this.businessEdges.add(edge); }
}

