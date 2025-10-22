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

package com.chaosblade.svc.faultscheduler.service;

import com.chaosblade.svc.faultscheduler.api.ChaosBladeApi;
import com.chaosblade.svc.faultscheduler.repository.FaultRedisRepo;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

/** 故障服务层 实现故障执行、状态查询、停止和TTL自动删除功能 */
@Service
public class FaultsService {

  private static final Logger logger = LoggerFactory.getLogger(FaultsService.class);

  private final ChaosBladeApi bladeApi;
  private final FaultRedisRepo repo;
  private final ThreadPoolTaskScheduler scheduler;
  private final SpecNormalizer normalizer;

  @Value("${app.faults.events-limit:50}")
  private int eventsLimit;

  @Value("${app.faults.default-ttl-seconds:0}")
  private int defaultTtlSeconds;

  @Value("${app.faults.name-prefix:blade-}")
  private String namePrefix;

  public FaultsService(
      ChaosBladeApi bladeApi,
      FaultRedisRepo repo,
      ThreadPoolTaskScheduler scheduler,
      SpecNormalizer normalizer) {
    this.bladeApi = bladeApi;
    this.repo = repo;
    this.scheduler = scheduler;
    this.normalizer = normalizer;
  }

  /**
   * 执行故障
   *
   * @param faultJson 故障定义（JSON格式）
   * @param name 故障名称（可选）
   * @param durationSec TTL秒数（可选）
   * @return 执行结果，包含 faultId 和 bladeName
   */
  public Map<String, String> execute(
      Map<String, Object> faultJson, String name, Integer durationSec) {
    logger.info("Executing fault with name: {}, duration: {}s", name, durationSec);

    try {
      // 生成故障ID和名称
      String faultId = UUID.randomUUID().toString().replace("-", "");
      String bladeName = generateBladeName(name, faultId);

      logger.debug("Generated faultId: {}, bladeName: {}", faultId, bladeName);

      // 检查是否已存在同名故障
      if (bladeApi.exists(bladeName)) {
        throw new IllegalArgumentException("Fault with name already exists: " + bladeName);
      }

      // 创建标签
      Map<String, String> labels =
          Map.of(
              "fault-id", faultId,
              "owner", "faults-api",
              "created-by", "svc-fault-scheduler");

      // 规范化 CR 并验证
      Map<String, Object> normalized = normalizer.normalize(faultJson, bladeName, labels);
      @SuppressWarnings("unchecked")
      Map<String, Object> spec = (Map<String, Object>) normalized.get("spec");

      // 验证 spec
      if (!normalizer.validateSpec(spec)) {
        throw new IllegalArgumentException("Invalid fault specification");
      }

      // 创建 ChaosBlade 资源
      GenericKubernetesResource created = bladeApi.create(bladeName, labels, spec);
      logger.info("Successfully created ChaosBlade resource: {}", bladeName);

      // 生成 YAML 用于审计
      String yaml = "";
      try {
        yaml = normalizer.toYaml(normalized);
      } catch (Exception e) {
        logger.warn("Failed to generate YAML for audit: {}", e.getMessage());
      }

      // 计算 TTL
      long ttl = calculateTtl(durationSec);

      // 保存到 Redis
      Map<String, String> faultData =
          Map.of(
              "faultId",
              faultId,
              "bladeName",
              bladeName,
              "createdAt",
              Instant.now().toString(),
              "specYaml",
              yaml,
              "status",
              "Created",
              "ttlSec",
              String.valueOf(ttl));

      repo.save(bladeName, faultData, ttl);
      logger.debug("Saved fault data to Redis for bladeName: {}", bladeName);

      // 设置 TTL 自动删除
      if (ttl > 0) {
        scheduleAutoDelete(bladeName, ttl);
      }

      logger.info(
          "Successfully executed fault: faultId={}, bladeName={}, ttl={}s",
          faultId,
          bladeName,
          ttl);

      return Map.of("faultId", faultId, "bladeName", bladeName);

    } catch (Exception e) {
      logger.error("Failed to execute fault", e);
      throw new RuntimeException("Failed to execute fault: " + e.getMessage(), e);
    }
  }

  /**
   * 查询故障状态和事件
   *
   * @param bladeName 故障名称
   * @return 状态和事件信息
   */
  public Map<String, Object> statusAndEvents(String bladeName) {
    logger.debug("Querying status and events for bladeName: {}", bladeName);

    try {
      // 获取 ChaosBlade 资源
      GenericKubernetesResource blade = bladeApi.get(bladeName);
      if (blade == null) {
        throw new NoSuchElementException("Fault not found: " + bladeName);
      }

      // 获取状态
      Map<String, Object> status = bladeApi.status(blade);
      String phase = String.valueOf(status.getOrDefault("phase", "Unknown"));

      // 获取事件
      var events = bladeApi.eventsForBlade(bladeName, eventsLimit);

      // 更新 Redis 中的状态
      try {
        repo.updateStatus(bladeName, phase);
      } catch (Exception e) {
        logger.warn("Failed to update status in Redis for bladeName: {}", bladeName, e);
      }

      // 构建响应
      Map<String, Object> result =
          Map.of(
              "bladeName", bladeName,
              "phase", phase,
              "status", status,
              "events", events,
              "eventsCount", events.size());

      logger.debug(
          "Successfully retrieved status for bladeName: {}, phase: {}, events: {}",
          bladeName,
          phase,
          events.size());

      return result;

    } catch (NoSuchElementException e) {
      logger.warn("Fault not found: {}", bladeName);
      throw e;
    } catch (Exception e) {
      logger.error("Failed to query status for bladeName: {}", bladeName, e);
      throw new RuntimeException("Failed to query fault status: " + e.getMessage(), e);
    }
  }

