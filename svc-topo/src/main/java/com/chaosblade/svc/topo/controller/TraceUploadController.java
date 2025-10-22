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

import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.model.trace.TraceData;
import com.chaosblade.svc.topo.service.TopologyConverterService;
import com.chaosblade.svc.topo.service.TraceParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Trace文件上传控制器
 *
 * 提供REST API接口：
 * 1. 文件上传和解析
 * 2. 拓扑图生成
 */
@RestController
@RequestMapping("/api/trace")
@CrossOrigin(origins = "*")
public class TraceUploadController {

    private static final Logger logger = LoggerFactory.getLogger(TraceUploadController.class);

    @Autowired
    private TraceParserService traceParserService;

    @Autowired
    private TopologyConverterService topologyConverterService;

    /**
     * 上传多个trace文件并生成合并的拓扑可视化
     */
    @PostMapping("/upload/batch")
    public ResponseEntity<Map<String, Object>> uploadMultipleTraceFiles(@RequestParam("files") MultipartFile[] files) {
        logger.info("收到批量trace文件上传请求: {} 个文件", files.length);

        try {
            // 1. 验证文件数量
            if (files.length == 0) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("未选择文件", "NO_FILES"));
            }

            if (files.length > 10) {  // 限制最多10个文件
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("文件数量超过限制（最多10个）", "TOO_MANY_FILES"));
            }

            // 2. 验证每个文件
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(createErrorResponse("存在空文件: " + file.getOriginalFilename(), "EMPTY_FILE"));
                }

                if (!isValidTraceFile(file)) {
                    return ResponseEntity.badRequest()
                            .body(createErrorResponse("无效的trace文件格式: " + file.getOriginalFilename(), "INVALID_FORMAT"));
                }
            }

            // 3. 解析所有trace文件
            List<TraceData> traceDataList = new ArrayList<>();
            List<String> fileNames = new ArrayList<>();
            long totalSize = 0;

            for (MultipartFile file : files) {
                logger.info("解析文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

                TraceData traceData = traceParserService.parseTraceFile(file);
                if (traceData != null && traceData.getData() != null && !traceData.getData().isEmpty()) {
                    traceDataList.add(traceData);
                    fileNames.add(file.getOriginalFilename());
                    totalSize += file.getSize();
                } else {
                    logger.warn("跳过空的trace文件: {}", file.getOriginalFilename());
                }
            }

            if (traceDataList.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("所有trace文件内容为空", "ALL_FILES_EMPTY"));
            }

            // 4. 合并trace数据
            TraceData mergedTraceData = traceParserService.mergeTraceData(traceDataList);

            // 5. 转换为拓扑图
            TopologyGraph topology = topologyConverterService.convertTraceToTopology(mergedTraceData);
            // 存储当前拓扑
            topologyConverterService.setCurrentTopology(topology);

            // 6. 提取统计信息
            Set<String> serviceNames = traceParserService.extractServiceNames(mergedTraceData);
            TopologyGraph.GraphStatistics statistics = topology.getStatistics();

            // 7. 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "批量trace文件处理成功");
            response.put("fileCount", traceDataList.size());
            response.put("fileNames", fileNames);
            response.put("totalSize", totalSize);
            response.put("processedAt", System.currentTimeMillis());

            // 数据统计
            Map<String, Object> stats = new HashMap<>();
            stats.put("serviceCount", serviceNames.size());
            stats.put("nodeCount", statistics.getNodeCount());
            stats.put("edgeCount", statistics.getEdgeCount());
            stats.put("serviceNames", serviceNames);
            stats.put("nodeTypeCount", statistics.getNodeTypeCount());
            stats.put("edgeTypeCount", statistics.getEdgeTypeCount());

            // 每个文件的详细信息
            List<Map<String, Object>> fileDetails = new ArrayList<>();
            for (int i = 0; i < traceDataList.size(); i++) {
                TraceData data = traceDataList.get(i);
                Map<String, Object> detail = new HashMap<>();
                detail.put("fileName", fileNames.get(i));
                detail.put("traceCount", data.getData().size());
                detail.put("services", traceParserService.extractServiceNames(data));
                fileDetails.add(detail);
            }
            stats.put("fileDetails", fileDetails);

            response.put("statistics", stats);

            // 生成的数据
            response.put("topology", topology);

            logger.info("批量trace文件处理完成: {} 个文件, {} 个服务, {} 个节点, {} 条边",
                       traceDataList.size(), serviceNames.size(), statistics.getNodeCount(), statistics.getEdgeCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("处理批量trace文件时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("处理文件时发生错误: " + e.getMessage(), "BATCH_PROCESSING_ERROR"));
        }
    }
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadTraceFile(@RequestParam("file") MultipartFile file) {
        logger.info("收到trace文件上传请求: {}, 大小: {} bytes",
                   file.getOriginalFilename(), file.getSize());

        try {
            // 1. 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("文件为空", "EMPTY_FILE"));
            }

            if (!isValidTraceFile(file)) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("不是有效的trace文件格式", "INVALID_FORMAT"));
            }

            // 2. 解析trace文件
            TraceData traceData = traceParserService.parseTraceFile(file);
            if (traceData == null || traceData.getData() == null || traceData.getData().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("trace文件内容为空", "EMPTY_TRACE_DATA"));
            }

            // 3. 转换为拓扑图
            TopologyGraph topology = topologyConverterService.convertTraceToTopology(traceData);
            // 存储当前拓扑
            topologyConverterService.setCurrentTopology(topology);

            // 4. 提取统计信息
            Set<String> serviceNames = traceParserService.extractServiceNames(traceData);
            TopologyGraph.GraphStatistics statistics = topology.getStatistics();

            // 5. 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "trace文件处理成功");
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("processedAt", System.currentTimeMillis());

            // 数据统计
            Map<String, Object> stats = new HashMap<>();
            stats.put("serviceCount", serviceNames.size());
            stats.put("nodeCount", statistics.getNodeCount());
            stats.put("edgeCount", statistics.getEdgeCount());
            stats.put("serviceNames", serviceNames);
            stats.put("nodeTypeCount", statistics.getNodeTypeCount());
            stats.put("edgeTypeCount", statistics.getEdgeTypeCount());
            response.put("statistics", stats);

            // 生成的数据
            response.put("topology", topology);

            logger.info("trace文件处理完成: {} 个服务, {} 个节点, {} 条边",
                       serviceNames.size(), statistics.getNodeCount(), statistics.getEdgeCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("处理trace文件时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("处理文件时发生错误: " + e.getMessage(), "PROCESSING_ERROR"));
        }
    }

    /**
     * 仅解析trace文件，返回基本信息
     */
    @PostMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseTraceFile(@RequestParam("file") MultipartFile file) {
        logger.info("收到trace文件解析请求: {}", file.getOriginalFilename());

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("文件为空", "EMPTY_FILE"));
            }

            // 解析trace文件
            TraceData traceData = traceParserService.parseTraceFile(file);
            Set<String> serviceNames = traceParserService.extractServiceNames(traceData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "trace文件解析成功");
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("serviceCount", serviceNames.size());
            response.put("serviceNames", serviceNames);
            response.put("traceRecordCount", traceData.getData() != null ? traceData.getData().size() : 0);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("解析trace文件时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("解析文件时发生错误: " + e.getMessage(), "PARSING_ERROR"));
        }
    }

    /**
     * 基于JSON内容生成拓扑图（用于测试）
     */
    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> generateTopologyFromJson(@RequestBody String traceJson) {
        logger.info("收到JSON内容拓扑生成请求，内容长度: {} 字符", traceJson.length());

        try {
            // 解析JSON内容
            TraceData traceData = traceParserService.parseTraceContent(traceJson);

            // 转换为拓扑图
            TopologyGraph topology = topologyConverterService.convertTraceToTopology(traceData);
            // 存储当前拓扑
            topologyConverterService.setCurrentTopology(topology);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "拓扑图生成成功");
            response.put("topology", topology);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("从JSON生成拓扑图时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("生成拓扑图时发生错误: " + e.getMessage(), "GENERATION_ERROR"));
        }
    }

    /**
     * 验证文件格式
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateTraceFile(@RequestParam("file") MultipartFile file) {
        logger.info("收到文件格式验证请求: {}", file.getOriginalFilename());

        try {
            boolean isValid = isValidTraceFile(file);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("isValid", isValid);
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("message", isValid ? "文件格式有效" : "文件格式无效");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("验证文件格式时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("验证文件时发生错误: " + e.getMessage(), "VALIDATION_ERROR"));
        }
    }

    /**
     * 获取支持的文件格式信息
     */
    @GetMapping("/formats")
    public ResponseEntity<Map<String, Object>> getSupportedFormats() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("supportedFormats", new String[]{"json"});
        response.put("maxFileSize", "50MB");
        response.put("description", "支持OpenTelemetry Jaeger格式的trace文件");
        response.put("example", "trace-*.json");

        return ResponseEntity.ok(response);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "TraceUploadController");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 验证是否为有效的trace文件
     */
    private boolean isValidTraceFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 检查文件扩展名
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".json")) {
            return false;
        }

        // 检查文件大小（不超过50MB）
        if (file.getSize() > 50 * 1024 * 1024) {
            return false;
        }

        // 检查文件内容格式
        return traceParserService.validateTraceFormat(file);
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message, String errorCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("errorCode", errorCode);
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }
}
