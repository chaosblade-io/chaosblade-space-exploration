package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.dto.RequestPatternResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 任务状态管理器 管理请求模式获取任务的状态和进度 */
@Service
public class TaskStateManager {

  private static final Logger logger = LoggerFactory.getLogger(TaskStateManager.class);

  /** 任务执行阶段枚举 */
  public enum TaskPhase {
    INITIALIZING("初始化阶段"),
    APPLYING_RULES("规则应用阶段"),
    ROLLING_UPDATE("滚动更新阶段"),
    TRIGGERING_REQUESTS("请求发起阶段"),
    COLLECTING_DATA("数据收集阶段"),
    ANALYZING_PATTERNS("模式分析阶段"),
    COMPLETED("完成阶段"),
    FAILED("失败阶段");

    private final String description;

    TaskPhase(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  @Autowired private StringRedisTemplate redis;

  @Autowired private ObjectMapper objectMapper;

  // 内存中的任务状态缓存
  private final ConcurrentHashMap<String, RequestPatternResponse> taskCache =
      new ConcurrentHashMap<>();

  /** 保存任务状态 */
  public void saveTaskState(RequestPatternResponse response) {
    try {
      String taskId = response.getTaskId();

      // 保存到内存缓存
      taskCache.put(taskId, response);

      // 保存到Redis
      String key = "task:pattern:" + taskId;
      String json = objectMapper.writeValueAsString(response);
      redis.opsForValue().set(key, json, Duration.ofHours(24)); // 24小时过期

      logger.debug("Task state saved: {} - {}", taskId, response.getStatus());

    } catch (Exception e) {
      logger.error("Failed to save task state: {}", e.getMessage(), e);
    }
  }

  /** 获取任务状态 */
  public RequestPatternResponse getTaskState(String taskId) {
    try {
      // 先从内存缓存获取
      RequestPatternResponse cached = taskCache.get(taskId);
      if (cached != null) {
        return cached;
      }

      // 从Redis获取
      String key = "task:pattern:" + taskId;
      String json = redis.opsForValue().get(key);
      if (json != null) {
        RequestPatternResponse response =
            objectMapper.readValue(json, RequestPatternResponse.class);
        taskCache.put(taskId, response); // 更新缓存
        return response;
      }

    } catch (Exception e) {
      logger.error("Failed to get task state: {}", e.getMessage(), e);
    }

    return null;
  }

  /** 更新任务阶段 */
  public void updateTaskPhase(String taskId, TaskPhase phase) {
    updateTaskPhase(taskId, phase, null);
  }

  /** 更新任务阶段和消息 */
  public void updateTaskPhase(String taskId, TaskPhase phase, String message) {
    RequestPatternResponse response = getTaskState(taskId);
    if (response != null) {
      response.setStatus(phase.name());
      if (message != null) {
        response.setMessage(message);
      } else {
        response.setMessage(phase.getDescription());
      }

      // 如果是完成或失败状态，设置结束时间
      if (phase == TaskPhase.COMPLETED || phase == TaskPhase.FAILED) {
        response.setEndTime(LocalDateTime.now());
      }

      saveTaskState(response);
      logger.info("Task {} phase updated: {} - {}", taskId, phase.name(), phase.getDescription());
    }
  }

  /** 更新任务进度信息 */
  public void updateTaskProgress(
      String taskId, Integer totalRecordedRequests, Integer analyzedServices) {
    RequestPatternResponse response = getTaskState(taskId);
    if (response != null) {
      if (totalRecordedRequests != null) {
        response.setTotalRecordedRequests(totalRecordedRequests);
      }
      if (analyzedServices != null) {
        response.setAnalyzedServices(analyzedServices);
      }
      saveTaskState(response);
    }
  }

  /** 设置任务失败 */
  public void setTaskFailed(String taskId, String errorMessage) {
    RequestPatternResponse response = getTaskState(taskId);
    if (response != null) {
      response.setStatus(TaskPhase.FAILED.name());
      response.setMessage("任务执行失败: " + errorMessage);
      response.setEndTime(LocalDateTime.now());
      saveTaskState(response);
      logger.error("Task {} failed: {}", taskId, errorMessage);
    }
  }

  /** 设置任务完成 */
  public void setTaskCompleted(String taskId, RequestPatternResponse finalResponse) {
    finalResponse.setStatus(TaskPhase.COMPLETED.name());
    finalResponse.setMessage("请求模式获取完成");
    finalResponse.setEndTime(LocalDateTime.now());
    saveTaskState(finalResponse);
    logger.info("Task {} completed successfully", taskId);
  }

  /** 删除任务状态 */
  public void removeTaskState(String taskId) {
    try {
      taskCache.remove(taskId);
      String key = "task:pattern:" + taskId;
      redis.delete(key);
      logger.debug("Task state removed: {}", taskId);
    } catch (Exception e) {
      logger.error("Failed to remove task state: {}", e.getMessage(), e);
    }
  }

  /** 检查任务是否存在 */
  public boolean taskExists(String taskId) {
    return getTaskState(taskId) != null;
  }
}
