package com.chaosblade.svc.topo.model.entity;

/**
 * 关系类型枚举
 * 基于PROMETHEUS_INTEGRATION_PLAN.md的实体间关系定义
 */
public enum RelationType {

    /**
     * 包含关系 - 一个实体包含另一个实体
     * 例如：Namespace包含Service，Service包含Pod
     * 例如：RPCGroup包含RPC
     */
    CONTAINS("CONTAINS", "contains"),

    /**
     * 依赖关系 - 一个实体依赖另一个实体
     * 例如：Service依赖其他Service、ExternalService或Middleware
     */
    DEPENDS_ON("DEPENDS_ON", "depends_on"),

    /**
     * 运行关系 - 一个实体运行在另一个实体上
     * 例如：Pod运行在Host上，Instance运行在Host上
     */
    RUNS_ON("RUNS_ON", "runs_on"),

    /**
     * 调用关系 - 一个实体调用另一个实体的接口
     * 例如：Service调用RPC接口
     */
    INVOKES("INVOKES", "invokes"),

    // ========== 新增关系类型 ==========

    /**
     * 归属关系 - 实体归属于某个集群或命名空间
     * 例如：Node → Cluster, Namespace → Cluster
     */
    BELONGS_TO("BELONGS_TO", "belongs_to"),

    /**
     * 托管关系 - 节点托管 Pod
     * 例如：Node → Pod（一个节点托管多个 Pod）
     */
    HOSTS("HOSTS", "hosts"),

    /**
     * 包含关系（容器级别）- Pod 包含 Container
     * 例如：Pod → Container（一个 Pod 包含多个容器）
     */
    INCLUDES("INCLUDES", "includes"),

    /**
     * 暴露关系 - 实体暴露指标
     * 例如：Pod → Metric, Node → Metric, Container → Metric
     */
    EXPOSES("EXPOSES", "exposes"),

    /**
     * 聚合关系 - 指标分组聚合指标
     * 例如：MetricGroup → Metric
     */
    AGGREGATES("AGGREGATES", "aggregates");

    private final String displayName;
    private final String identifier;

    RelationType(String displayName, String identifier) {
        this.displayName = displayName;
        this.identifier = identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * 获取关系的权重（用于图布局算法）
     */
    public int getWeight() {
        switch (this) {
            case CONTAINS:
            case INCLUDES:
                return 10; // 强关系
            case DEPENDS_ON:
            case INVOKES:
                return 5;  // 中等关系
            case RUNS_ON:
            case HOSTS:
            case BELONGS_TO:
                return 3;  // 基础设施关系
            case EXPOSES:
            case AGGREGATES:
                return 1;  // 弱关系
            default:
                return 1;
        }
    }

    /**
     * 判断是否为层级关系（父子关系）
     */
    public boolean isHierarchical() {
        return this == CONTAINS || this == INCLUDES;
    }

    /**
     * 判断是否为依赖关系
     */
    public boolean isDependency() {
        return this == DEPENDS_ON || this == INVOKES;
    }

    /**
     * 判断是否为部署关系
     */
    public boolean isDeployment() {
        return this == RUNS_ON || this == HOSTS;
    }

    /**
     * 判断是否为基础设施关系
     */
    public boolean isInfrastructure() {
        return this == BELONGS_TO || this == HOSTS || this == INCLUDES;
    }

    /**
     * 判断是否为指标关系
     */
    public boolean isMetricRelation() {
        return this == EXPOSES || this == AGGREGATES;
    }

    /**
     * 根据字符串获取关系类型
     */
    public static RelationType fromString(String relationStr) {
        if (relationStr == null) {
            return null;
        }

        try {
            return RelationType.valueOf(relationStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试匹配identifier
            for (RelationType type : RelationType.values()) {
                if (type.identifier.equalsIgnoreCase(relationStr)) {
                    return type;
                }
            }
            // 尝试匹配displayName
            for (RelationType type : RelationType.values()) {
                if (type.displayName.equals(relationStr)) {
                    return type;
                }
            }
            return null;
        }
    }
}
