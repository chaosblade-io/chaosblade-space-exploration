package com.chaosblade.svc.topo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Topology Visualizer 应用启动类
 *
 * 功能特性：
 * 1. 支持OpenTelemetry trace文件上传
 * 2. 使用JGraphT构建拓扑图结构
 * 3. 前端可视化渲染
 *
 * @author Topo Visualizer Team
 * @version 1.0.0
 */
@SpringBootApplication
public class TopoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TopoApplication.class, args);
        System.out.println("\n=== Topology Visualizer Started ===");
        System.out.println("🚀 Application is running on: http://localhost:8080/");
        System.out.println("📊 Ready to process OpenTelemetry trace files!");
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
