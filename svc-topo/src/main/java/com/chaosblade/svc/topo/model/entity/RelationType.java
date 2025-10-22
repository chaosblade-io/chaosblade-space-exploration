/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.topo.model.entity;

/**
 * 关系类型枚举
 * 基于topo_schema_design.md的实体间关系定义
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
    INVOKES("INVOKES", "invokes");

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
                return 10; // 强关系
            case DEPENDS_ON:
            case INVOKES:
                return 5;  // 中等关系
            case RUNS_ON:
                return 1;  // 弱关系
            default:
                return 1;
        }
    }

    /**
     * 判断是否为层级关系（父子关系）
     */
    public boolean isHierarchical() {
        return this == CONTAINS ;
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
        return this == RUNS_ON ;
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
