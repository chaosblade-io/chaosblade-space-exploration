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

package com.chaosblade.svc.reqrspproxy.task;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.chaosblade.svc.reqrspproxy.service.RecordingStateService;
import com.chaosblade.svc.reqrspproxy.service.TapCollector;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 自动采集任务 */
@Component
public class AutoCollectionTask {

  private static final Logger logger = LoggerFactory.getLogger(AutoCollectionTask.class);

  @Autowired private StringRedisTemplate redis;

  @Autowired private RecordingStateService stateService;

  @Autowired private TapCollector tapCollector;

  @Autowired private RecordingConfig recordingConfig;

  /** 定期采集运行中的录制数据 */
  @Scheduled(fixedDelayString = "#{${recording.auto-collect-interval-sec:30} * 1000}")
  public void autoCollect() {
    logger.debug("Starting auto collection task");

    try {
      // 查找所有运行中的录制
      Set<String> stateKeys = redis.keys("rec:*:state");
      if (stateKeys == null || stateKeys.isEmpty()) {
        logger.debug("No active recordings found");
        return;
      }

      int collectedCount = 0;
      int cleanedCount = 0;

      for (String stateKey : stateKeys) {
        String recordingId = extractRecordingId(stateKey);
        if (recordingId == null) continue;

        // 如果状态键存在但状态已被其他流程删除，跳过并清理孤儿键
        if (!stateService.exists(recordingId)) {
          logger.debug(
              "Skip auto-collect, state missing for {}. Clean up key {}", recordingId, stateKey);
          redis.delete(stateKey);
          cleanedCount++;
          continue;
        }

        try {
          RecordingState state = stateService.loadState(recordingId);

          if (state.getStatus() == RecordingState.RecordingStatus.RECORDING) {
            // 冷启动缓冲，避免 Envoy/Pod 未就绪
            java.time.LocalDateTime startedAt = state.getStartedAt();
            if (startedAt != null) {
              long secondsSinceStart =
                  java.time.Duration.between(startedAt, java.time.LocalDateTime.now()).getSeconds();
              if (secondsSinceStart < 20) {
                logger.debug(
                    "Skip auto-collect for {} ({}s since RECORDING < 20s)",
                    recordingId,
                    secondsSinceStart);
                continue;
              }
            }

            tapCollector
                .collectOnce(recordingId, state)
                .thenAccept(
                    count -> {
                      if (count > 0) {
                        logger.debug(
                            "Auto-collected {} entries for recording {}", count, recordingId);
                      }
                    })
                .exceptionally(
                    throwable -> {
                      logger.warn(
                          "Auto-collection failed for recording {}: {}",
                          recordingId,
                          throwable.getMessage());
                      return null;
                    });
            collectedCount++;

          } else if (state.getStatus() == RecordingState.RecordingStatus.STOPPED
              || state.getStatus() == RecordingState.RecordingStatus.ERROR) {
            logger.info(
                "Cleaning up finished recording {}: status={}", recordingId, state.getStatus());
            stateService.deleteState(recordingId);
            cleanedCount++;
          }

        } catch (Exception e) {
          logger.warn("Failed to process recording {}: {}", recordingId, e.getMessage());
          if (e.getMessage() != null && e.getMessage().contains("Recording not found")) {
            logger.info("Cleaning up orphaned recording state key: {}", stateKey);
            redis.delete(stateKey);
            cleanedCount++;
          }
        }
      }

      if (collectedCount > 0 || cleanedCount > 0) {
        logger.info(
            "Auto collection task completed: collected from {} recordings, cleaned up {} finished"
                + " recordings",
            collectedCount,
            cleanedCount);
      }

    } catch (Exception e) {
      logger.error("Auto collection task failed: {}", e.getMessage(), e);
    }
  }

  /** 从状态键中提取录制 ID */
  private String extractRecordingId(String stateKey) {
    // 格式: rec:{recordingId}:state
    if (stateKey.startsWith("rec:") && stateKey.endsWith(":state")) {
      return stateKey.substring(4, stateKey.length() - 6);
    }
    return null;
  }
}
