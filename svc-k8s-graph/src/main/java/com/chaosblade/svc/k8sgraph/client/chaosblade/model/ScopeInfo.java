package com.chaosblade.svc.k8sgraph.client.chaosblade.model;

/**
 * 机器/Pod范围信息
 *
 * 包含ChaosBlade Box GetScopesByApplication接口返回的完整字段
 */
public class ScopeInfo {
    private String deviceId;
    private String deviceName;
    private String ip;
    private String privateIp;
    private String appId;
    private String clusterId;
    private String clusterName;
    private String kubNamespace;
    private String nodeGroup;

    /** 应用配置ID */
    private String appConfigurationId;

    /** 设备配置ID */
    private String deviceConfigurationId;

    /** Agent端口 */
    private int port;

    /** VPC ID */
    private String vpcId;

    /** 应用名称 */
    private String appName;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getPrivateIp() { return privateIp; }
    public void setPrivateIp(String privateIp) { this.privateIp = privateIp; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }

    public String getKubNamespace() { return kubNamespace; }
    public void setKubNamespace(String kubNamespace) { this.kubNamespace = kubNamespace; }

    public String getNodeGroup() { return nodeGroup; }
    public void setNodeGroup(String nodeGroup) { this.nodeGroup = nodeGroup; }

    public String getAppConfigurationId() { return appConfigurationId; }
    public void setAppConfigurationId(String appConfigurationId) { this.appConfigurationId = appConfigurationId; }

    public String getDeviceConfigurationId() { return deviceConfigurationId; }
    public void setDeviceConfigurationId(String deviceConfigurationId) { this.deviceConfigurationId = deviceConfigurationId; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getVpcId() { return vpcId; }
    public void setVpcId(String vpcId) { this.vpcId = vpcId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}

