package com.chaosblade.svc.k8sgraph.client.chaosblade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

/**
 * ChaosBlade Box API客户端
 * 
 * 封装ChaosBlade Box的HTTP接口调用，自动管理登录Cookie
 */
@Component
public class ChaosBladeClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ChaosBladeClient.class);
    
    @Autowired
    private ChaosBladeClientConfig config;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /** 缓存的Cookie */
    private volatile String cachedCookie;
    
    /** Cookie过期时间 */
    private volatile Instant cookieExpireTime;
    
    /**
     * 登录ChaosBlade Box，获取并缓存Cookie
     */
    public synchronized String login() {
        logger.info("Logging in to ChaosBlade Box: {}", config.getBaseUrl());
        
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("userName", config.getUsername());
            requestBody.put("password", config.getPassword());
            requestBody.put("Namespace", config.getNamespace());
            requestBody.put("NameSpace", config.getNamespace());
            requestBody.put("Lang", "zh");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                config.getBaseUrl() + "/chaos/UserLogin",
                HttpMethod.POST,
                entity,
                String.class
            );
            
            // 从响应头提取Cookie
            List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (cookies != null && !cookies.isEmpty()) {
                for (String cookie : cookies) {
                    if (cookie.startsWith("JSESSIONID=")) {
                        // 提取 JSESSIONID=xxx 部分
                        String sessionId = cookie.split(";")[0];
                        cachedCookie = sessionId;
                        cookieExpireTime = Instant.now().plusSeconds(config.getCookieExpireMinutes() * 60L);
                        logger.info("Login successful, cookie cached until: {}", cookieExpireTime);
                        return cachedCookie;
                    }
                }
            }
            
            throw new RuntimeException("No JSESSIONID cookie found in response");
            
        } catch (Exception e) {
            logger.error("Failed to login to ChaosBlade Box: {}", e.getMessage(), e);
            throw new RuntimeException("ChaosBlade login failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取有效的Cookie，如果过期则自动刷新
     */
    public String getValidCookie() {
        if (cachedCookie == null || cookieExpireTime == null || Instant.now().isAfter(cookieExpireTime)) {
            return login();
        }
        return cachedCookie;
    }
    
    /**
     * 发送带Cookie的POST请求
     */
    public JsonNode post(String path, ObjectNode requestBody) {
        String cookie = getValidCookie();
        
        // 添加默认参数
        requestBody.put("namespace", config.getNamespace());
        requestBody.put("Lang", "zh");
        requestBody.put("Namespace", config.getNamespace());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(HttpHeaders.COOKIE, cookie);
            
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            
            String url = config.getBaseUrl() + path;
            logger.debug("POST {} with cookie", url);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            JsonNode result = objectMapper.readTree(response.getBody());
            
            // 检查响应状态
            if (result.has("success") && !result.get("success").asBoolean()) {
                logger.warn("ChaosBlade API returned error: {}", result);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("ChaosBlade API call failed: {} - {}", path, e.getMessage(), e);
            throw new RuntimeException("ChaosBlade API call failed: " + e.getMessage(), e);
        }
    }
    
    // ==================== 业务接口封装 ====================
    
    /**
     * 获取用户应用列表
     * @param appName 应用名称（模糊搜索）
     */
    public JsonNode getUserApplications(String appName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("filterDisabled", true);
        body.put("appType", 2);
        body.putNull("osType");
        body.put("page", 1);
        body.put("size", 100);
        body.put("key", appName != null ? appName : "");
        return post("/chaos/GetUserApplications", body);
    }

    /**
     * 获取用户应用分组
     * @param appId 应用ID
     */
    public JsonNode getUserApplicationGroups(String appId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("app_id", appId);
        return post("/chaos/GetUserApplicationGroups", body);
    }

    /**
     * 获取应用对应的机器列表
     * @param appId 应用ID
     * @param appGroups 应用分组列表
     */
    public JsonNode getScopesByApplication(String appId, List<String> appGroups) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("size", 100);
        body.put("key", "");
        body.putArray("tags");
        body.putArray("kubNamespaces");
        body.putArray("clusterIds");
        body.put("app_id", appId);

        com.fasterxml.jackson.databind.node.ArrayNode groupsArray = body.putArray("app_group");
        if (appGroups != null) {
            for (String group : appGroups) {
                groupsArray.add(group);
            }
        }

        return post("/chaos/GetScopesByApplication", body);
    }

    /**
     * 获取故障场景分类
     */
    public JsonNode querySceneFunctionCategories() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("phase", 2);
        body.put("scopeType", 2);
        body.put("filterNoChild", true);
        body.put("cloudServiceType", "");
        body.put("osType", 0);
        return post("/chaos/QuerySceneFunctionCategories", body);
    }

    /**
     * 获取故障场景详情
     * @param categoryId 分类ID
     */
    public JsonNode querySceneFunctionByCategoryId(String categoryId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("categoryId", categoryId);
        body.put("phase", 2);
        body.put("scopeType", 2);
        body.put("k8sResourceType", 1);
        body.put("size", 100);
        body.put("osType", 0);
        return post("/chaos/QuerySceneFunctionByCategoryId", body);
    }

    /**
     * 获取故障参数模版
     * @param appCode 故障场景代码，如 chaos.container-mem.load
     * @param nodeGroups 节点分组列表
     */
    public JsonNode initMiniFlowByAppCode(String appCode, List<String> nodeGroups) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appCode", appCode);
        body.put("source", 1);
        body.put("appId", "2");

        com.fasterxml.jackson.databind.node.ArrayNode nodeGroupsArray = body.putArray("nodeGroups");
        if (nodeGroups != null) {
            for (String group : nodeGroups) {
                nodeGroupsArray.add(group);
            }
        }

        return post("/chaos/InitMiniFlowByAppCode", body);
    }
}

