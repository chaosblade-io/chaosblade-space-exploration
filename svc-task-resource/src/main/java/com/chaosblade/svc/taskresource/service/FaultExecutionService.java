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

import com.chaosblade.svc.taskresource.entity.FaultExecution;
import com.chaosblade.svc.taskresource.repository.FaultExecutionRepository;
import com.chaosblade.common.core.dto.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 故障执行服务 - 基于fault_injection_results表
 */
@Service
public class FaultExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(FaultExecutionService.class);
    
    @Autowired
    private FaultExecutionRepository faultExecutionRepository;
    
    /**
     * 获取所有故障执行记录
     */
    @Transactional(readOnly = true)
    public List<FaultExecution> getAllFaultExecutions() {
        logger.debug("Getting all fault executions");
        return faultExecutionRepository.findAll();
    }
    
    /**
     * 分页获取故障执行记录
     */
    @Transactional(readOnly = true)
    public PageResponse<FaultExecution> getFaultExecutions(Long executionId, Long topologyNodeId, 
                                                          String templateType, FaultExecution.ResultStatus resultStatus, 
                                                          int page, int size) {
        logger.debug("Getting fault executions with filters - executionId: {}, topologyNodeId: {}, templateType: {}, resultStatus: {}, page: {}, size: {}",
                    executionId, topologyNodeId, templateType, resultStatus, page, size);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FaultExecution> executionPage = faultExecutionRepository.findByConditions(
                executionId, topologyNodeId, templateType, resultStatus, pageable);
        
        return PageResponse.of(executionPage.getContent(), executionPage.getTotalElements(), page, size);
    }
    
    /**
     * 根据ID获取故障执行记录
     */
    @Transactional(readOnly = true)
    public FaultExecution getFaultExecutionById(Long id) {
        logger.debug("Getting fault execution by id: {}", id);
        return faultExecutionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("故障执行记录不存在: " + id));
    }
    
    /**
     * 创建故障执行记录
     */
    @Transactional
    public FaultExecution createFaultExecution(FaultExecution faultExecution) {
        logger.info("Creating new fault execution: executionId={}, topologyNodeId={}, templateType={}", 
                   faultExecution.getExecutionId(), faultExecution.getTopologyNodeId(), faultExecution.getTemplateType());
        
        faultExecution.setCreatedAt(LocalDateTime.now());
        
        FaultExecution savedExecution = faultExecutionRepository.save(faultExecution);
        logger.info("Fault execution created successfully with id: {}", savedExecution.getId());
        return savedExecution;
    }
    
    /**
     * 删除故障执行记录
     */
    @Transactional
    public void deleteFaultExecution(Long id) {
        logger.info("Deleting fault execution: {}", id);
        
        if (!faultExecutionRepository.existsById(id)) {
            throw new RuntimeException("故障执行记录不存在: " + id);
        }
        
        faultExecutionRepository.deleteById(id);
        logger.info("Fault execution deleted successfully: {}", id);
    }
    
    /**
     * 根据执行ID获取执行记录列表
     */
    @Transactional(readOnly = true)
    public List<FaultExecution> getFaultExecutionsByExecutionId(Long executionId) {
        logger.debug("Getting fault executions by execution id: {}", executionId);
        return faultExecutionRepository.findByExecutionId(executionId);
    }
    
    /**
     * 检查故障执行记录是否存在
     */
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return faultExecutionRepository.existsById(id);
    }
}
