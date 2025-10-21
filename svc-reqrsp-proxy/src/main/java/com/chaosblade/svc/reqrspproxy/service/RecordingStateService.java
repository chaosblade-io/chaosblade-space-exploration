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

package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 录制状态管理服务
 */
@Service
public class RecordingStateService {
    
    private static final Logger logger = LoggerFactory.getLogger(RecordingStateService.class);
    private static final String STATE_KEY_PREFIX = "rec:";
    private static final String STATE_KEY_SUFFIX = ":state";
    private static final int STATE_TTL_DAYS = 7;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试 Redis 连接
     */
    public void testConnection() {
        try {
            String testKey = "test:connection:" + System.currentTimeMillis();
            String testValue = "test";

            redis.opsForValue().set(testKey, testValue);
            String result = redis.opsForValue().get(testKey);
            redis.delete(testKey);

            if (!testValue.equals(result)) {
                throw new RuntimeException("Redis read/write test failed");
            }

            logger.debug("Redis connection test successful");

        } catch (Exception e) {
            logger.error("Redis connection test failed: {}", e.getMessage(), e);
            throw new RuntimeException("Redis connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * 保存录制状态
     */
    public void saveState(RecordingState state) {
        try {
            String key = getStateKey(state.getRecordingId());
            String json = objectMapper.writeValueAsString(state);

            logger.info("Saving recording state for {} with key: {}", state.getRecordingId(), key);
            logger.debug("Recording state JSON: {}", json);

            redis.opsForValue().set(key, json);
            redis.expire(key, Duration.ofDays(STATE_TTL_DAYS));

            // 验证保存是否成功
            String savedJson = redis.opsForValue().get(key);
            if (savedJson != null) {
                logger.info("Successfully saved and verified recording state for {}", state.getRecordingId());
            } else {
                logger.error("Failed to verify saved recording state for {}", state.getRecordingId());
                throw new RuntimeException("Recording state verification failed");
            }

        } catch (Exception e) {
            logger.error("Failed to save recording state for {}: {}", state.getRecordingId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save recording state: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载录制状态
     */
    public RecordingState loadState(String recordingId) {
        try {
            String key = getStateKey(recordingId);

            String json = redis.opsForValue().get(key);

            if (json == null) {
                logger.error("Recording state not found in Redis for key: {}", key);

                // 列出所有相关的 key 用于调试
                Set<String> allKeys = redis.keys("recording:state:*");
                logger.error("Available recording keys in Redis: {}", allKeys);

                throw new RuntimeException("Recording not found: " + recordingId);
            }

            logger.debug("Found recording state JSON: {}", json);
            RecordingState state = objectMapper.readValue(json, RecordingState.class);
            logger.info("Successfully loaded recording state for {}", recordingId);

            return state;

        } catch (Exception e) {
            logger.error("Failed to load recording state for {}: {}", recordingId, e.getMessage(), e);
            throw new RuntimeException("Failed to load recording state: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查录制是否存在
     */
    public boolean exists(String recordingId) {
        try {
            String key = getStateKey(recordingId);
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (Exception e) {
            logger.error("Failed to check recording existence for {}: {}", recordingId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 更新录制状态
     */
    public void updateStatus(String recordingId, RecordingState.RecordingStatus status) {
        try {
            if (!exists(recordingId)) {
                logger.warn("Skip updateStatus: recording {} not found (target status={})", recordingId, status);
                return;
            }
            RecordingState state = loadState(recordingId);
            state.setStatus(status);

            if (status == RecordingState.RecordingStatus.STOPPED) {
                state.setStoppedAt(LocalDateTime.now());
            }

            saveState(state);
            logger.info("Updated recording {} status to {}", recordingId, status);

        } catch (Exception e) {
            logger.error("Failed to update recording status for {}: {}", recordingId, e.getMessage(), e);
            throw new RuntimeException("Failed to update recording status: " + e.getMessage(), e);
        }
    }
    
    /**
     * 设置错误信息
     */
    public void setError(String recordingId, String errorMessage) {
        try {
            if (!exists(recordingId)) {
                logger.warn("Skip setError: recording {} not found, message={} (cleaned?)", recordingId, errorMessage);
                return;
            }
            RecordingState state = loadState(recordingId);
            state.setStatus(RecordingState.RecordingStatus.ERROR);
            state.setErrorMessage(errorMessage);
            state.setStoppedAt(LocalDateTime.now());

            saveState(state);
            logger.error("Set recording {} to error state: {}", recordingId, errorMessage);

        } catch (Exception e) {
            logger.error("Failed to set error for recording {}: {}", recordingId, e.getMessage(), e);
        }
    }
    
    /**
     * 删除录制状态
     */
    public void deleteState(String recordingId) {
        try {
            String key = getStateKey(recordingId);
            redis.delete(key);

            // 同时删除相关的条目和处理记录
            redis.delete("rec:" + recordingId + ":entries");
            redis.delete("rec:" + recordingId + ":processed");

            logger.info("Deleted recording state for {}", recordingId);

        } catch (Exception e) {
            logger.error("Failed to delete recording state for {}: {}", recordingId, e.getMessage(), e);
        }
    }

    /**
     * 获取所有活跃的录制 ID
     */
    public List<String> getAllActiveRecordings() {
        try {
            String pattern = STATE_KEY_PREFIX + "*" + STATE_KEY_SUFFIX;
            Set<String> keys = redis.keys(pattern);

            List<String> activeRecordings = new ArrayList<>();

            if (keys != null) {
                for (String key : keys) {
                    try {
                        String json = redis.opsForValue().get(key);
                        if (json != null) {
                            RecordingState state = objectMapper.readValue(json, RecordingState.class);

                            // 只返回运行中的录制
                            if (state.getStatus() == RecordingState.RecordingStatus.RECORDING) {
                                activeRecordings.add(state.getRecordingId());
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse recording state from key {}: {}", key, e.getMessage());
                    }
                }
            }

            logger.debug("Found {} active recordings", activeRecordings.size());
            return activeRecordings;

        } catch (Exception e) {
            logger.error("Failed to get all active recordings: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有活跃的录制状态详情
     */
    public List<RecordingState> getAllActiveRecordingStates() {
        try {
            String pattern = STATE_KEY_PREFIX + "*" + STATE_KEY_SUFFIX;
            Set<String> keys = redis.keys(pattern);

            List<RecordingState> activeRecordings = new ArrayList<>();

            if (keys != null) {
                for (String key : keys) {
                    try {
                        String json = redis.opsForValue().get(key);
                        if (json != null) {
                            RecordingState state = objectMapper.readValue(json, RecordingState.class);

                            // 只返回运行中的录制
                            if (state.getStatus() == RecordingState.RecordingStatus.RECORDING) {
                                activeRecordings.add(state);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse recording state from key {}: {}", key, e.getMessage());
                    }
                }
            }

            logger.debug("Found {} active recording states", activeRecordings.size());
            return activeRecordings;

        } catch (Exception e) {
            logger.error("Failed to get all active recording states: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取 Redis 数据统计
     */
    public Map<String, Object> getRedisStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 统计任务数量
            Set<String> taskKeys = redis.keys("task:pattern:*");
            stats.put("totalTasks", taskKeys != null ? taskKeys.size() : 0);

            // 统计录制状态数量
            Set<String> recordingStateKeys = redis.keys("rec:*:state");
            stats.put("totalRecordings", recordingStateKeys != null ? recordingStateKeys.size() : 0);

            // 统计录制数据数量
            Set<String> recordingDataKeys = redis.keys("rec:*:entries");
            stats.put("totalRecordingData", recordingDataKeys != null ? recordingDataKeys.size() : 0);

            // 统计处理记录数量
            Set<String> processedKeys = redis.keys("rec:*:processed");
            stats.put("totalProcessedFiles", processedKeys != null ? processedKeys.size() : 0);

            // 按状态统计录制
            Map<String, Integer> statusCounts = new HashMap<>();
            if (recordingStateKeys != null) {
                for (String stateKey : recordingStateKeys) {
                    try {
                        String json = redis.opsForValue().get(stateKey);
                        if (json != null) {
                            RecordingState state = objectMapper.readValue(json, RecordingState.class);
                            String status = state.getStatus().name();
                            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
                        }
                    } catch (Exception e) {
                        statusCounts.put("INVALID", statusCounts.getOrDefault("INVALID", 0) + 1);
                    }
                }
            }
            stats.put("recordingsByStatus", statusCounts);

            // 最近的任务和录制
            if (taskKeys != null && !taskKeys.isEmpty()) {
                stats.put("recentTasks", taskKeys.stream().limit(5).collect(Collectors.toList()));
            }

            if (recordingStateKeys != null && !recordingStateKeys.isEmpty()) {
                stats.put("recentRecordings", recordingStateKeys.stream().limit(10).collect(Collectors.toList()));
            }

            logger.debug("Redis stats collected successfully");

        } catch (Exception e) {
            logger.error("Failed to collect Redis stats: {}", e.getMessage(), e);
            stats.put("error", "Failed to collect stats: " + e.getMessage());
        }

        return stats;
    }

    /**
     * 生成状态键
     */
    private String getStateKey(String recordingId) {
        return STATE_KEY_PREFIX + recordingId + STATE_KEY_SUFFIX;
    }
}
