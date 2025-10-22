/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.faultscheduler.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 基础设施配置类 配置 Kubernetes 客户端和任务调度器 */
@Configuration
public class InfraConfig {

  private static final Logger logger = LoggerFactory.getLogger(InfraConfig.class);

  @Value("${kubernetes.api-url}")
  private String kubernetesApiUrl;

  @Value("${kubernetes.token}")
  private String kubernetesToken;

  @Value("${kubernetes.verify-ssl:false}")
  private boolean verifySSL;

  @Value("${kubernetes.connection-timeout:10000}")
  private int connectionTimeout;

  @Value("${kubernetes.request-timeout:30000}")
  private int requestTimeout;

  /** 配置 Kubernetes 客户端 */
  @Bean
  public KubernetesClient kubernetesClient() {
    logger.info("Initializing Kubernetes client with API URL: {}", kubernetesApiUrl);

    try {
      Config config =
          new ConfigBuilder()
              .withMasterUrl(kubernetesApiUrl)
              .withOauthToken(kubernetesToken)
              .withTrustCerts(!verifySSL)
              .withConnectionTimeout(connectionTimeout)
              .withRequestTimeout(requestTimeout)
              .build();

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

  /** 配置任务调度器，用于 TTL 自动删除功能 */
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
