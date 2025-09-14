package com.chaosblade.svc.reqrspproxy.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 认证服务 - 处理用户登录和token管理
 */
@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    
    private static final String LOGIN_URL = "http://1.94.151.57:32677/api/v1/users/login";
    private static final String USERNAME = "fdse_microservice";
    private static final String PASSWORD = "111111";
    private static final String VERIFICATION_CODE = "1234";
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantLock lock = new ReentrantLock();
    
    // Token缓存
    private String cachedToken;
    private LocalDateTime tokenExpireTime;
    
    /**
     * 登录请求体
     */
    public static class LoginRequest {
        private String username;
        private String password;
        private String verificationCode;
        
        public LoginRequest(String username, String password, String verificationCode) {
            this.username = username;
            this.password = password;
            this.verificationCode = verificationCode;
        }
        
        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getVerificationCode() { return verificationCode; }
        public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }
    }
    
    /**
     * 登录响应体
     */
    public static class LoginResponse {
        private int status;
        private String msg;
        private LoginData data;
        
        // Getters and setters
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
        public LoginData getData() { return data; }
        public void setData(LoginData data) { this.data = data; }
    }
    
    public static class LoginData {
        private String userId;
        private String username;
        private String token;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
    
    /**
     * 获取有效的认证token
     * 如果token不存在或即将过期，会自动重新登录
     */
    public String getValidToken() {
        lock.lock();
        try {
            // 检查token是否存在且未过期（提前5分钟刷新）
            if (cachedToken != null && tokenExpireTime != null && 
                LocalDateTime.now().isBefore(tokenExpireTime.minus(5, ChronoUnit.MINUTES))) {
                logger.debug("Using cached token");
                return cachedToken;
            }
            
            // 重新登录获取token
            logger.info("Token expired or not exists, performing login...");
            return performLogin();
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 执行登录操作
     */
    private String performLogin() {
        try {
            // 构建登录请求
            LoginRequest loginRequest = new LoginRequest(USERNAME, PASSWORD, VERIFICATION_CODE);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);
            
            logger.info("Sending login request to: {}", LOGIN_URL);
            
            // 发送登录请求
            ResponseEntity<LoginResponse> response = restTemplate.exchange(
                    LOGIN_URL,
                    HttpMethod.POST,
                    request,
                    LoginResponse.class
            );
            
            LoginResponse loginResponse = response.getBody();
            
            if (loginResponse == null) {
                throw new RuntimeException("Login response is null");
            }
            
            if (loginResponse.getStatus() != 1) {
                throw new RuntimeException("Login failed: " + loginResponse.getMsg());
            }
            
            if (loginResponse.getData() == null || loginResponse.getData().getToken() == null) {
                throw new RuntimeException("Login response does not contain token");
            }
            
            // 缓存token和过期时间（假设token有效期为1小时）
            cachedToken = loginResponse.getData().getToken();
            tokenExpireTime = LocalDateTime.now().plus(1, ChronoUnit.HOURS);
            
            logger.info("Login successful, token cached. Username: {}, UserId: {}", 
                    loginResponse.getData().getUsername(), 
                    loginResponse.getData().getUserId());
            
            return cachedToken;
            
        } catch (Exception e) {
            logger.error("Login failed: {}", e.getMessage(), e);
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建带有认证头的HttpHeaders
     */
    public HttpHeaders createAuthenticatedHeaders() {
        String token = getValidToken();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        
        return headers;
    }
    
    /**
     * 清除缓存的token（用于强制重新登录）
     */
    public void clearToken() {
        lock.lock();
        try {
            cachedToken = null;
            tokenExpireTime = null;
            logger.info("Token cache cleared");
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 检查token是否有效
     */
    public boolean isTokenValid() {
        return cachedToken != null && tokenExpireTime != null && 
               LocalDateTime.now().isBefore(tokenExpireTime);
    }
    
    /**
     * 获取当前缓存的token（不触发登录）
     */
    public String getCachedToken() {
        return cachedToken;
    }
}
