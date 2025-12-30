package com.chaosblade.svc.k8sgraph.domain.experiment;

/**
 * 实验目标主机/Pod信息
 */
public class ExperimentHost {
    
    /** 应用名称 */
    private String app;
    
    /** 应用ID */
    private String appId;
    
    /** 应用配置ID */
    private String appConfigurationId;
    
    /** 设备名称（Pod全名） */
    private String deviceName;
    
    /** 设备ID */
    private String deviceId;
    
    /** 设备配置ID */
    private String deviceConfigurationId;
    
    /** Pod IP */
    private String ip;
    
    /** 私有IP */
    private String privateIp;
    
    /** 目标IP */
    private String targetIp;
    
    /** K8s命名空间 */
    private String kubNamespace;
    
    /** 集群名称 */
    private String clusterName;
    
    /** 集群ID */
    private String clusterId;
    
    /** 节点分组 */
    private String nodeGroup;
    
    /** 是否K8s环境（固定值：true） */
    private boolean k8s = true;
    
    /** 范围类型（固定值：2 - 容器级别） */
    private int scopeType = 2;
    
    /** 设备类型（固定值：0 - 容器设备） */
    private int deviceType = 0;
    
    /** 操作系统类型（固定值：0 - Linux） */
    private int osType = 0;
    
    /** Agent端口 */
    private int port;
    
    /** 允许执行（固定值：true） */
    private boolean allow = true;
    
    /** 应用范围（固定值：true） */
    private boolean appScope = true;
    
    /** 是否无效（固定值：false） */
    private boolean invalid = false;
    
    /** 是否主节点（固定值：false） */
    private boolean master = false;
    
    /** 类型（固定值：host） */
    private String type = "host";
    
    /** VPC ID */
    private String vpcId;
    
    // Getters and Setters
    public String getApp() { return app; }
    public void setApp(String app) { this.app = app; }
    
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    
    public String getAppConfigurationId() { return appConfigurationId; }
    public void setAppConfigurationId(String appConfigurationId) { this.appConfigurationId = appConfigurationId; }
    
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getDeviceConfigurationId() { return deviceConfigurationId; }
    public void setDeviceConfigurationId(String deviceConfigurationId) { this.deviceConfigurationId = deviceConfigurationId; }
    
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    
    public String getPrivateIp() { return privateIp; }
    public void setPrivateIp(String privateIp) { this.privateIp = privateIp; }
    
    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }
    
    public String getKubNamespace() { return kubNamespace; }
    public void setKubNamespace(String kubNamespace) { this.kubNamespace = kubNamespace; }
    
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    
    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }
    
    public String getNodeGroup() { return nodeGroup; }
    public void setNodeGroup(String nodeGroup) { this.nodeGroup = nodeGroup; }
    
    public boolean isK8s() { return k8s; }
    public void setK8s(boolean k8s) { this.k8s = k8s; }
    
    public int getScopeType() { return scopeType; }
    public void setScopeType(int scopeType) { this.scopeType = scopeType; }
    
    public int getDeviceType() { return deviceType; }
    public void setDeviceType(int deviceType) { this.deviceType = deviceType; }
    
    public int getOsType() { return osType; }
    public void setOsType(int osType) { this.osType = osType; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public boolean isAllow() { return allow; }
    public void setAllow(boolean allow) { this.allow = allow; }
    
    public boolean isAppScope() { return appScope; }
    public void setAppScope(boolean appScope) { this.appScope = appScope; }
    
    public boolean isInvalid() { return invalid; }
    public void setInvalid(boolean invalid) { this.invalid = invalid; }
    
    public boolean isMaster() { return master; }
    public void setMaster(boolean master) { this.master = master; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getVpcId() { return vpcId; }
    public void setVpcId(String vpcId) { this.vpcId = vpcId; }
}

