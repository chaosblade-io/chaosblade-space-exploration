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

package com.chaosblade.svc.topo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * Topology Visualizer 应用启动类
 *
 * 功能特性：
 * 1. 支持OpenTelemetry trace文件上传
 * 2. 使用JGraphT构建拓扑图结构
 * 3. 前端可视化渲染
 * 4. 定时刷新拓扑数据
 *
 * @author Topo Visualizer Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class TopoApplication {

    @PostConstruct
    public void checkTimeZone() {
        TimeZone currentTimeZone = TimeZone.getDefault();
        String timeZoneId = currentTimeZone.getID();
        
        if (!"Asia/Shanghai".equals(timeZoneId)) {
            System.out.println("\n=== WARNING: 时区设置不是 Asia/Shanghai ===");
            System.out.println("当前时区: " + timeZoneId);
            System.out.println("建议设置时区为 Asia/Shanghai 以确保时间处理正确");
            System.out.println("==========================================\n");
        } else {
            System.out.println("\n=== 时区设置正确: " + timeZoneId + " ===\n");
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(TopoApplication.class, args);
        System.out.println("\n=== Topology Visualizer Started ===");
        System.out.println("==========================================\n");
    }

    /**
     * 配置CORS以支持前端跨域请求
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}