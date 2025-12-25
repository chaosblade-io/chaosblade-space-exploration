package com.chaosblade.svc.k8sgraph.domain;

/**
 * K8s资源类型定义
 */
public final class ResourceType {
    
    private ResourceType() {}
    
    // 基础设施域 (k8s.infra)
    public static final String NAMESPACE = "k8s.infra.namespace";
    public static final String NODE = "k8s.infra.node";
    public static final String PERSISTENT_VOLUME = "k8s.infra.persistentvolume";
    public static final String STORAGE_CLASS = "k8s.infra.storageclass";
    public static final String CLUSTER_ROLE = "k8s.infra.clusterrole";
    public static final String CLUSTER_ROLE_BINDING = "k8s.infra.clusterrolebinding";
    
    // 工作负载域 (k8s.workload)
    public static final String DEPLOYMENT = "k8s.workload.deployment";
    public static final String REPLICA_SET = "k8s.workload.replicaset";
    public static final String STATEFUL_SET = "k8s.workload.statefulset";
    public static final String DAEMON_SET = "k8s.workload.daemonset";
    public static final String JOB = "k8s.workload.job";
    public static final String CRON_JOB = "k8s.workload.cronjob";
    public static final String POD = "k8s.workload.pod";
    public static final String REPLICATION_CONTROLLER = "k8s.workload.replicationcontroller";
    
    // 网络域 (k8s.network)
    public static final String SERVICE = "k8s.network.service";
    public static final String INGRESS = "k8s.network.ingress";
    public static final String INGRESS_CLASS = "k8s.network.ingressclass";
    public static final String NETWORK_POLICY = "k8s.network.networkpolicy";
    public static final String ENDPOINTS = "k8s.network.endpoints";
    public static final String ENDPOINT_SLICE = "k8s.network.endpointslice";
    
    // 配置与安全域 (k8s.config)
    public static final String CONFIG_MAP = "k8s.config.configmap";
    public static final String SECRET = "k8s.config.secret";
    public static final String PVC = "k8s.config.persistentvolumeclaim";
    public static final String SERVICE_ACCOUNT = "k8s.config.serviceaccount";
    public static final String ROLE = "k8s.config.role";
    public static final String ROLE_BINDING = "k8s.config.rolebinding";
    public static final String LIMIT_RANGE = "k8s.config.limitrange";
    public static final String RESOURCE_QUOTA = "k8s.config.resourcequota";
    public static final String HPA = "k8s.config.horizontalpodautoscaler";
    
    /**
     * 从资源类型获取所属领域
     */
    public static String getDomain(String resourceType) {
        if (resourceType == null) return null;
        int lastDot = resourceType.lastIndexOf('.');
        if (lastDot > 0) {
            return resourceType.substring(0, lastDot);
        }
        return null;
    }
}

