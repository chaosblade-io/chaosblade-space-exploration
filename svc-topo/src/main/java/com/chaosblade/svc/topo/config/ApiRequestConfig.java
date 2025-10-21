package com.chaosblade.svc.topo.config;

import com.chaosblade.svc.topo.model.ApiRequestPayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** API请求配置类 用于加载和管理system-request.json文件中的ApiRequestPayload数据 */
@Component
public class ApiRequestConfig {

  private static final Logger logger = LoggerFactory.getLogger(ApiRequestConfig.class);

  private List<ApiRequestPayload> apiRequestPayloads;

  @PostConstruct
  public void init() {
    loadApiRequestPayloads();
  }

  /** 从system-request.json文件加载ApiRequestPayload数据 首先尝试从应用程序根目录加载，如果不存在则从classpath加载 */
  private void loadApiRequestPayloads() {
    try {
      // 首先尝试从应用程序根目录加载
      File file = new File("system-request.json");
      if (file.exists()) {
        try (InputStream inputStream = java.nio.file.Files.newInputStream(file.toPath())) {
          ObjectMapper objectMapper = new ObjectMapper();
          apiRequestPayloads =
              objectMapper.readValue(inputStream, new TypeReference<List<ApiRequestPayload>>() {});
          logger.info(
              "Successfully loaded {} API request payloads from system-request.json in app root",
              apiRequestPayloads.size());
          return;
        }
      }

      // 如果应用程序根目录不存在，则尝试从classpath加载
      ClassPathResource resource = new ClassPathResource("system-request.json");
      if (resource.exists()) {
        try (InputStream inputStream = resource.getInputStream()) {
          ObjectMapper objectMapper = new ObjectMapper();
          apiRequestPayloads =
              objectMapper.readValue(inputStream, new TypeReference<List<ApiRequestPayload>>() {});
          logger.info(
              "Successfully loaded {} API request payloads from system-request.json in classpath",
              apiRequestPayloads.size());
        }
      } else {
        logger.warn(
            "system-request.json not found in app root or classpath, initializing with empty list");
        apiRequestPayloads = new ArrayList<>();
      }
    } catch (IOException e) {
      logger.error("Failed to load system-request.json", e);
      apiRequestPayloads = new ArrayList<>();
    }
  }

  /**
   * 获取API请求负载列表
   *
   * @return ApiRequestPayload列表
   */
  public List<ApiRequestPayload> getApiRequestPayloads() {
    return apiRequestPayloads;
  }

  /**
   * 根据operationId查找ApiRequestPayload
   *
   * @param operationId 操作ID
   * @return Optional包装的ApiRequestPayload对象
   */
  public Optional<ApiRequestPayload> findByOperationId(String operationId) {
    if (apiRequestPayloads == null || operationId == null) {
      return Optional.empty();
    }

    return apiRequestPayloads.stream()
        .filter(payload -> operationId.equals(payload.getOperationId()))
        .findFirst();
  }

  /**
   * 添加新的API请求负载到列表中
   *
   * @param payload 要添加的ApiRequestPayload对象
   */
  public void addApiRequestPayload(ApiRequestPayload payload) {
    if (apiRequestPayloads == null) {
      apiRequestPayloads = new ArrayList<>();
    }
    apiRequestPayloads.add(payload);
    logger.info("Added new API request payload: {}", payload);
  }

  /**
   * 根据operationId更新或添加API请求负载
   *
   * @param payload 要更新或添加的ApiRequestPayload对象
   */
  public void updateOrAddApiRequestPayload(ApiRequestPayload payload) {
    if (apiRequestPayloads == null) {
      apiRequestPayloads = new ArrayList<>();
    }

    // 检查是否已存在相同的operationId，如果存在则更新，否则添加
    boolean updated = false;
    for (int i = 0; i < apiRequestPayloads.size(); i++) {
      if (apiRequestPayloads.get(i).getOperationId().equals(payload.getOperationId())) {
        apiRequestPayloads.set(i, payload);
        updated = true;
        logger.info("Updated API request payload with operationId: {}", payload.getOperationId());
        break;
      }
    }

    if (!updated) {
      apiRequestPayloads.add(payload);
      logger.info("Added new API request payload: {}", payload);
    }
  }
}
