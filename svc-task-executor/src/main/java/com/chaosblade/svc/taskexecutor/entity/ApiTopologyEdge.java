package com.chaosblade.svc.taskexecutor.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "api_topology_edges")
public class ApiTopologyEdge {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "topology_id", nullable = false)
  private Long topologyId;

  @Column(name = "from_node_id", nullable = false)
  private Long fromNodeId;

  @Column(name = "to_node_id", nullable = false)
  private Long toNodeId;

  @Column(name = "metadata", columnDefinition = "JSON")
  private String metadata;

  public Long getId() {
    return id;
  }

  public Long getTopologyId() {
    return topologyId;
  }

  public Long getFromNodeId() {
    return fromNodeId;
  }

  public Long getToNodeId() {
    return toNodeId;
  }
}
