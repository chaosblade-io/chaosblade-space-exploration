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

package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.service.TopologyCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 拓扑缓存管理控制器
 * 提供缓存状态查询和管理功能
 */
@RestController
@RequestMapping("/v1/cache")
@CrossOrigin(origins = "*")
public class TopologyCacheController {

    private static final Logger logger = LoggerFactory.getLogger(TopologyCacheController.class);

    @Autowired
    private TopologyCacheService topologyCacheService;

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<String> getCacheStats() {
        logger.info("收到缓存统计信息查询请求");
        try {
            String stats = topologyCacheService.getCacheStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("查询缓存统计信息失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清空缓存
     *
     * @return 操作结果
     */
    @DeleteMapping("/clear")
    public ResponseEntity<String> clearCache() {
        logger.info("收到清空缓存请求");
        try {
            topologyCacheService.clear();
            return ResponseEntity.ok("缓存已清空");
        } catch (Exception e) {
            logger.error("清空缓存失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 按时间索引查询缓存项
     *
     * @param timeIndex 时间索引（0-14）
     * @return 匹配的缓存项数量
     */
    @GetMapping("/time-index/{timeIndex}")
    public ResponseEntity<Integer> getCacheByTimeIndex(@PathVariable int timeIndex) {
        logger.info("收到按时间索引查询缓存请求: timeIndex={}", timeIndex);
        
        // 验证时间索引范围
        if (timeIndex < 0 || timeIndex > 14) {
            return ResponseEntity.badRequest().body(null);
        }
        
        try {
            Map<TopologyCacheService.TimeKey, com.chaosblade.svc.topo.model.topology.TopologyGraph> cacheItems = 
                topologyCacheService.getByTimeIndex(timeIndex);
            return ResponseEntity.ok(cacheItems.size());
        } catch (Exception e) {
            logger.error("按时间索引查询缓存失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}