  /**
   * 停止故障
   *
   * @param bladeName 故障名称
   */
  public void stop(String bladeName) {
    logger.info("Stopping fault: {}", bladeName);

    try {
      // 删除 ChaosBlade 资源
      boolean deleted = bladeApi.delete(bladeName);
      if (!deleted) {
        throw new NoSuchElementException("Fault not found: " + bladeName);
      }

      // 从 Redis 删除
      repo.delete(bladeName);

      logger.info("Successfully stopped fault: {}", bladeName);

    } catch (NoSuchElementException e) {
      logger.warn("Fault not found for deletion: {}", bladeName);
      throw e;
    } catch (Exception e) {
      logger.error("Failed to stop fault: {}", bladeName, e);
      throw new RuntimeException("Failed to stop fault: " + e.getMessage(), e);
    }
  }

  /** 生成故障名称 */
  private String generateBladeName(String name, String faultId) {
    if (name != null && !name.trim().isEmpty()) {
      return name.trim();
    }
    return namePrefix + faultId.substring(0, 12);
  }

  /** 计算 TTL */
  private long calculateTtl(Integer durationSec) {
    if (durationSec != null && durationSec > 0) {
      return durationSec;
    }
    return defaultTtlSeconds;
  }

  /** 调度自动删除任务 */
  private void scheduleAutoDelete(String bladeName, long ttlSec) {
    logger.debug("Scheduling auto-delete for bladeName: {} in {}s", bladeName, ttlSec);

    scheduler.schedule(
        () -> {
          try {
            logger.info("Auto-deleting fault due to TTL expiration: {}", bladeName);

            // 删除 ChaosBlade 资源
            bladeApi.delete(bladeName);

            // 从 Redis 删除
            repo.delete(bladeName);

            logger.info("Successfully auto-deleted fault: {}", bladeName);

          } catch (Exception e) {
            logger.error("Failed to auto-delete fault: {}", bladeName, e);
          }
        },
        Date.from(Instant.now().plusSeconds(ttlSec)));
  }

  /**
   * 获取所有故障列表
   *
   * @return 故障名称列表
   */
  public java.util.Set<String> listAllFaults() {
    try {
      logger.debug("Listing all faults");
      return repo.getAllFaultNames();
    } catch (Exception e) {
      logger.error("Failed to list all faults", e);
      throw new RuntimeException("Failed to list faults", e);
    }
  }

  /**
   * 获取故障详细信息
   *
   * @param bladeName 故障名称
   * @return 故障详细信息，包含 Redis 中的数据和 Kubernetes 中的状态
   */
  public Map<String, Object> getFaultDetails(String bladeName) {
    logger.debug("Getting fault details for bladeName: {}", bladeName);

    // 检查故障是否存在
    if (!bladeApi.exists(bladeName)) {
      throw new java.util.NoSuchElementException("Fault not found: " + bladeName);
    }

    try {
      // 从 Redis 获取故障数据
      Map<Object, Object> rawFaultData = repo.get(bladeName);
      Map<String, String> faultData = null;
      if (rawFaultData != null && !rawFaultData.isEmpty()) {
        faultData = new java.util.HashMap<>();
        for (Map.Entry<Object, Object> entry : rawFaultData.entrySet()) {
          faultData.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
      }

      // 从 Kubernetes 获取 ChaosBlade 资源
      GenericKubernetesResource blade = bladeApi.get(bladeName);
      Map<String, Object> status = bladeApi.status(blade);

      // 构建详细信息
      Map<String, Object> details = new java.util.HashMap<>();
      details.put("bladeName", bladeName);

      // 添加 Redis 中的数据
      if (faultData != null) {
        details.put("faultId", faultData.get("faultId"));
        details.put("createdAt", faultData.get("createdAt"));
        details.put("ttlSec", faultData.get("ttlSec"));
        details.put("specYaml", faultData.get("specYaml"));
      }

      // 添加 Kubernetes 状态
      details.put("phase", status.getOrDefault("phase", "Unknown"));
      details.put("status", status);

      // 添加资源规格
      if (blade != null && blade.getAdditionalProperties() != null) {
        details.put("spec", blade.getAdditionalProperties().get("spec"));
        details.put("metadata", blade.getMetadata());
      }

      logger.debug("Successfully retrieved fault details for bladeName: {}", bladeName);
      return details;

    } catch (java.util.NoSuchElementException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Failed to get fault details for bladeName: {}", bladeName, e);
      throw new RuntimeException("Failed to get fault details: " + e.getMessage(), e);
    }
  }

  /**
   * 检查故障是否存在
   *
   * @param bladeName 故障名称
   * @return 是否存在
   */
  public boolean exists(String bladeName) {
    try {
      return bladeApi.exists(bladeName);
    } catch (Exception e) {
      logger.error("Failed to check fault existence: {}", bladeName, e);
      return false;
    }
  }
}
