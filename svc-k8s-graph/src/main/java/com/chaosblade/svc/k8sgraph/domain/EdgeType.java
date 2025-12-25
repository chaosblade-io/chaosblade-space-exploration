package com.chaosblade.svc.k8sgraph.domain;

/**
 * 边类型枚举
 * 描述资源间的依赖和管理关系
 */
public enum EdgeType {
    
    /** 包含关系：Namespace包含其下的所有资源 */
    CONTAINS("contains", "包含"),
    
    /** 拥有关系：资源间的所有权关系，如Deployment拥有ReplicaSet */
    OWNS("owns", "拥有"),
    
    /** 管理关系：如ReplicaSet管理Pod副本 */
    MANAGES("manages", "管理"),
    
    /** 创建关系：如Job创建Pod */
    CREATES("creates", "创建"),
    
    /** 选择关系：通过标签选择器建立的关联，如Service选择Pod */
    SELECTS("selects", "选择"),
    
    /** 路由关系：如Ingress路由到Service */
    ROUTES_TO("routes_to", "路由到"),
    
    /** 运行于关系：如Pod运行在Node上 */
    RUNS_ON("runs_on", "运行于"),
    
    /** 挂载关系：如Pod挂载ConfigMap/Secret/PVC */
    MOUNTS("mounts", "挂载"),
    
    /** 声明关系：如PVC声明PV */
    CLAIMS("claims", "声明"),
    
    /** 调用关系：服务间的调用关系 */
    CALLS("calls", "调用"),
    
    /** 绑定关系：如RoleBinding绑定Role */
    BINDS_TO("binds_to", "绑定到"),
    
    /** 使用关系：如Pod使用ServiceAccount */
    USES("uses", "使用");
    
    private final String value;
    private final String displayName;
    
    EdgeType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }
    
    public String getValue() { return value; }
    public String getDisplayName() { return displayName; }
}

