package com.chaosblade.svc.taskresource.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.entity.ApiTopology;
import com.chaosblade.svc.taskresource.entity.System;
import com.chaosblade.svc.taskresource.service.ApiTopologyService;
import com.chaosblade.svc.taskresource.service.ExternalTopologySyncService;
import com.chaosblade.svc.taskresource.service.SystemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 系统管理控制器 */
@RestController
@RequestMapping("/api")
public class SystemController {

  private static final Logger logger = LoggerFactory.getLogger(SystemController.class);

  @Autowired private SystemService systemService;

  @Autowired private ApiTopologyService apiTopologyService;

  @Autowired private ExternalTopologySyncService externalTopologySyncService;

  /** 获取系统列表 GET /api/systems */
  @GetMapping("/systems")
  public ApiResponse<PageResponse<System>> getSystems(
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "owner", required = false) String owner,
      @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {

    logger.info(
        "GET /api/systems - name: {}, owner: {}, page: {}, size: {}", name, owner, page, size);

    // [[Before returning list, sync external topology data (systems/apis/topology) for missing
    // records
    try {
      externalTopologySyncService.syncBeforeList();
    } catch (Exception e) {
      // do not fail listing on sync errors
      logger.warn("External topology sync failed: {}", e.getMessage());
    }

    PageResponse<System> systems = systemService.getAllSystems(name, owner, page, size);
    return ApiResponse.success(systems);
  }

  /** 获取系统详情 GET /api/systems/{systemId} */
  @GetMapping("/systems/{systemId}")
  public ApiResponse<System> getSystemById(@PathVariable Long systemId) {
    logger.info("GET /api/systems/{}", systemId);

    System system = systemService.getSystemById(systemId);
    return ApiResponse.success(system);
  }

  /** 创建新系统 POST /api/systems */
  @PostMapping("/systems")
  public ApiResponse<System> createSystem(@Valid @RequestBody System system) {
    logger.info("POST /api/systems - name: {}", system.getName());

    System createdSystem = systemService.createSystem(system);
    return ApiResponse.success(createdSystem);
  }

  /** 更新系统信息 PUT /api/systems/{systemId} */
  @PutMapping("/systems/{systemId}")
  public ApiResponse<System> updateSystem(
      @PathVariable Long systemId, @Valid @RequestBody System system) {
    logger.info("PUT /api/systems/{}", systemId);

    System updatedSystem = systemService.updateSystem(systemId, system);
    return ApiResponse.success(updatedSystem);
  }

  /** 删除系统 DELETE /api/systems/{systemId} */
  @DeleteMapping("/systems/{systemId}")
  public ApiResponse<Void> deleteSystem(@PathVariable Long systemId) {
    logger.info("DELETE /api/systems/{}", systemId);

    systemService.deleteSystem(systemId);
    return ApiResponse.success();
  }

  /** 获取系统的拓扑列表 GET /api/systems/{systemId}/topologies */
  @GetMapping("/systems/{systemId}/topologies")
  public ApiResponse<PageResponse<ApiTopology>> getSystemTopologies(
      @PathVariable Long systemId,
      @RequestParam(value = "sourceVersion", required = false) String sourceVersion,
      @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {

    logger.info(
        "GET /api/systems/{}/topologies - sourceVersion: {}, page: {}, size: {}",
        systemId,
        sourceVersion,
        page,
        size);

    PageResponse<ApiTopology> topologies =
        apiTopologyService.getTopologiesBySystemId(systemId, sourceVersion, page, size);
    return ApiResponse.success(topologies);
  }

  /** 获取系统的最新拓扑 GET /api/systems/{systemId}/topologies/latest */
  @GetMapping("/systems/{systemId}/topologies/latest")
  public ApiResponse<ApiTopology> getLatestSystemTopology(@PathVariable Long systemId) {
    logger.info("GET /api/systems/{}/topologies/latest", systemId);

    ApiTopology topology = apiTopologyService.getLatestTopologyBySystemId(systemId);
    return ApiResponse.success(topology);
  }

  /** 创建系统拓扑 POST /api/systems/{systemId}/topologies */
  @PostMapping("/systems/{systemId}/topologies")
  public ApiResponse<ApiTopology> createSystemTopology(
      @PathVariable Long systemId, @Valid @RequestBody ApiTopology topology) {
    logger.info("POST /api/systems/{}/topologies - apiId: {}", systemId, topology.getApiId());

    topology.setSystemId(systemId);
    ApiTopology createdTopology = apiTopologyService.createTopology(topology);
    return ApiResponse.success(createdTopology);
  }
}
