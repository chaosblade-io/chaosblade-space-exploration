package com.chaosblade.svc.faultscheduler.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 基础设施配置类
 * 配置 Kubernetes 客户端和任务调度器
 */
@Configuration
public class InfraConfig {

    private static final Logger logger = LoggerFactory.getLogger(InfraConfig.class);

    /**
     * 配置 Kubernetes 客户端 - 使用 in-cluster 自动配置
     */
    @Bean
    public KubernetesClient kubernetesClient() {
        logger.info("Initializing Kubernetes client with in-cluster auto config");

        try {
            // In-cluster auto config
            Config config = Config.autoConfigure(null);
            config.setTrustCerts(true);
            config.setConnectionTimeout(10000);
            config.setRequestTimeout(30000);

            KubernetesClient client = new DefaultKubernetesClient(config);

            // 测试连接
            logger.info("Testing Kubernetes connection...");
            String version = client.getKubernetesVersion().getGitVersion();
            logger.info("Successfully connected to Kubernetes cluster, version: {}", version);

            return client;
        } catch (Exception e) {
            logger.error("Failed to initialize Kubernetes client", e);
            throw new RuntimeException("Failed to initialize Kubernetes client", e);
        }
    }
    
    /**
     * 配置任务调度器，用于 TTL 自动删除功能
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        logger.info("Initializing task scheduler for fault TTL management");
        
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("fault-ttl-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        
        logger.info("Task scheduler initialized with pool size: 4");
        return scheduler;
    }
}
