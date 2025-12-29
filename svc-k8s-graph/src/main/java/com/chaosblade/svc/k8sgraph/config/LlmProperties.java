package com.chaosblade.svc.k8sgraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置属性
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    
    private Api api = new Api();
    private Timeout timeout = new Timeout();
    private int retries = 3;
    
    public static class Api {
        /** API 提供商: anthropic, openai */
        private String provider = "anthropic";
        private String url = "https://globalai.vip/v1/messages";
        private String key;
        private String model = "claude-haiku-4-5-20251001";
        private int maxTokens = 64000;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }
    
    public static class Timeout {
        private int ms = 120000;
        
        public int getMs() { return ms; }
        public void setMs(int ms) { this.ms = ms; }
    }
    
    // Getters and Setters
    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }
    public Timeout getTimeout() { return timeout; }
    public void setTimeout(Timeout timeout) { this.timeout = timeout; }
    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }
}

