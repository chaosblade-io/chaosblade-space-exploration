package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 服务风险画像
 * 
 * Phase 1的输出：以服务为单位的风险扫描结果
 */
public class ServiceRiskProfile {
    
    /** 服务名称 */
    private String serviceName;
    
    /** 命名空间 */
    private String namespace;
    
    /** 总风险分 */
    private int totalScore;
    
    /** 各分类的风险分数 */
    private Map<RiskRuleCategory, Integer> categoryScores;
    
    /** 触发的规则列表 */
    private List<TriggeredRule> triggeredRules;
    
    /** 关联的K8s资源列表 */
    private List<RelatedResource> relatedResources;
    
    /** 是否为关键基础设施 */
    private boolean criticalInfra;
    
    /** 关键基础设施类型（如 database, mq, cache） */
    private String infraType;
    
    /** 扫描时间 */
    private String scanTime;
    
    /** 规则检查统计 */
    private ScanStats scanStats;
    
    public ServiceRiskProfile() {
        this.categoryScores = new EnumMap<>(RiskRuleCategory.class);
        this.triggeredRules = new ArrayList<>();
        this.relatedResources = new ArrayList<>();
        this.scanTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.scanStats = new ScanStats();
    }
    
    public ServiceRiskProfile(String serviceName, String namespace) {
        this();
        this.serviceName = serviceName;
        this.namespace = namespace;
    }
    
    /**
     * 添加触发的规则
     */
    public void addTriggeredRule(TriggeredRule rule) {
        this.triggeredRules.add(rule);
        this.totalScore += rule.getScore();
        this.categoryScores.merge(rule.getCategory(), rule.getScore(), Integer::sum);
    }
    
    /**
     * 添加关联资源
     */
    public void addRelatedResource(RelatedResource resource) {
        this.relatedResources.add(resource);
    }
    
    /**
     * 获取风险等级
     */
    public String getRiskLevel() {
        if (totalScore >= 100) return "CRITICAL";
        if (totalScore >= 60) return "HIGH";
        if (totalScore >= 30) return "MEDIUM";
        if (totalScore > 0) return "LOW";
        return "NONE";
    }
    
    // Getters and Setters
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    
    public Map<RiskRuleCategory, Integer> getCategoryScores() { return categoryScores; }
    public void setCategoryScores(Map<RiskRuleCategory, Integer> categoryScores) { this.categoryScores = categoryScores; }
    
    public List<TriggeredRule> getTriggeredRules() { return triggeredRules; }
    public void setTriggeredRules(List<TriggeredRule> triggeredRules) { this.triggeredRules = triggeredRules; }
    
    public List<RelatedResource> getRelatedResources() { return relatedResources; }
    public void setRelatedResources(List<RelatedResource> relatedResources) { this.relatedResources = relatedResources; }
    
    public boolean isCriticalInfra() { return criticalInfra; }
    public void setCriticalInfra(boolean criticalInfra) { this.criticalInfra = criticalInfra; }
    
    public String getInfraType() { return infraType; }
    public void setInfraType(String infraType) { this.infraType = infraType; }
    
    public String getScanTime() { return scanTime; }
    public void setScanTime(String scanTime) { this.scanTime = scanTime; }
    
    public ScanStats getScanStats() { return scanStats; }
    public void setScanStats(ScanStats scanStats) { this.scanStats = scanStats; }
    
    /**
     * 关联的K8s资源
     */
    public static class RelatedResource {
        private String resourceType;
        private String resourceName;
        private String status;
        
        public RelatedResource() {}
        public RelatedResource(String resourceType, String resourceName) {
            this.resourceType = resourceType;
            this.resourceName = resourceName;
        }
        
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String getResourceName() { return resourceName; }
        public void setResourceName(String resourceName) { this.resourceName = resourceName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    /**
     * 扫描统计
     */
    public static class ScanStats {
        private int totalRulesChecked;
        private int rulesTriggered;
        private int resourcesScanned;
        
        public int getTotalRulesChecked() { return totalRulesChecked; }
        public void setTotalRulesChecked(int totalRulesChecked) { this.totalRulesChecked = totalRulesChecked; }
        public int getRulesTriggered() { return rulesTriggered; }
        public void setRulesTriggered(int rulesTriggered) { this.rulesTriggered = rulesTriggered; }
        public int getResourcesScanned() { return resourcesScanned; }
        public void setResourcesScanned(int resourcesScanned) { this.resourcesScanned = resourcesScanned; }
    }
}

