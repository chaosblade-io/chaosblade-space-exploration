package com.chaosblade.svc.topo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Jaeger 配置类
 */
@Configuration
@ConfigurationProperties(prefix = "jaeger")
public class JaegerConfig {
    
    private Test test = new Test();
    
    public static class Test {
        private String host;
        private int port;
        private String serviceName;
        private String operationName;
        
        // Getters and Setters
        public String getHost() {
            return host;
        }
        
        public void setHost(String host) {
            this.host = host;
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
    }
    
    public Test getTest() {
        return test;
    }
    
    public void setTest(Test test) {
        this.test = test;
    }
}