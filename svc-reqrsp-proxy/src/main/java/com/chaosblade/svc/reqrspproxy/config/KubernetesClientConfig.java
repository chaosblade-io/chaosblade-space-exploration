package com.chaosblade.svc.reqrspproxy.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kubernetes 客户端配置。
 * 兼容 Fabric8 5.x API。
 */
@Configuration
public class KubernetesClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesClientConfig.class);

    @Bean
    public KubernetesClient kubernetesClient() {
        logger.info("Initializing Kubernetes client with in-cluster auto config");

        // In-cluster auto config
        Config config = Config.autoConfigure(null);
        config.setTrustCerts(true);
        config.setConnectionTimeout(30000);
        config.setRequestTimeout(60000);

        KubernetesClient client = new DefaultKubernetesClient(config);

        logger.info("Kubernetes client initialized successfully");
        return client;
    }
}
