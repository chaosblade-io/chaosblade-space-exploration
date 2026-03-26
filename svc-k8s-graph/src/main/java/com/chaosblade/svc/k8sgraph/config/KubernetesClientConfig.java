package com.chaosblade.svc.k8sgraph.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kubernetes客户端配置
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

        KubernetesClient client = new KubernetesClientBuilder()
                .withConfig(config)
                .build();

        logger.info("Kubernetes client initialized successfully");
        return client;
    }
}

