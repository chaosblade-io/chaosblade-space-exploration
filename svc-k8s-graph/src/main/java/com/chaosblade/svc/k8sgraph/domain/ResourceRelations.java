package com.chaosblade.svc.k8sgraph.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * K8s 资源关系模型 - 通用的资源间技术关系
 * 用于表示任意 K8s 资源与其他资源的连接关系
 */
public class ResourceRelations {
    
    /** 资源类型（如 Service, Pod, Deployment, Node 等） */
    private String resourceType;
    
    /** 资源名称 */
    private String resourceName;
    
    /** 命名空间（某些资源如 Node 没有命名空间） */
    private String namespace;
    
    /** 当前资源节点 */
    private GraphNode resourceNode;
    
    /** 关联的资源节点列表 */
    private List<GraphNode> relatedNodes;
    
    /** 资源间的关系边 */
    private List<GraphEdge> edges;
    
    public ResourceRelations() {
        this.relatedNodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }
    
    public ResourceRelations(String resourceType, String resourceName) {
        this();
        this.resourceType = resourceType;
        this.resourceName = resourceName;
    }
    
    public ResourceRelations(String resourceType, String resourceName, String namespace) {
        this(resourceType, resourceName);
        this.namespace = namespace;
    }
    
    // Getters and Setters
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public GraphNode getResourceNode() { return resourceNode; }
    public void setResourceNode(GraphNode resourceNode) { this.resourceNode = resourceNode; }
    public List<GraphNode> getRelatedNodes() { return relatedNodes; }
    public void setRelatedNodes(List<GraphNode> relatedNodes) { this.relatedNodes = relatedNodes; }
    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> edges) { this.edges = edges; }
    
    public void addRelatedNode(GraphNode node) { this.relatedNodes.add(node); }
    public void addEdge(GraphEdge edge) { this.edges.add(edge); }
}

