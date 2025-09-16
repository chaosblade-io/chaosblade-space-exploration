package com.chaosblade.svc.taskresource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private Api api = new Api();
    private Timeout timeout = new Timeout();
    private int retries = 3;

    public static class Api {
        private String url;
        private String key;
        private String model;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Timeout {
        private int ms = 20000;
        public int getMs() { return ms; }
        public void setMs(int ms) { this.ms = ms; }
    }

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }

    public Timeout getTimeout() { return timeout; }
    public void setTimeout(Timeout timeout) { this.timeout = timeout; }

    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }
}

