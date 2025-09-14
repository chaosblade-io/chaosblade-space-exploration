package com.chaosblade.svc.topo.model.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * 图边模型
 * 基于topo_schema_design.md的Edge属性定义
 *
 * 表示节点之间的关系连接
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Edge {

    /**
     * 边唯一标识符
     */
    @JsonProperty("edgeId")
    private String edgeId;

    /**
     * 源节点ID
     */
    @JsonProperty("from")
    private String from;

    /**
     * 目标节点ID
     */
    @JsonProperty("to")
    private String to;

    /**
     * 边类型（关系类型）
     */
    @JsonProperty("type")
    private RelationType type;

    /**
     * 边首次出现时间戳（毫秒）
     */
    @JsonProperty("firstSeen")
    private Long firstSeen;

    /**
     * 边最后出现时间戳（毫秒）
     */
    @JsonProperty("lastSeen")
    private Long lastSeen;

    /**
     * 边属性对象
     */
    @JsonProperty("attrs")
    private EdgeAttributes attrs;

    // 构造函数
    public Edge() {
        this.attrs = new EdgeAttributes();
        this.firstSeen = System.currentTimeMillis();
        this.lastSeen = this.firstSeen;
    }

    public Edge(String edgeId, String from, String to, RelationType type) {
        this();
        this.edgeId = edgeId;
        this.from = from;
        this.to = to;
        this.type = type;
    }

    // Getter and Setter methods
    public String getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public RelationType getType() {
        return type;
    }

    public void setType(RelationType type) {
        this.type = type;
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

    public EdgeAttributes getAttrs() {
        return attrs;
    }

    public void setAttrs(EdgeAttributes attrs) {
        this.attrs = attrs;
    }

    /**
     * 设置RED指标
     */
    public void setRedMetrics(RedMetrics redMetrics) {
        if (this.attrs == null) {
            this.attrs = new EdgeAttributes();
        }
        this.attrs.setRed(redMetrics);
    }

    /**
     * 获取RED指标
     */
    public RedMetrics getRedMetrics() {
        return attrs != null ? attrs.getRed() : null;
    }

    /**
     * 更新最后出现时间
     */
    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    /**
     * 生成边ID（如果没有设置）
     */
    public void generateEdgeId() {
        if (this.edgeId == null) {
            this.edgeId = from + "-" + to + "-" + type.name();
        }
    }

    @Override
    public String toString() {
        return "Edge{" +
                "edgeId='" + edgeId + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Edge edge = (Edge) obj;
        return edgeId != null ? edgeId.equals(edge.edgeId) : edge.edgeId == null;
    }

    @Override
    public int hashCode() {
        return edgeId != null ? edgeId.hashCode() : 0;
    }

    /**
     * 边属性类
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EdgeAttributes {

        /**
         * RED指标数据
         */
        @JsonProperty("RED")
        private RedMetrics red;

        /**
         * 扩展属性
         */
        @JsonProperty("extensions")
        private Map<String, Object> extensions;

        public EdgeAttributes() {
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
            return "EdgeAttributes{" +
                    "red=" + red +
                    ", extensions=" + extensions +
                    '}';
        }
    }
}
