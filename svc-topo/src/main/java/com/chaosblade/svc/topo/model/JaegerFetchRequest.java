package com.chaosblade.svc.topo.model;

/**
 * Jaeger查询请求数据模型
 */
public class JaegerFetchRequest {
    
    private String jaegerHost;
    private int port = 16685;
    private String serviceName;
    private String operationName;
    private long startTime;
    private long endTime;
    
    public JaegerFetchRequest() {
    }
    
    public String getJaegerHost() {
        return jaegerHost;
    }
    
    public void setJaegerHost(String jaegerHost) {
        this.jaegerHost = jaegerHost;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getOperationName() {
        return operationName;
    }
    
    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
}