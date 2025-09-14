package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceTopologyResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("data")
    private ServiceTopologyData data;

    public ServiceTopologyResponse() {}

    public ServiceTopologyResponse(boolean success, ServiceTopologyData data) {
        this.success = success;
        this.data = data;
    }

    // Getter and Setter methods
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ServiceTopologyData getData() {
        return data;
    }

    public void setData(ServiceTopologyData data) {
        this.data = data;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServiceTopologyData {
        
        @JsonProperty("topology")
        private TopologyInfo topology;
        
        @JsonProperty("nodes")
        private List<ServiceNode> nodes;
        
        @JsonProperty("edges")
        private List<ServiceEdge> edges;

        public ServiceTopologyData() {}

        public TopologyInfo getTopology() {
            return topology;
        }

        public void setTopology(TopologyInfo topology) {
            this.topology = topology;
        }

        public List<ServiceNode> getNodes() {
            return nodes;
        }

        public void setNodes(List<ServiceNode> nodes) {
            this.nodes = nodes;
        }

        public List<ServiceEdge> getEdges() {
            return edges;
        }

        public void setEdges(List<ServiceEdge> edges) {
            this.edges = edges;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TopologyInfo {
        
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("systemId")
        private Long systemId;
        
        @JsonProperty("apiId")
        private Long apiId;
        
        @JsonProperty("discoveredAt")
        private String discoveredAt;
        
        @JsonProperty("notes")
        private String notes;
        
        @JsonProperty("createdAt")
        private String createdAt;

        public TopologyInfo() {}

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getSystemId() {
            return systemId;
        }

        public void setSystemId(Long systemId) {
            this.systemId = systemId;
        }

        public Long getApiId() {
            return apiId;
        }

        public void setApiId(Long apiId) {
            this.apiId = apiId;
        }

        public String getDiscoveredAt() {
            return discoveredAt;
        }

        public void setDiscoveredAt(String discoveredAt) {
            this.discoveredAt = discoveredAt;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServiceNode {
        
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("topologyId")
        private Long topologyId;
        
        @JsonProperty("nodeKey")
        private String nodeKey;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("layer")
        private Integer layer;
        
        @JsonProperty("protocol")
        private String protocol;

        public ServiceNode() {}

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

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServiceEdge {
        
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("topologyId")
        private Long topologyId;
        
        @JsonProperty("fromNodeId")
        private Long fromNodeId;
        
        @JsonProperty("toNodeId")
        private Long toNodeId;

        public ServiceEdge() {}

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
    }
}