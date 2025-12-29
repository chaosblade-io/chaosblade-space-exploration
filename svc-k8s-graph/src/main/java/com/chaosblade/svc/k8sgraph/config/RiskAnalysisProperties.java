package com.chaosblade.svc.k8sgraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 风险分析配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "risk-analysis")
public class RiskAnalysisProperties {
    
    private Debug debug = new Debug();
    
    public Debug getDebug() {
        return debug;
    }
    
    public void setDebug(Debug debug) {
        this.debug = debug;
    }
    
    public static class Debug {
        /** 是否在响应中显示发送给LLM的prompt */
        private boolean showPrompt = false;
        
        public boolean isShowPrompt() {
            return showPrompt;
        }
        
        public void setShowPrompt(boolean showPrompt) {
            this.showPrompt = showPrompt;
        }
    }
}

