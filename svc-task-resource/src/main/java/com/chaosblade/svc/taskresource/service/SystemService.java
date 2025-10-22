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
import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.entity.System;
import com.chaosblade.svc.taskresource.repository.SystemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 系统服务类
 */
@Service
@Transactional
public class SystemService {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemService.class);
    
    @Autowired
    private SystemRepository systemRepository;
    
    /**
     * 获取所有系统列表
     */
    @Transactional(readOnly = true)
    public PageResponse<System> getAllSystems(String name, String owner, int page, int size) {
        logger.debug("Getting systems with filters - name: {}, owner: {}, page: {}, size: {}", name, owner, page, size);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<System> systemPage = systemRepository.findByNameAndOwner(name, owner, pageable);

        return PageResponse.of(systemPage.getContent(), systemPage.getTotalElements(), page, size);
    }
    
    /**
     * 根据ID获取系统详情
     */
    @Transactional(readOnly = true)
    public System getSystemById(Long systemId) {
        logger.debug("Getting system by id: {}", systemId);
        
        return systemRepository.findById(systemId)
                .orElseThrow(() -> new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + systemId));
    }
    
    /**
     * 根据名称获取系统
     */
    @Transactional(readOnly = true)
    public Optional<System> getSystemByName(String name) {
        logger.debug("Getting system by name: {}", name);
        return systemRepository.findByName(name);
    }
    
    /**
     * 创建新系统
     */
    public System createSystem(System system) {
        logger.info("Creating new system: {}", system.getName());
        
        // 检查系统名称是否已存在
        if (systemRepository.existsByName(system.getName())) {
            throw new BusinessException("SYSTEM_NAME_EXISTS", "系统名称已存在: " + system.getName());
        }
        
        // 设置默认环境
        if (system.getDefaultEnvironment() == null) {
            system.setDefaultEnvironment("default");
        }
        
        System savedSystem = systemRepository.save(system);
        logger.info("System created successfully with id: {}", savedSystem.getId());
        
        return savedSystem;
    }
    
    /**
     * 更新系统信息
     */
    public System updateSystem(Long systemId, System systemUpdate) {
        logger.info("Updating system: {}", systemId);
        
        System existingSystem = getSystemById(systemId);
        
        // 检查名称是否与其他系统冲突
        if (systemUpdate.getName() != null && !systemUpdate.getName().equals(existingSystem.getName())) {
            if (systemRepository.existsByName(systemUpdate.getName())) {
                throw new BusinessException("SYSTEM_NAME_EXISTS", "系统名称已存在: " + systemUpdate.getName());
            }
            existingSystem.setName(systemUpdate.getName());
        }
        
        // 更新其他字段
        if (systemUpdate.getDescription() != null) {
            existingSystem.setDescription(systemUpdate.getDescription());
        }
        if (systemUpdate.getOwner() != null) {
            existingSystem.setOwner(systemUpdate.getOwner());
        }
        if (systemUpdate.getDefaultEnvironment() != null) {
            existingSystem.setDefaultEnvironment(systemUpdate.getDefaultEnvironment());
        }
        
        System savedSystem = systemRepository.save(existingSystem);
        logger.info("System updated successfully: {}", savedSystem.getId());
        
        return savedSystem;
    }
    
    /**
     * 删除系统
     */
    public void deleteSystem(Long systemId) {
        logger.info("Deleting system: {}", systemId);
        
        System system = getSystemById(systemId);
        systemRepository.delete(system);
        
        logger.info("System deleted successfully: {}", systemId);
    }
    
    /**
     * 根据所有者获取系统列表
     */
    @Transactional(readOnly = true)
    public List<System> getSystemsByOwner(String owner) {
        logger.debug("Getting systems by owner: {}", owner);
        return systemRepository.findByOwner(owner);
    }
    
    /**
     * 检查系统是否存在
     */
    @Transactional(readOnly = true)
    public boolean existsById(Long systemId) {
        return systemRepository.existsById(systemId);
    }
}
