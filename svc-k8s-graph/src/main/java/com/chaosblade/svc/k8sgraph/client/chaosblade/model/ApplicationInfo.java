package com.chaosblade.svc.k8sgraph.client.chaosblade.model;

/**
 * 应用信息
 */
public class ApplicationInfo {
    private String appId;
    private String appName;
    private int appType;
    private int scopeType;
    
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    
    public int getAppType() { return appType; }
    public void setAppType(int appType) { this.appType = appType; }
    
    public int getScopeType() { return scopeType; }
    public void setScopeType(int scopeType) { this.scopeType = scopeType; }
}

