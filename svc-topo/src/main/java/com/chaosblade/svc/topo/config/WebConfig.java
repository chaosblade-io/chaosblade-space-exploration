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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 *
 * <p>配置： 1. CORS跨域支持 2. 静态资源处理 3. 文件上传配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  /** 配置CORS跨域 */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }

  /** 配置静态资源处理 */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // 静态文件
    registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");

    registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/");

    registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");

    // 前端构建后的静态资源
    registry.addResourceHandler("/").addResourceLocations("classpath:/static/frontend/dist/");

    registry.addResourceHandler("/**").addResourceLocations("classpath:/static/frontend/dist/");

    // 其他静态资源
    registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");

    // favicon处理
    registry
        .addResourceHandler("/favicon.ico")
        .addResourceLocations("classpath:/static/favicon.ico");
  }

  /** 文件上传解析器 */
  @Bean
  public MultipartResolver multipartResolver() {
    return new StandardServletMultipartResolver();
  }
}
