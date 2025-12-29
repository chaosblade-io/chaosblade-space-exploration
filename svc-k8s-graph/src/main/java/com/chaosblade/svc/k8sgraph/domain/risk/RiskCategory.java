package com.chaosblade.svc.k8sgraph.domain.risk;

/**
 * 风险分类枚举
 */
public enum RiskCategory {
    /** 单点故障 - 缺少高可用配置 */
    SINGLE_POINT_FAILURE("单点故障", "资源缺少冗余配置，存在单点故障风险"),
    
    /** 资源瓶颈 - 资源配置不足 */
    RESOURCE_BOTTLENECK("资源瓶颈", "资源限制设置不当，可能导致性能问题"),
    
    /** 依赖风险 - 过度依赖其他服务 */
    DEPENDENCY_RISK("依赖风险", "服务依赖关系过于复杂或存在循环依赖"),
    
    /** 性能退化 - 潜在的性能问题 */
    PERFORMANCE_DEGRADATION("性能退化", "存在可能导致性能下降的配置或模式"),
    
    /** 可用性风险 - 影响系统可用性 */
    AVAILABILITY_RISK("可用性风险", "配置可能影响系统整体可用性");
    
    private final String displayName;
    private final String description;
    
    RiskCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}

