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

package com.chaosblade.svc.reqrspproxy.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kubernetes 客户端配置
 */
@Configuration
public class KubernetesClientConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(KubernetesClientConfig.class);
    
    @Bean
    public KubernetesClient kubernetesClient(
            @Value("${kubernetes.api-url}") String apiUrl,
            @Value("${kubernetes.token}") String token,
            @Value("${kubernetes.verify-ssl:false}") boolean verifySsl) {
        
        logger.info("Initializing Kubernetes client with API URL: {}", apiUrl);
        
        Config config = new ConfigBuilder()
                .withMasterUrl(apiUrl)
                .withOauthToken(token)
                .withTrustCerts(!verifySsl)
                .withConnectionTimeout(30000)
                .withRequestTimeout(60000)
                .build();
        
        KubernetesClient client = new KubernetesClientBuilder()
                .withConfig(config)
                .build();
        
        logger.info("Kubernetes client initialized successfully");
        return client;
    }
}
