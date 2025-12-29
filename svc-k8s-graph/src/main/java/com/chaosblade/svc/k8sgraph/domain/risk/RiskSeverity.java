package com.chaosblade.svc.k8sgraph.domain.risk;

/**
 * 风险严重等级枚举
 */
public enum RiskSeverity {
    /** 严重 - 影响核心业务 */
    CRITICAL(1, "严重", "影响核心业务，需要立即修复"),
    
    /** 高 - 影响重要功能 */
    HIGH(2, "高", "影响重要功能，需要尽快处理"),
    
    /** 中等 - 影响次要功能 */
    MEDIUM(3, "中等", "影响次要功能，应该计划修复"),
    
    /** 低 - 轻微影响 */
    LOW(4, "低", "轻微影响，可以稍后处理");
    
    private final int level;
    private final String displayName;
    private final String description;
    
    RiskSeverity(int level, String displayName, String description) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
    }
    
    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}

