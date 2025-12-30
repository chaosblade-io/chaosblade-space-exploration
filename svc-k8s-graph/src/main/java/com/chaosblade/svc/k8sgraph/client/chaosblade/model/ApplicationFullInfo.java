package com.chaosblade.svc.k8sgraph.client.chaosblade.model;

import java.util.List;

/**
 * 应用完整信息（包含应用、分组、机器信息）
 */
public class ApplicationFullInfo {
    private ApplicationInfo application;
    private List<String> groups;
    private List<ScopeInfo> scopes;
    
    public ApplicationInfo getApplication() { return application; }
    public void setApplication(ApplicationInfo application) { this.application = application; }
    
    public List<String> getGroups() { return groups; }
    public void setGroups(List<String> groups) { this.groups = groups; }
    
    public List<ScopeInfo> getScopes() { return scopes; }
    public void setScopes(List<ScopeInfo> scopes) { this.scopes = scopes; }
}

