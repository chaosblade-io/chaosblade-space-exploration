package com.chaosblade.svc.taskresource.dto;

import com.chaosblade.svc.taskresource.entity.ApiTopology;
import com.chaosblade.svc.taskresource.entity.ApiTopologyNode;
import com.chaosblade.svc.taskresource.entity.ApiTopologyEdge;

import java.util.List;

/**
 * 完整拓扑信息DTO
 */
public class CompleteTopologyDto {
    
    private ApiTopology topology;
    private List<ApiTopologyNode> nodes;
    private List<ApiTopologyEdge> edges;
    
    // Constructors
    public CompleteTopologyDto() {}
    
    public CompleteTopologyDto(ApiTopology topology, List<ApiTopologyNode> nodes, List<ApiTopologyEdge> edges) {
        this.topology = topology;
        this.nodes = nodes;
        this.edges = edges;
    }
    
    // Getters and Setters
    public ApiTopology getTopology() {
        return topology;
    }
    
    public void setTopology(ApiTopology topology) {
        this.topology = topology;
    }
    
    public List<ApiTopologyNode> getNodes() {
        return nodes;
    }
    
    public void setNodes(List<ApiTopologyNode> nodes) {
        this.nodes = nodes;
    }
    
    public List<ApiTopologyEdge> getEdges() {
        return edges;
    }
    
    public void setEdges(List<ApiTopologyEdge> edges) {
        this.edges = edges;
    }
}
