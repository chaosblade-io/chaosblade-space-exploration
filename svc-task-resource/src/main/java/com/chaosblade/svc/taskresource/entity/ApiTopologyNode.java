package com.chaosblade.svc.taskresource.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** API拓扑节点实体类 */
@Entity
@Table(name = "api_topology_nodes")
public class ApiTopologyNode {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "topology_id", nullable = false)
  private Long topologyId;

  @Column(name = "node_key", nullable = false, length = 128)
  private String nodeKey;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "layer", nullable = false)
  private Integer layer = 1;

  @Enumerated(EnumType.STRING)
  @Column(name = "protocol", nullable = false)
  private Protocol protocol = Protocol.HTTP;

  @Column(name = "metadata", columnDefinition = "JSON")
  private String metadata;

  // Constructors
  public ApiTopologyNode() {}

  public ApiTopologyNode(
      Long topologyId, String nodeKey, String name, Integer layer, Protocol protocol) {
    this.topologyId = topologyId;
    this.nodeKey = nodeKey;
    this.name = name;
    this.layer = layer;
    this.protocol = protocol;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getTopologyId() {
    return topologyId;
  }

  public void setTopologyId(Long topologyId) {
    this.topologyId = topologyId;
  }

  public String getNodeKey() {
    return nodeKey;
  }

  public void setNodeKey(String nodeKey) {
    this.nodeKey = nodeKey;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getLayer() {
    return layer;
  }

  public void setLayer(Integer layer) {
    this.layer = layer;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  public void setProtocol(Protocol protocol) {
    this.protocol = protocol;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }
}
