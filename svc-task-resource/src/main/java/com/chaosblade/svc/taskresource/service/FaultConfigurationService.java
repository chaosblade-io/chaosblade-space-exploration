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

package com.chaosblade.svc.taskresource.service;

import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.entity.FaultConfiguration;
import com.chaosblade.svc.taskresource.repository.FaultConfigurationRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 故障配置服务 */
@Service
public class FaultConfigurationService {

  private static final Logger logger = LoggerFactory.getLogger(FaultConfigurationService.class);

  @Autowired private FaultConfigurationRepository faultConfigurationRepository;

  /** 分页获取故障配置 */
  @Transactional(readOnly = true)
  public PageResponse<FaultConfiguration> getFaultConfigurations(
      Long faultTypeId, Long configId, Long nodeId, int page, int size) {
    logger.debug(
        "Getting fault configurations with filters - faultTypeId: {}, configId: {}, nodeId: {},"
            + " page: {}, size: {}",
        faultTypeId,
        configId,
        nodeId,
        page,
        size);

    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<FaultConfiguration> configPage =
        faultConfigurationRepository.findByConditions(faultTypeId, configId, nodeId, pageable);

    return PageResponse.of(configPage.getContent(), configPage.getTotalElements(), page, size);
  }

  /** 根据ID获取故障配置 */
  @Transactional(readOnly = true)
  public FaultConfiguration getFaultConfigurationById(Long id) {
    logger.debug("Getting fault configuration by id: {}", id);
    return faultConfigurationRepository
        .findById(id)
        .orElseThrow(() -> new RuntimeException("故障配置不存在: " + id));
  }

  /** 创建故障配置 */
  @Transactional
  public FaultConfiguration createFaultConfiguration(FaultConfiguration faultConfiguration) {
    logger.info(
        "Creating new fault configuration: configId={}, nodeId={}, faultTypeId={}",
        faultConfiguration.getConfigId(),
        faultConfiguration.getNodeId(),
        faultConfiguration.getFaultTypeId());

    faultConfiguration.setCreatedAt(LocalDateTime.now());
    faultConfiguration.setUpdatedAt(LocalDateTime.now());

    return faultConfigurationRepository.save(faultConfiguration);
  }

  /** 删除故障配置 */
  @Transactional
  public void deleteFaultConfiguration(Long id) {
    logger.info("Deleting fault configuration: {}", id);

    if (!faultConfigurationRepository.existsById(id)) {
      throw new RuntimeException("故障配置不存在: " + id);
    }

    faultConfigurationRepository.deleteById(id);
  }

  /** 检查故障配置是否存在 */
  @Transactional(readOnly = true)
  public boolean existsById(Long id) {
    return faultConfigurationRepository.existsById(id);
  }
}
