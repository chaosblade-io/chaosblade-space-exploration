package com.chaosblade.svc.k8sgraph.domain.trace;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Coroot Trace 请求查询对象
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceRequestQuery {
    
    private String view;
    
    private List<TraceRequestFilter> filters;
    
    @JsonProperty("trace_id")
    private String traceId;
    
    public TraceRequestQuery() {
    }
    
    public String getView() {
        return view;
    }
    
    public void setView(String view) {
        this.view = view;
    }
    
    public List<TraceRequestFilter> getFilters() {
        return filters;
    }
    
    public void setFilters(List<TraceRequestFilter> filters) {
        this.filters = filters;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}

