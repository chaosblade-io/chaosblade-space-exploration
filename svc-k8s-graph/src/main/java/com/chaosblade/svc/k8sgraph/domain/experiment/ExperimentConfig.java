package com.chaosblade.svc.k8sgraph.domain.experiment;

import java.util.ArrayList;
import java.util.List;

/**
 * ChaosBlade Box 演练实验配置
 * 
 * 完全按照ChaosBlade Box的JSON结构设计，可直接序列化后提交执行
 */
public class ExperimentConfig {
    
    /** 实验名称 */
    private String name;
    
    /** 实验描述 */
    private String description;
    
    /** 实验命名空间（固定值：default） */
    private String namespace = "default";
    
    /** 语言（固定值：zh） */
    private String Lang = "zh";
    
    /** 命名空间（固定值：default） */
    private String Namespace = "default";
    
    /** 实验定义 */
    private ExperimentDefinition definition;
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public String getLang() { return Lang; }
    public void setLang(String lang) { this.Lang = lang; }
    
    public String getChaosNamespace() { return Namespace; }
    public void setChaosNamespace(String namespace) { this.Namespace = namespace; }
    
    public ExperimentDefinition getDefinition() { return definition; }
    public void setDefinition(ExperimentDefinition definition) { this.definition = definition; }
    
    /**
     * 实验定义
     */
    public static class ExperimentDefinition {
        /** 运行模式（固定值：SEQUENCE） */
        private String runMode = "SEQUENCE";
        
        /** 实验时长（秒） */
        private int duration;
        
        /** 流程组列表 */
        private List<FlowGroup> flowGroups = new ArrayList<>();
        
        public String getRunMode() { return runMode; }
        public void setRunMode(String runMode) { this.runMode = runMode; }
        
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        
        public List<FlowGroup> getFlowGroups() { return flowGroups; }
        public void setFlowGroups(List<FlowGroup> flowGroups) { this.flowGroups = flowGroups; }
    }
    
    /**
     * 流程组
     */
    public static class FlowGroup {
        /** 组名称 */
        private String groupName;
        
        /** 范围类型（固定值：2 - 容器级别） */
        private int scopeType = 2;
        
        /** 操作系统类型（固定值：0 - Linux） */
        private int osType = 0;
        
        /** 应用类型（固定值：1 - K8s应用） */
        private int appType = 1;
        
        /** 应用名称 */
        private String appName;
        
        /** 应用ID */
        private String appId;
        
        /** 应用分组列表 */
        private List<String> appGroups = new ArrayList<>();
        
        /** 显示顺序（固定值：1） */
        private int displayIndex = 1;
        
        /** 主机/Pod列表 */
        private List<ExperimentHost> hosts = new ArrayList<>();
        
        /** 流程列表 */
        private List<Flow> flows = new ArrayList<>();
        
        // Getters and Setters
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        
        public int getScopeType() { return scopeType; }
        public void setScopeType(int scopeType) { this.scopeType = scopeType; }
        
        public int getOsType() { return osType; }
        public void setOsType(int osType) { this.osType = osType; }
        
        public int getAppType() { return appType; }
        public void setAppType(int appType) { this.appType = appType; }
        
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        
        public List<String> getAppGroups() { return appGroups; }
        public void setAppGroups(List<String> appGroups) { this.appGroups = appGroups; }
        
        public int getDisplayIndex() { return displayIndex; }
        public void setDisplayIndex(int displayIndex) { this.displayIndex = displayIndex; }
        
        public List<ExperimentHost> getHosts() { return hosts; }
        public void setHosts(List<ExperimentHost> hosts) { this.hosts = hosts; }
        
        public List<Flow> getFlows() { return flows; }
        public void setFlows(List<Flow> flows) { this.flows = flows; }
    }
}

