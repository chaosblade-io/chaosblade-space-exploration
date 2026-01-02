package com.chaosblade.svc.k8sgraph.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 命名空间分析缓存服务
 */
@Service
public class NsAnalysisCacheService {

    private static final Logger logger = LoggerFactory.getLogger(NsAnalysisCacheService.class);

    private static final String CACHE_PREFIX = "ns-analysis:";
    private static final String TASK_PREFIX = CACHE_PREFIX + "task:";
    private static final String RESULT_PREFIX = CACHE_PREFIX + "result:";
    private static final String LATEST_PREFIX = CACHE_PREFIX + "latest:";
    private static final String PROGRESS_PREFIX = CACHE_PREFIX + "progress:";

    private static final long TASK_TTL_HOURS = 24;
    private static final long RESULT_TTL_HOURS = 72;
    private static final long PROGRESS_TTL_MINUTES = 30;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 缓存任务状态
     */
    public void cacheTaskStatus(String taskId, Map<String, Object> status) {
        try {
            String key = TASK_PREFIX + taskId;
            String value = objectMapper.writeValueAsString(status);
            redisTemplate.opsForValue().set(key, value, TASK_TTL_HOURS, TimeUnit.HOURS);
            logger.debug("Cached task status: taskId={}", taskId);
        } catch (Exception e) {
            logger.warn("Failed to cache task status: taskId={}", taskId, e);
        }
    }

    /**
     * 获取缓存的任务状态
     */
    public Optional<Map<String, Object>> getTaskStatus(String taskId) {
        try {
            String key = TASK_PREFIX + taskId;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Optional.of(objectMapper.readValue(value, Map.class));
            }
        } catch (Exception e) {
            logger.warn("Failed to get cached task status: taskId={}", taskId, e);
        }
        return Optional.empty();
    }

    /**
     * 缓存分析结果
     */
    public void cacheResult(String taskId, Map<String, Object> result) {
        try {
            String key = RESULT_PREFIX + taskId;
            String value = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, value, RESULT_TTL_HOURS, TimeUnit.HOURS);
            logger.debug("Cached result: taskId={}", taskId);
        } catch (Exception e) {
            logger.warn("Failed to cache result: taskId={}", taskId, e);
        }
    }

    /**
     * 获取缓存的分析结果
     */
    public Optional<Map<String, Object>> getResult(String taskId) {
        try {
            String key = RESULT_PREFIX + taskId;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Optional.of(objectMapper.readValue(value, Map.class));
            }
        } catch (Exception e) {
            logger.warn("Failed to get cached result: taskId={}", taskId, e);
        }
        return Optional.empty();
    }

    /**
     * 缓存命名空间最新结果的taskId
     */
    public void cacheLatestTaskId(String namespace, String taskId) {
        try {
            String key = LATEST_PREFIX + namespace;
            redisTemplate.opsForValue().set(key, taskId, RESULT_TTL_HOURS, TimeUnit.HOURS);
            logger.debug("Cached latest taskId: namespace={}, taskId={}", namespace, taskId);
        } catch (Exception e) {
            logger.warn("Failed to cache latest taskId: namespace={}", namespace, e);
        }
    }

    /**
     * 获取命名空间最新结果的taskId
     */
    public Optional<String> getLatestTaskId(String namespace) {
        try {
            String key = LATEST_PREFIX + namespace;
            String value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            logger.warn("Failed to get latest taskId: namespace={}", namespace, e);
        }
        return Optional.empty();
    }

    /**
     * 更新任务进度
     */
    public void updateProgress(String taskId, int phase, int percent, String message) {
        try {
            String key = PROGRESS_PREFIX + taskId;
            Map<String, Object> progress = new HashMap<>();
            progress.put("phase", phase);
            progress.put("percent", percent);
            progress.put("message", message != null ? message : "");
            progress.put("updatedAt", System.currentTimeMillis());
            String value = objectMapper.writeValueAsString(progress);
            redisTemplate.opsForValue().set(key, value, PROGRESS_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.warn("Failed to update progress: taskId={}", taskId, e);
        }
    }

    /**
     * 获取任务进度
     */
    public Optional<Map<String, Object>> getProgress(String taskId) {
        try {
            String key = PROGRESS_PREFIX + taskId;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Optional.of(objectMapper.readValue(value, Map.class));
            }
        } catch (Exception e) {
            logger.warn("Failed to get progress: taskId={}", taskId, e);
        }
        return Optional.empty();
    }

    /**
     * 删除任务相关缓存
     */
    public void deleteTaskCache(String taskId) {
        try {
            redisTemplate.delete(TASK_PREFIX + taskId);
            redisTemplate.delete(RESULT_PREFIX + taskId);
            redisTemplate.delete(PROGRESS_PREFIX + taskId);
            logger.debug("Deleted task cache: taskId={}", taskId);
        } catch (Exception e) {
            logger.warn("Failed to delete task cache: taskId={}", taskId, e);
        }
    }

    /**
     * 检查任务是否存在
     */
    public boolean taskExists(String taskId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(TASK_PREFIX + taskId));
        } catch (Exception e) {
            return false;
        }
    }
}

