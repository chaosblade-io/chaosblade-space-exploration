package com.chaosblade.svc.topo.model.entity;

/**
 * 实体类型枚举
 * 基于PROMETHEUS_INTEGRATION_PLAN.md的四级实体模型定义
 */
public enum EntityType {

    // ========== 0级实体（集群层） ==========

    /**
     * Kubernetes 集群
     */
    CLUSTER("Cluster"),

    // ========== 1级实体（抽象服务实体） ==========

    /**
     * 命名空间，用于组织和隔离资源
     */
    NAMESPACE("Namespace"),

    /**
     * 服务/应用实体，代表一个可独立运行的服务
     */
    SERVICE("Service"),

    /**
     * 外部服务，表示依赖的外部系统（如第三方API）
     */
    EXTERNAL_SERVICE("ExternalService"),

    /**
     * 中间件实体，包括各种基础组件（MQ、DB等）
     */
    MIDDLEWARE("Middleware"),

    // ========== 2级实体（运行时实例实体） ==========

    /**
     * Kubernetes 节点（原 HOST，重命名）
     */
    NODE("Node"),

    /**
     * Pod 实例
     */
    POD("Pod"),

    /**
     * 容器实例（新增）
     */
    CONTAINER("Container"),

    /**
     * 中间件的具体实例，比如一个DB实例
     */
    INSTANCE("Instance"),

    // ========== 3级实体（接口与调用实体） ==========

    /**
     * 远程过程调用，代表一个具体的服务接口
     */
    RPC("RPC"),

    /**
     * RPC分组，将具有相似性的接口进行分组聚合
     */
    RPC_GROUP("RPCGroup"),

    // ========== 4级实体（指标层） ==========

    /**
     * Prometheus 指标（新增）
     */
    METRIC("Metric"),

    /**
     * 指标分组（新增）
     */
    METRIC_GROUP("MetricGroup");

    private final String displayName;

    EntityType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取实体级别
     * @return 0, 1, 2, 3, 或 4
     */
    public int getLevel() {
        switch (this) {
            case CLUSTER:
                return 0;
            case NAMESPACE:
            case SERVICE:
            case EXTERNAL_SERVICE:
            case MIDDLEWARE:
                return 1;
            case NODE:
            case POD:
            case CONTAINER:
            case INSTANCE:
                return 2;
            case RPC:
            case RPC_GROUP:
                return 3;
            case METRIC:
            case METRIC_GROUP:
                return 4;
            default:
                return -1;
        }
    }

    /**
     * 判断是否为服务类实体
     */
    public boolean isServiceType() {
        return this == SERVICE || this == EXTERNAL_SERVICE || this == MIDDLEWARE;
    }

    /**
     * 判断是否为运行时实例
     */
    public boolean isRuntimeInstance() {
        return this == POD || this == INSTANCE || this == NODE || this == CONTAINER;
    }

    /**
     * 判断是否为接口类实体
     */
    public boolean isInterfaceType() {
        return this == RPC || this == RPC_GROUP;
    }

    /**
     * 判断是否为基础设施实体
     */
    public boolean isInfrastructure() {
        return this == CLUSTER || this == NODE || this == POD || this == CONTAINER;
    }

    /**
     * 判断是否为指标实体
     */
    public boolean isMetric() {
        return this == METRIC || this == METRIC_GROUP;
    }

    /**
     * 根据字符串获取实体类型
     */
    public static EntityType fromString(String typeStr) {
        if (typeStr == null) {
            return null;
        }

        try {
            return EntityType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试匹配displayName
            for (EntityType type : EntityType.values()) {
                if (type.displayName.equalsIgnoreCase(typeStr)) {
                    return type;
                }
            }
            return null;
        }
    }
}
