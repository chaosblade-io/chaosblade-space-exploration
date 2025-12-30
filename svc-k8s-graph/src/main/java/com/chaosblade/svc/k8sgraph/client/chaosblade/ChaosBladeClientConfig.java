package com.chaosblade.svc.k8sgraph.client.chaosblade;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ChaosBlade Box客户端配置
 */
@Configuration
@ConfigurationProperties(prefix = "chaosblade.box")
public class ChaosBladeClientConfig {
    
    /** ChaosBlade Box服务地址 */
    private String baseUrl = "http://1.94.151.57:7001";
    
    /** 登录用户名 */
    private String username = "root";
    
    /** 登录密码 */
    private String password = "1234";
    
    /** 默认命名空间 */
    private String namespace = "default";
    
    /** Cookie过期时间（分钟） */
    private int cookieExpireMinutes = 10;
    
    /** 请求超时时间（秒） */
    private int requestTimeoutSeconds = 30;
    
    // Getters and Setters
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public int getCookieExpireMinutes() { return cookieExpireMinutes; }
    public void setCookieExpireMinutes(int cookieExpireMinutes) { this.cookieExpireMinutes = cookieExpireMinutes; }
    
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
}

