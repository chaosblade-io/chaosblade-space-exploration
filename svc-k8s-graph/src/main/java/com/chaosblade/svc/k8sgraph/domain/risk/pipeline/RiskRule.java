package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.util.List;

/**
 * 风险规则定义
 * 
 * 每条规则描述一种K8s配置层面的风险检测逻辑
 */
public class RiskRule {
    
    /** 规则ID，如 "AVAIL_001" */
    private String ruleId;
    
    /** 规则名称，如 "单副本部署" */
    private String name;
    
    /** 规则描述 */
    private String description;
    
    /** 风险分类 */
    private RiskRuleCategory category;
    
    /** 基础分值 */
    private int baseScore;
    
    /** 适用的资源类型，如 "Deployment", "StatefulSet" */
    private List<String> resourceTypes;
    
    /** 检测条件类型: REPLICAS, PROBE, RESOURCE_LIMITS, IMAGE_TAG, PDB, HPA, CUSTOM */
    private String checkType;
    
    /** 检测参数（根据checkType不同而不同） */
    private CheckParams checkParams;
    
    /** 是否为关键基础设施特殊处理（如数据库） */
    private boolean criticalInfra;
    
    /** 关键基础设施的额外加分 */
    private int criticalInfraBonus;
    
    /** 关键基础设施识别关键词 */
    private List<String> criticalKeywords;
    
    /** 严重等级 */
    private String severity;
    
    /** 修复建议 */
    private String remediation;
    
    /** 是否启用 */
    private boolean enabled = true;
    
    public RiskRule() {}
    
    // Getters and Setters
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public RiskRuleCategory getCategory() { return category; }
    public void setCategory(RiskRuleCategory category) { this.category = category; }
    
    public int getBaseScore() { return baseScore; }
    public void setBaseScore(int baseScore) { this.baseScore = baseScore; }
    
    public List<String> getResourceTypes() { return resourceTypes; }
    public void setResourceTypes(List<String> resourceTypes) { this.resourceTypes = resourceTypes; }
    
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    
    public CheckParams getCheckParams() { return checkParams; }
    public void setCheckParams(CheckParams checkParams) { this.checkParams = checkParams; }
    
    public boolean isCriticalInfra() { return criticalInfra; }
    public void setCriticalInfra(boolean criticalInfra) { this.criticalInfra = criticalInfra; }
    
    public int getCriticalInfraBonus() { return criticalInfraBonus; }
    public void setCriticalInfraBonus(int criticalInfraBonus) { this.criticalInfraBonus = criticalInfraBonus; }
    
    public List<String> getCriticalKeywords() { return criticalKeywords; }
    public void setCriticalKeywords(List<String> criticalKeywords) { this.criticalKeywords = criticalKeywords; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getRemediation() { return remediation; }
    public void setRemediation(String remediation) { this.remediation = remediation; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    /**
     * 检测参数
     */
    public static class CheckParams {
        /** 最小副本数阈值 */
        private Integer minReplicas;
        
        /** 探针类型: readiness, liveness, startup */
        private String probeType;
        
        /** 资源类型: requests, limits */
        private String resourceType;
        
        /** 镜像标签黑名单 */
        private List<String> forbiddenTags;
        
        public Integer getMinReplicas() { return minReplicas; }
        public void setMinReplicas(Integer minReplicas) { this.minReplicas = minReplicas; }
        
        public String getProbeType() { return probeType; }
        public void setProbeType(String probeType) { this.probeType = probeType; }
        
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        
        public List<String> getForbiddenTags() { return forbiddenTags; }
        public void setForbiddenTags(List<String> forbiddenTags) { this.forbiddenTags = forbiddenTags; }
    }
}

