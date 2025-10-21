package com.chaosblade.svc.taskresource.service;

import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.dto.CompleteTopologyDto;
import com.chaosblade.svc.taskresource.entity.ApiTopology;
import com.chaosblade.svc.taskresource.entity.ApiTopologyEdge;
import com.chaosblade.svc.taskresource.entity.ApiTopologyNode;
import com.chaosblade.svc.taskresource.repository.ApiTopologyEdgeRepository;
import com.chaosblade.svc.taskresource.repository.ApiTopologyNodeRepository;
import com.chaosblade.svc.taskresource.repository.ApiTopologyRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API拓扑服务类 */
@Service
@Transactional
public class ApiTopologyService {

  private static final Logger logger = LoggerFactory.getLogger(ApiTopologyService.class);

  @Autowired private ApiTopologyRepository apiTopologyRepository;

  @Autowired private ApiTopologyNodeRepository apiTopologyNodeRepository;

  @Autowired private ApiTopologyEdgeRepository apiTopologyEdgeRepository;

  @Autowired private SystemService systemService;

  @Autowired private ApiService apiService;

  /** 根据系统ID获取拓扑列表 */
  @Transactional(readOnly = true)
  public PageResponse<ApiTopology> getTopologiesBySystemId(
      Long systemId, String sourceVersion, int page, int size) {
    logger.debug(
        "Getting topologies for system: {}, sourceVersion: {}, page: {}, size: {}",
        systemId,
        sourceVersion,
        page,
        size);

    // 验证系统是否存在
    if (!systemService.existsById(systemId)) {
      throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + systemId);
    }

    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<ApiTopology> topologyPage =
        apiTopologyRepository.findBySystemIdAndSourceVersion(systemId, sourceVersion, pageable);

    return PageResponse.of(topologyPage.getContent(), topologyPage.getTotalElements(), page, size);
  }

  /** 根据系统ID获取最新拓扑 */
  @Transactional(readOnly = true)
  public ApiTopology getLatestTopologyBySystemId(Long systemId) {
    logger.debug("Getting latest topology for system: {}", systemId);

    // 验证系统是否存在
    if (!systemService.existsById(systemId)) {
      throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + systemId);
    }

    return apiTopologyRepository
        .findLatestBySystemId(systemId)
        .orElseThrow(() -> new BusinessException("TOPOLOGY_NOT_FOUND", "系统暂无拓扑数据: " + systemId));
  }

  /** 根据ID获取拓扑详情 */
  @Transactional(readOnly = true)
  public ApiTopology getTopologyById(Long topologyId) {
    logger.debug("Getting topology by id: {}", topologyId);

    return apiTopologyRepository
        .findById(topologyId)
        .orElseThrow(() -> new BusinessException("TOPOLOGY_NOT_FOUND", "拓扑不存在: " + topologyId));
  }

  /** 创建新拓扑 */
  public ApiTopology createTopology(ApiTopology topology) {
    logger.info(
        "Creating new topology for system: {} and api: {}",
        topology.getSystemId(),
        topology.getApiId());

    // 验证系统是否存在
    if (!systemService.existsById(topology.getSystemId())) {
      throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + topology.getSystemId());
    }

    // 验证API是否存在
    if (!apiService.existsById(topology.getApiId())) {
      throw new BusinessException("API_NOT_FOUND", "API不存在: " + topology.getApiId());
    }

    // 检查拓扑是否已存在
    if (apiTopologyRepository.existsBySystemIdAndApiId(
        topology.getSystemId(), topology.getApiId())) {
      throw new BusinessException(
          "TOPOLOGY_EXISTS",
          String.format("拓扑已存在: 系统ID %d, API ID %d", topology.getSystemId(), topology.getApiId()));
    }

    ApiTopology savedTopology = apiTopologyRepository.save(topology);
    logger.info("Topology created successfully with id: {}", savedTopology.getId());

    return savedTopology;
  }

  /** 更新拓扑信息 */
  public ApiTopology updateTopology(Long topologyId, ApiTopology topologyUpdate) {
    logger.info("Updating topology: {}", topologyId);

    ApiTopology existingTopology = getTopologyById(topologyId);

    // 更新字段
    if (topologyUpdate.getSourceVersion() != null) {
      existingTopology.setSourceVersion(topologyUpdate.getSourceVersion());
    }
    if (topologyUpdate.getNotes() != null) {
      existingTopology.setNotes(topologyUpdate.getNotes());
    }
    if (topologyUpdate.getDiscoveredAt() != null) {
      existingTopology.setDiscoveredAt(topologyUpdate.getDiscoveredAt());
    }

    ApiTopology savedTopology = apiTopologyRepository.save(existingTopology);
    logger.info("Topology updated successfully: {}", savedTopology.getId());

    return savedTopology;
  }

  /** 删除拓扑 */
  public void deleteTopology(Long topologyId) {
    logger.info("Deleting topology: {}", topologyId);

    ApiTopology topology = getTopologyById(topologyId);

    // 删除相关的节点
    apiTopologyNodeRepository.deleteByTopologyId(topologyId);

    // 删除拓扑
    apiTopologyRepository.delete(topology);

    logger.info("Topology deleted successfully: {}", topologyId);
  }

  /** 获取拓扑的节点列表 */
  @Transactional(readOnly = true)
  public PageResponse<ApiTopologyNode> getTopologyNodes(
      Long topologyId, String protocol, String name, Integer layer, int page, int size) {
    logger.debug(
        "Getting nodes for topology: {}, protocol: {}, name: {}, layer: {}, page: {}, size: {}",
        topologyId,
        protocol,
        name,
        layer,
        page,
        size);

    // 验证拓扑是否存在
    if (!apiTopologyRepository.existsById(topologyId)) {
      throw new BusinessException("TOPOLOGY_NOT_FOUND", "拓扑不存在: " + topologyId);
    }

    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "id"));
    Page<ApiTopologyNode> nodePage =
        apiTopologyNodeRepository.findByTopologyId(topologyId, pageable);

    return PageResponse.of(nodePage.getContent(), nodePage.getTotalElements(), page, size);
  }

  /** 根据API ID查询完整拓扑信息 */
  @Transactional(readOnly = true)
  public CompleteTopologyDto getCompleteTopologyByApiId(Long apiId) {
    logger.debug("Getting complete topology for API: {}", apiId);

    // 查找该API的拓扑
    List<ApiTopology> topologies = apiTopologyRepository.findByApiId(apiId);
    if (topologies.isEmpty()) {
      throw new BusinessException("TOPOLOGY_NOT_FOUND", "API拓扑不存在: " + apiId);
    }

    // 取最新的拓扑（假设按创建时间排序）
    ApiTopology topology = topologies.get(0);

    // 查询拓扑的所有节点
    List<ApiTopologyNode> nodes = apiTopologyNodeRepository.findByTopologyId(topology.getId());

    // 查询拓扑的所有边
    List<ApiTopologyEdge> edges = apiTopologyEdgeRepository.findByTopologyId(topology.getId());

    return new CompleteTopologyDto(topology, nodes, edges);
  }

  /** 检查拓扑是否存在 */
  @Transactional(readOnly = true)
  public boolean existsById(Long topologyId) {
    return apiTopologyRepository.existsById(topologyId);
  }
}
