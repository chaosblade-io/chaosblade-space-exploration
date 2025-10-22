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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用实体基类 基于topo_schema_design.md的Entity定义
 *
 * <p>包含所有实体的共有属性： - 唯一标识符（Entity ID） - 实体类型 (SERVICE, POD, HOST, RPC, NAMESPACE等) - 实体名称 -
 * 命名空间（Kubernetes namespace） - 创建时间与最后更新时间 - 标签和元数据 - 状态信息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Entity {

  /** 实体唯一标识符 */
  @JsonProperty("entityId")
  private String entityId;

  /** 实体类型 (SERVICE, POD, HOST, RPC, NAMESPACE等) */
  @JsonProperty("type")
  private EntityType type;

  /** 实体显示名称 */
  @JsonProperty("displayName")
  private String displayName;

  /** 实体名称（可选，根据实体类型可能有所不同） */
  @JsonProperty("name")
  private String name;

  /** 命名空间（对应 k8s.namespace.name） */
  @JsonProperty("namespace")
  private String namespace;

  /** 应用ID（适用于应用相关实体） */
  @JsonProperty("appId")
  private String appId;

  /** 区域ID */
  @JsonProperty("regionId")
  private String regionId;

  /** 实体首次出现时间戳（毫秒） */
  @JsonProperty("firstSeen")
  private Long firstSeen;

  /** 实体最后出现时间戳（毫秒） */
  @JsonProperty("lastSeen")
  private Long lastSeen;

  /** 扩展属性，用于存储特定实体类型的额外信息 */
  @JsonProperty("attributes")
  private Map<String, Object> attributes;

  // 构造函数
  public Entity() {
    this.attributes = new HashMap<>();
  }

  public Entity(String entityId, EntityType type, String displayName) {
    this();
    this.entityId = entityId;
    this.type = type;
    this.displayName = displayName;
    this.firstSeen = System.currentTimeMillis();
    this.lastSeen = this.firstSeen;
  }

  // Getter and Setter methods
  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public EntityType getType() {
    return type;
  }

  public void setType(EntityType type) {
    this.type = type;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getRegionId() {
    return regionId;
  }

  public void setRegionId(String regionId) {
    this.regionId = regionId;
  }

  public Long getFirstSeen() {
    return firstSeen;
  }

  public void setFirstSeen(Long firstSeen) {
    this.firstSeen = firstSeen;
  }

  public Long getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(Long lastSeen) {
    this.lastSeen = lastSeen;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  /** 添加扩展属性 */
  public void addAttribute(String key, Object value) {
    if (this.attributes == null) {
      this.attributes = new HashMap<>();
    }
    this.attributes.put(key, value);
  }

  /** 更新最后出现时间 */
  public void updateLastSeen() {
    this.lastSeen = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return "Entity{"
        + "entityId='"
        + entityId
        + '\''
        + ", type="
        + type
        + ", displayName='"
        + displayName
        + '\''
        + ", name='"
        + name
        + '\''
        + ", namespace='"
        + namespace
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    Entity entity = (Entity) obj;
    return entityId != null ? entityId.equals(entity.entityId) : entity.entityId == null;
  }

  @Override
  public int hashCode() {
    return entityId != null ? entityId.hashCode() : 0;
  }
}
