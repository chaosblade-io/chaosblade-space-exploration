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
 * 图节点模型 基于topo_schema_design.md的Node属性定义
 *
 * <p>从Graph的角度，作为Entity的附加属性容器
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Node {

  /** 节点唯一标识符 */
  @JsonProperty("nodeId")
  private String nodeId;

  /** 实体信息对象 */
  @JsonProperty("entity")
  private Entity entity;

  /** 节点属性对象 */
  @JsonProperty("attrs")
  private NodeAttributes attrs;

  // 构造函数
  public Node() {
    this.attrs = new NodeAttributes();
  }

  public Node(String nodeId, Entity entity) {
    this.nodeId = nodeId;
    this.entity = entity;
    this.attrs = new NodeAttributes();
  }

  // Getter and Setter methods
  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public Entity getEntity() {
    return entity;
  }

  public void setEntity(Entity entity) {
    this.entity = entity;
  }

  public NodeAttributes getAttrs() {
    return attrs;
  }

  public void setAttrs(NodeAttributes attrs) {
    this.attrs = attrs;
  }

  /** 获取实体类型 */
  public EntityType getEntityType() {
    return entity != null ? entity.getType() : null;
  }

  /** 获取显示名称 */
  public String getDisplayName() {
    return entity != null ? entity.getDisplayName() : nodeId;
  }

  /** 设置RED指标 */
  public void setRedMetrics(RedMetrics redMetrics) {
    if (this.attrs == null) {
      this.attrs = new NodeAttributes();
    }
    this.attrs.setRed(redMetrics);
  }

  /** 获取RED指标 */
  public RedMetrics getRedMetrics() {
    return attrs != null ? attrs.getRed() : null;
  }

  @Override
  public String toString() {
    return "Node{" + "nodeId='" + nodeId + '\'' + ", entity=" + entity + ", attrs=" + attrs + '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    Node node = (Node) obj;
    return nodeId != null ? nodeId.equals(node.nodeId) : node.nodeId == null;
  }

  @Override
  public int hashCode() {
    return nodeId != null ? nodeId.hashCode() : 0;
  }

  /** 节点属性类 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class NodeAttributes {

    /** RED指标数据 */
    @JsonProperty("RED")
    private RedMetrics red;

    /** 扩展属性 */
    @JsonProperty("extensions")
    private Map<String, Object> extensions;

    public NodeAttributes() {
      this.extensions = new HashMap<>();
    }

    public RedMetrics getRed() {
      return red;
    }

    public void setRed(RedMetrics red) {
      this.red = red;
    }

    public Map<String, Object> getExtensions() {
      return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
      this.extensions = extensions;
    }

    public void addExtension(String key, Object value) {
      if (this.extensions == null) {
        this.extensions = new HashMap<>();
      }
      this.extensions.put(key, value);
    }

    @Override
    public String toString() {
      return "NodeAttributes{" + "red=" + red + ", extensions=" + extensions + '}';
    }
  }
}
