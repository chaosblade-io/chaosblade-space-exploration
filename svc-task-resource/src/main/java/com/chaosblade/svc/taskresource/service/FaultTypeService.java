package com.chaosblade.svc.taskresource.service;

import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.entity.FaultType;
import com.chaosblade.svc.taskresource.repository.FaultTypeRepository;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 故障类型服务类
 */
@Service
@Transactional
public class FaultTypeService {
    
    private static final Logger logger = LoggerFactory.getLogger(FaultTypeService.class);
    
    @Autowired
    private FaultTypeRepository faultTypeRepository;
    
    /**
     * 获取故障类型列表
     */
    @Transactional(readOnly = true)
    public PageResponse<FaultType> getFaultTypes(String category, String severityLevel, String status,
                                                String name, int page, int size) {
        logger.debug("Getting fault types with filters - category: {}, enabled: {}, faultCode: {}, name: {}, page: {}, size: {}",
                    category, severityLevel, status, name, page, size);

        // 将旧参数映射到新字段
        Boolean enabled = status != null ? "ACTIVE".equals(status) : null;
        String faultCode = severityLevel; // 临时映射，实际应该根据业务逻辑调整

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "category", "name"));
        Page<FaultType> faultTypePage = faultTypeRepository.findByConditions(category, enabled, faultCode, name, pageable);
        
        return PageResponse.of(faultTypePage.getContent(), faultTypePage.getTotalElements(), page, size);
    }
    
    /**
     * 根据ID获取故障类型详情
     */
    @Transactional(readOnly = true)
    public FaultType getFaultTypeById(Long faultTypeId) {
        logger.debug("Getting fault type by id: {}", faultTypeId);
        
        return faultTypeRepository.findById(faultTypeId)
                .orElseThrow(() -> new BusinessException("FAULT_TYPE_NOT_FOUND", "故障类型不存在: " + faultTypeId));
    }
    
    /**
     * 根据名称获取故障类型
     */
    @Transactional(readOnly = true)
    public FaultType getFaultTypeByName(String name) {
        logger.debug("Getting fault type by name: {}", name);
        
        return faultTypeRepository.findByName(name)
                .orElseThrow(() -> new BusinessException("FAULT_TYPE_NOT_FOUND", "故障类型不存在: " + name));
    }
    
    /**
     * 创建新故障类型
     */
    public FaultType createFaultType(FaultType faultType) {
        logger.info("Creating new fault type: {}", faultType.getName());
        
        // 检查故障类型名称是否已存在
        if (faultTypeRepository.existsByName(faultType.getName())) {
            throw new BusinessException("FAULT_TYPE_NAME_EXISTS", "故障类型名称已存在: " + faultType.getName());
        }
        
        // 设置默认值
        if (faultType.getEnabled() == null) {
            faultType.setEnabled(true);
        }

        // 设置默认显示顺序
        if (faultType.getDisplayOrder() == null) {
            faultType.setDisplayOrder(0);
        }
        
        FaultType savedFaultType = faultTypeRepository.save(faultType);
        logger.info("Fault type created successfully with id: {}", savedFaultType.getFaultTypeId());
        
        return savedFaultType;
    }
    
    /**
     * 更新故障类型信息
     */
    public FaultType updateFaultType(Long faultTypeId, FaultType faultTypeUpdate) {
        logger.info("Updating fault type: {}", faultTypeId);
        
        FaultType existingFaultType = getFaultTypeById(faultTypeId);
        
        // 检查名称是否与其他故障类型冲突
        if (faultTypeUpdate.getName() != null && !faultTypeUpdate.getName().equals(existingFaultType.getName())) {
            if (faultTypeRepository.existsByName(faultTypeUpdate.getName())) {
                throw new BusinessException("FAULT_TYPE_NAME_EXISTS", "故障类型名称已存在: " + faultTypeUpdate.getName());
            }
            existingFaultType.setName(faultTypeUpdate.getName());
        }
        
        // 更新其他字段
        if (faultTypeUpdate.getCategory() != null) {
            existingFaultType.setCategory(faultTypeUpdate.getCategory());
        }
        if (faultTypeUpdate.getDescription() != null) {
            existingFaultType.setDescription(faultTypeUpdate.getDescription());
        }
        if (faultTypeUpdate.getParamConfig() != null) {
            existingFaultType.setParamConfig(faultTypeUpdate.getParamConfig());
        }
        if (faultTypeUpdate.getEnabled() != null) {
            existingFaultType.setEnabled(faultTypeUpdate.getEnabled());
        }
        if (faultTypeUpdate.getDisplayOrder() != null) {
            existingFaultType.setDisplayOrder(faultTypeUpdate.getDisplayOrder());
        }
        
        FaultType savedFaultType = faultTypeRepository.save(existingFaultType);
        logger.info("Fault type updated successfully: {}", savedFaultType.getFaultTypeId());
        
        return savedFaultType;
    }
    
    /**
     * 删除故障类型
     */
    public void deleteFaultType(Long faultTypeId) {
        logger.info("Deleting fault type: {}", faultTypeId);
        
        FaultType faultType = getFaultTypeById(faultTypeId);
        faultTypeRepository.delete(faultType);
        
        logger.info("Fault type deleted successfully: {}", faultTypeId);
    }
    
    /**
     * 根据分类获取故障类型列表
     */
    @Transactional(readOnly = true)
    public List<FaultType> getFaultTypesByCategory(String category) {
        logger.debug("Getting fault types by category: {}", category);
        return faultTypeRepository.findByCategory(category);
    }
    
    /**
     * 根据支持的目标获取故障类型列表
     */
    @Transactional(readOnly = true)
    public List<FaultType> getFaultTypesByFaultCode(String code) {
        logger.debug("Getting fault types by fault code: {}", code);
        return faultTypeRepository.findByFaultCodeContaining(code);
    }
    
    /**
     * 获取所有分类
     */
    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        logger.debug("Getting all fault type categories");
        return faultTypeRepository.findAllCategories();
    }
    

    
    /**
     * 获取分类统计信息
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getCategoryStatistics() {
        logger.debug("Getting category statistics");
        List<Object[]> results = faultTypeRepository.countByCategory();
        return results.stream()
                .collect(Collectors.toMap(
                        result -> (String) result[0],
                        result -> (Long) result[1]
                ));
    }
    
    /**
     * 检查故障类型是否存在
     */
    @Transactional(readOnly = true)
    public boolean existsById(Long faultTypeId) {
        return faultTypeRepository.existsById(faultTypeId);
    }
}
