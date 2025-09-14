package com.chaosblade.svc.taskresource.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * API拓扑边实体类
 */
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
    
    // Constructors
    public ApiTopologyEdge() {}

    public ApiTopologyEdge(Long topologyId, Long fromNodeId, Long toNodeId) {
        this.topologyId = topologyId;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
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

    public Long getFromNodeId() {
        return fromNodeId;
    }

    public void setFromNodeId(Long fromNodeId) {
        this.fromNodeId = fromNodeId;
    }

    public Long getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(Long toNodeId) {
        this.toNodeId = toNodeId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
