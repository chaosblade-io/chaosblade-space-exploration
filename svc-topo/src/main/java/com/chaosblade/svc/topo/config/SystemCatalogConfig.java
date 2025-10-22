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

package com.chaosblade.svc.topo.config;

import com.chaosblade.svc.topo.model.JaegerSource;
import com.chaosblade.svc.topo.model.SystemInfo;
import com.chaosblade.svc.topo.model.SystemUnderTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class SystemCatalogConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemCatalogConfig.class);
    
    @Value("${topology.jaeger.host:localhost}")
    private String jaegerHost;
    
    @Value("${topology.jaeger.http-port:16686}")
    private int jaegerHttpPort;
    
    @Value("${topology.sut.service-name:ts-preserve-service}")
    private String entryService;
    
    @Value("${topology.sut.system-name:train-ticket}")
    private String systemName;
    
    private List<SystemInfo> systemCatalog;
    private Map<String, SystemInfo> systemInfoMap;
    
    @PostConstruct
    public void init() {
        loadSystemCatalog();
        buildSystemInfoMap();
    }
    
    private void loadSystemCatalog() {
        try {
            ClassPathResource resource = new ClassPathResource("system-catalog.json");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    SystemInfo[] systems = objectMapper.readValue(inputStream, SystemInfo[].class);
                    systemCatalog = Arrays.asList(systems);
                    logger.info("Successfully loaded {} systems from system-catalog.json", systemCatalog.size());
                }
            } else {
                logger.warn("system-catalog.json not found in classpath, initializing with empty catalog");
                systemCatalog = Arrays.asList();
            }
        } catch (IOException e) {
            logger.error("Failed to load system-catalog.json", e);
            systemCatalog = Arrays.asList();
        }
    }
    
    private void buildSystemInfoMap() {
        systemInfoMap = new HashMap<>();
        for (SystemInfo systemInfo : systemCatalog) {
            // 以key作为主键
            if (systemInfo.getKey() != null) {
                systemInfoMap.put(systemInfo.getKey(), systemInfo);
            }
            
            // 以alias作为额外键
            if (systemInfo.getAlias() != null) {
                for (String alias : systemInfo.getAlias()) {
                    systemInfoMap.put(alias, systemInfo);
                }
            }
        }
        logger.info("Built system info map with {} keys", systemInfoMap.size());
    }
    
    @Bean
    public JaegerSource jaegerSource() {
        JaegerSource jaegerSource = new JaegerSource();
        
        // 检查环境变量并覆盖配置
        String envJaegerHost = System.getenv("JaegerHost");
        String envJaegerPort = System.getenv("JaegerPort");
        String envEntryService = System.getenv("EntryService");
        
        // 使用环境变量或application.yml中的默认配置
        String effectiveJaegerHost = (envJaegerHost != null && !envJaegerHost.isEmpty()) ? envJaegerHost : jaegerHost;
        int effectiveJaegerHttpPort = jaegerHttpPort;
        String effectiveEntryService = (envEntryService != null && !envEntryService.isEmpty()) ? envEntryService : entryService;
        String effectiveSystemName = System.getenv("SystemName") != null && !System.getenv("SystemName").isEmpty() ? 
                                   System.getenv("SystemName") : systemName;
        
        // 解析端口环境变量
        if (envJaegerPort != null && !envJaegerPort.isEmpty()) {
            try {
                effectiveJaegerHttpPort = Integer.parseInt(envJaegerPort);
            } catch (NumberFormatException e) {
                logger.warn("无效的 JaegerPort 环境变量值: {}, 使用默认值: {}", envJaegerPort, jaegerHttpPort);
            }
        }
        
        jaegerSource.setHost(effectiveJaegerHost);
        jaegerSource.setHttpPort(effectiveJaegerHttpPort);
        jaegerSource.setEntryService(effectiveEntryService);
        jaegerSource.setSystemKey(effectiveSystemName);
        
        // 设置默认值
        jaegerSource.setBasePath("/api/traces");
        jaegerSource.setLimit(20);
        
        logger.info("Created JaegerSource with effective config: host={}, httpPort={}, entryService={}, systemKey={}", 
                   effectiveJaegerHost, effectiveJaegerHttpPort, effectiveEntryService, effectiveSystemName);
        return jaegerSource;
    }
    
    @Bean
    public SystemUnderTest systemUnderTest(JaegerSource jaegerSource) {
        SystemInfo systemInfo = findSystemInfo(jaegerSource.getSystemKey());
        SystemUnderTest systemUnderTest = new SystemUnderTest(jaegerSource, systemInfo);
        logger.info("Created SystemUnderTest: {}", systemUnderTest);
        return systemUnderTest;
    }
    
    public SystemInfo findSystemInfo(String systemKey) {
        if (systemKey == null || systemKey.isEmpty()) {
            logger.warn("System key is null or empty");
            return null;
        }
        
        SystemInfo systemInfo = systemInfoMap.get(systemKey);
        if (systemInfo == null) {
            logger.warn("SystemInfo not found for systemKey: {}", systemKey);
        }
        
        return systemInfo;
    }
    
    /**
     * 获取系统目录列表
     * 
     * @return 系统信息列表
     */
    public List<SystemInfo> getSystemCatalog() {
        return systemCatalog;
    }
}