package com.chaosblade.svc.k8sgraph.service.risk;

/**
 * Pipeline执行日志回调接口
 * 
 * 用于在Pipeline执行过程中记录详细日志到持久层
 */
public interface PipelineLogCallback {
    
    /**
     * 记录INFO级别日志
     */
    void info(int phase, String message);
    
    /**
     * 记录INFO级别日志（带详情）
     */
    void info(int phase, String message, String details);
    
    /**
     * 记录INFO级别日志（带耗时）
     */
    void info(int phase, String message, long durationMs);
    
    /**
     * 记录WARN级别日志
     */
    void warn(int phase, String message);
    
    /**
     * 记录ERROR级别日志
     */
    void error(int phase, String message);
    
    /**
     * 记录DEBUG级别日志
     */
    void debug(int phase, String message);
    
    /**
     * 空实现（用于不需要日志回调的场景）
     */
    PipelineLogCallback NOOP = new PipelineLogCallback() {
        @Override public void info(int phase, String message) {}
        @Override public void info(int phase, String message, String details) {}
        @Override public void info(int phase, String message, long durationMs) {}
        @Override public void warn(int phase, String message) {}
        @Override public void error(int phase, String message) {}
        @Override public void debug(int phase, String message) {}
    };
}

