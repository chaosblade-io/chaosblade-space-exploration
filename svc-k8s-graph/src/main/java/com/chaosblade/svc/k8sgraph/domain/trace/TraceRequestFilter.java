package com.chaosblade.svc.k8sgraph.domain.trace;

/**
 * Coroot Trace 请求过滤条件
 */
public class TraceRequestFilter {
    
    private String field;
    private String op;
    private String value;
    
    public TraceRequestFilter() {
    }
    
    public TraceRequestFilter(String field, String op, String value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }
    
    public String getField() {
        return field;
    }
    
    public void setField(String field) {
        this.field = field;
    }
    
    public String getOp() {
        return op;
    }
    
    public void setOp(String op) {
        this.op = op;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
}

