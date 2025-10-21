package com.chaosblade.svc.taskresource.service;

import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.entity.FaultConfig;
import com.chaosblade.svc.taskresource.repository.FaultConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FaultConfigService {

  private static final Logger logger = LoggerFactory.getLogger(FaultConfigService.class);

  @Autowired private FaultConfigRepository repository;

  @Transactional(readOnly = true)
  public PageResponse<FaultConfig> pageQuery(
      Long nodeId, String type, Long taskId, int page, int size) {
    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
    Page<FaultConfig> p = repository.findByConditions(nodeId, type, taskId, pageable);
    return PageResponse.of(p.getContent(), p.getTotalElements(), page, size);
  }

  @Transactional(readOnly = true)
  public FaultConfig getById(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new BusinessException("FAULT_CONFIG_NOT_FOUND", "故障配置不存在: " + id));
  }

  public FaultConfig create(FaultConfig cfg) {
    if (cfg.getFaultscript() == null || cfg.getFaultscript().isBlank()) {
      throw new BusinessException("FAULT_SCRIPT_INVALID", "faultscript 不能为空");
    }
    FaultConfig saved = repository.save(cfg);
    logger.info(
        "FaultConfig created id={}, nodeId={}, taskId={} ",
        saved.getId(),
        saved.getNodeId(),
        saved.getTaskId());
    return saved;
  }

  public FaultConfig update(Long id, FaultConfig cfg) {
    FaultConfig existing = getById(id);
    cfg.setId(existing.getId());
    FaultConfig saved = repository.save(cfg);
    logger.info(
        "FaultConfig updated id={}, nodeId={}, taskId={} ",
        saved.getId(),
        saved.getNodeId(),
        saved.getTaskId());
    return saved;
  }

  public void delete(Long id) {
    FaultConfig existing = getById(id);
    repository.delete(existing);
    logger.info("FaultConfig deleted id={} ", id);
  }
}
