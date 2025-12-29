package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

/**
 * 风险规则分类枚举
 */
public enum RiskRuleCategory {
    
    /** 可用性风险 - 副本数、探针、PDB */
    AVAILABILITY("可用性风险", "影响服务可用性的配置问题"),
    
    /** 资源风险 - requests/limits */
    RESOURCE("资源风险", "资源配置不当可能导致资源耗尽或争用"),
    
    /** 稳定性风险 - 镜像、更新策略 */
    STABILITY("稳定性风险", "可能导致服务不稳定的配置"),
    
    /** 数据安全风险 - 存储、数据库 */
    DATA_SECURITY("数据安全风险", "数据持久化和安全相关风险"),
    
    /** 可扩展性风险 - HPA、资源裕度 */
    SCALABILITY("可扩展性风险", "影响服务扩展能力的配置");
    
    private final String displayName;
    private final String description;
    
    RiskRuleCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}

