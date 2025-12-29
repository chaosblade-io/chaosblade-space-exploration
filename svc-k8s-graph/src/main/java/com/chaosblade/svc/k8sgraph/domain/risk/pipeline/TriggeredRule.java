package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.util.Map;

/**
 * 触发的风险规则
 * 
 * 表示某条规则在特定资源上被触发
 */
public class TriggeredRule {
    
    /** 规则ID */
    private String ruleId;
    
    /** 规则名称 */
    private String ruleName;
    
    /** 风险分类 */
    private RiskRuleCategory category;
    
    /** 实际得分（可能包含关键基础设施加成） */
    private int score;
    
    /** 基础分值 */
    private int baseScore;
    
    /** 是否应用了关键基础设施加成 */
    private boolean criticalBonus;
    
    /** 触发该规则的资源类型 */
    private String resourceType;
    
    /** 触发该规则的资源名称 */
    private String resourceName;
    
    /** 证据 - 触发规则的具体配置值 */
    private Map<String, Object> evidence;
    
    /** 证据描述 */
    private String evidenceDescription;
    
    /** 严重等级 */
    private String severity;
    
    /** 修复建议 */
    private String remediation;
    
    public TriggeredRule() {}
    
    public static TriggeredRule from(RiskRule rule, String resourceType, String resourceName) {
        TriggeredRule triggered = new TriggeredRule();
        triggered.setRuleId(rule.getRuleId());
        triggered.setRuleName(rule.getName());
        triggered.setCategory(rule.getCategory());
        triggered.setBaseScore(rule.getBaseScore());
        triggered.setScore(rule.getBaseScore());
        triggered.setResourceType(resourceType);
        triggered.setResourceName(resourceName);
        triggered.setSeverity(rule.getSeverity());
        triggered.setRemediation(rule.getRemediation());
        return triggered;
    }
    
    public void applyCriticalBonus(int bonus) {
        this.score = this.baseScore + bonus;
        this.criticalBonus = true;
    }
    
    // Getters and Setters
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    
    public RiskRuleCategory getCategory() { return category; }
    public void setCategory(RiskRuleCategory category) { this.category = category; }
    
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    
    public int getBaseScore() { return baseScore; }
    public void setBaseScore(int baseScore) { this.baseScore = baseScore; }
    
    public boolean isCriticalBonus() { return criticalBonus; }
    public void setCriticalBonus(boolean criticalBonus) { this.criticalBonus = criticalBonus; }
    
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    
    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence; }
    
    public String getEvidenceDescription() { return evidenceDescription; }
    public void setEvidenceDescription(String evidenceDescription) { this.evidenceDescription = evidenceDescription; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getRemediation() { return remediation; }
    public void setRemediation(String remediation) { this.remediation = remediation; }
}

