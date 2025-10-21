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

package com.chaosblade.svc.reqrspproxy.controller;

import com.chaosblade.svc.reqrspproxy.dto.FixtureUpsertRequest;
import com.chaosblade.svc.reqrspproxy.dto.FixtureUpsertResponse;

import com.chaosblade.svc.reqrspproxy.service.FixtureInterceptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.Map;

/**
 * Fixture管理控制器
 */
@RestController
@RequestMapping("/api/fixtures")
public class FixtureController {

    private static final Logger logger = LoggerFactory.getLogger(FixtureController.class);

    @Autowired
    private FixtureInterceptionService fixtureService;

    /**
     * 批量更新fixture规则
     */
    @PostMapping("/upsert")
    public ResponseEntity<FixtureUpsertResponse> upsertFixtures(
            @Valid @RequestBody FixtureUpsertRequest request,
            HttpServletRequest httpRequest) {
        logger.info("POST /api/fixtures/upsert - recordId: {}, namespace: {}, items: {}, ttlSec: {}",
                request.getRecordId(), request.getNamespace(),
                request.getItems() != null ? request.getItems().size() : 0,
                request.getTtlSec());
        try {
            FixtureUpsertResponse response = fixtureService.upsert(request, httpRequest);
            logger.info("Fixture upsert completed successfully: upserted={}, expiresAt={}",
                    response.getUpserted(), response.getExpiresAt());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for fixture upsert: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to upsert fixtures", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据记录ID查询拦截规则应用状态
     */
    @GetMapping("/record/{recordId}/status")
    public ResponseEntity<Map<String, Object>> getStatusByRecordId(@PathVariable String recordId) {
        logger.info("GET /api/fixtures/record/{}/status - recordId: {}", recordId, recordId);
        try {
            Map<String, Object> status = fixtureService.getStatusByRecordId(recordId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Failed to get status by recordId: {}", recordId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据记录ID删除拦截规则（恢复服务、移除Sidecar、删除ConfigMap）
     */
    @DeleteMapping("/record/{recordId}")
    public ResponseEntity<Map<String, Object>> deleteByRecordId(@PathVariable String recordId) {
        logger.info("DELETE /api/fixtures/record/{} - recordId: {}", recordId, recordId);
        try {
            fixtureService.deleteByRecordId(recordId);
            return ResponseEntity.ok(Map.of(
                    "deleted", true,
                    "recordId", recordId
            ));
        } catch (Exception e) {
            logger.error("Failed to delete interception by recordId: {}", recordId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // 由于切换为 Envoy 配置管理，数据库查询接口已废弃

    // 由于切换为 Envoy 配置管理，匹配查询接口已废弃

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "FixtureController",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
