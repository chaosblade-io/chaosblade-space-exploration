package com.chaosblade.svc.k8sgraph.domain.trace;

import java.util.List;

/**
 * Trace 列表响应模型
 */
public class TraceListResponse {
    
    /** Trace 列表 */
    private List<TraceInfo> traces;
    
    /** 总数 */
    private Integer total;
    
    /** 是否有更多 */
    private Boolean hasMore;
    
    public TraceListResponse() {}
    
    public TraceListResponse(List<TraceInfo> traces) {
        this.traces = traces;
        this.total = traces != null ? traces.size() : 0;
        this.hasMore = false;
    }
    
    public List<TraceInfo> getTraces() { return traces; }
    public void setTraces(List<TraceInfo> traces) { this.traces = traces; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public Boolean getHasMore() { return hasMore; }
    public void setHasMore(Boolean hasMore) { this.hasMore = hasMore; }
}

