package com.chaosblade.svc.taskresource.service;

import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.entity.Api;
import com.chaosblade.svc.taskresource.repository.ApiRepository;
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
 * API服务类
 */
@Service
@Transactional
public class ApiService {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiService.class);
    
    @Autowired
    private ApiRepository apiRepository;
    
    @Autowired
    private SystemService systemService;
    
    /**
     * 根据系统ID获取API列表
     */
    @Transactional(readOnly = true)
    public PageResponse<Api> getApisBySystemId(Long systemId, String method, String path,
                                              String tags, String version, int page, int size) {
        logger.debug("Getting APIs for system: {}, method: {}, path: {}, tags: {}, version: {}, page: {}, size: {}",
                    systemId, method, path, tags, version, page, size);

        // 验证系统是否存在
        if (!systemService.existsById(systemId)) {
            throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + systemId);
        }

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "path", "method"));
        Page<Api> apiPage = apiRepository.findByConditions(systemId, method, path, tags, version, pageable);

        return PageResponse.of(apiPage.getContent(), apiPage.getTotalElements(), page, size);
    }
    
    /**
     * 根据系统ID和操作ID获取API详情
     */
    @Transactional(readOnly = true)
    public Api getApiBySystemIdAndOperationId(Long systemId, String operationId) {
        logger.debug("Getting API by systemId: {} and operationId: {}", systemId, operationId);
        
        // 验证系统是否存在
        if (!systemService.existsById(systemId)) {
            throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + systemId);
        }
        
        return apiRepository.findBySystemIdAndOperationId(systemId, operationId)
                .orElseThrow(() -> new BusinessException("API_NOT_FOUND", 
                        String.format("API不存在: systemId=%d, operationId=%s", systemId, operationId)));
    }
    
    /**
     * 根据ID获取API详情
     */
    @Transactional(readOnly = true)
    public Api getApiById(Long apiId) {
        logger.debug("Getting API by id: {}", apiId);
        
        return apiRepository.findById(apiId)
                .orElseThrow(() -> new BusinessException("API_NOT_FOUND", "API不存在: " + apiId));
    }
    
    /**
     * 创建新API
     */
    public Api createApi(Api api) {
        logger.info("Creating new API: {} for system: {}", api.getOperationId(), api.getSystemId());
        
        // 验证系统是否存在
        if (!systemService.existsById(api.getSystemId())) {
            throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + api.getSystemId());
        }
        
        // 检查操作ID在系统内是否已存在
        if (apiRepository.existsBySystemIdAndOperationId(api.getSystemId(), api.getOperationId())) {
            throw new BusinessException("API_OPERATION_ID_EXISTS", 
                    String.format("操作ID在系统内已存在: %s (系统ID: %d)", api.getOperationId(), api.getSystemId()));
        }

        Api savedApi = apiRepository.save(api);
        logger.info("API created successfully with id: {}", savedApi.getId());
        
        return savedApi;
    }
    
    /**
     * 更新API信息
     */
    public Api updateApi(Long apiId, Api apiUpdate) {
        logger.info("Updating API: {}", apiId);
        
        Api existingApi = getApiById(apiId);
        
        // 检查操作ID是否与同系统内其他API冲突
        if (apiUpdate.getOperationId() != null && !apiUpdate.getOperationId().equals(existingApi.getOperationId())) {
            if (apiRepository.existsBySystemIdAndOperationId(existingApi.getSystemId(), apiUpdate.getOperationId())) {
                throw new BusinessException("API_OPERATION_ID_EXISTS", 
                        String.format("操作ID在系统内已存在: %s (系统ID: %d)", 
                                apiUpdate.getOperationId(), existingApi.getSystemId()));
            }
            existingApi.setOperationId(apiUpdate.getOperationId());
        }
        
        // 更新其他字段
        if (apiUpdate.getPath() != null) {
            existingApi.setPath(apiUpdate.getPath());
        }
        if (apiUpdate.getMethod() != null) {
            existingApi.setMethod(apiUpdate.getMethod());
        }
        if (apiUpdate.getSummary() != null) {
            existingApi.setSummary(apiUpdate.getSummary());
        }
        if (apiUpdate.getTags() != null) {
            existingApi.setTags(apiUpdate.getTags());
        }
        if (apiUpdate.getVersion() != null) {
            existingApi.setVersion(apiUpdate.getVersion());
        }
        if (apiUpdate.getBaseUrl() != null) {
            existingApi.setBaseUrl(apiUpdate.getBaseUrl());
        }
        if (apiUpdate.getContentType() != null) {
            existingApi.setContentType(apiUpdate.getContentType());
        }
        if (apiUpdate.getHeadersTemplate() != null) {
            existingApi.setHeadersTemplate(apiUpdate.getHeadersTemplate());
        }
        if (apiUpdate.getAuthType() != null) {
            existingApi.setAuthType(apiUpdate.getAuthType());
        }
        if (apiUpdate.getAuthTemplate() != null) {
            existingApi.setAuthTemplate(apiUpdate.getAuthTemplate());
        }
        if (apiUpdate.getPathParams() != null) {
            existingApi.setPathParams(apiUpdate.getPathParams());
        }
        if (apiUpdate.getQueryParams() != null) {
            existingApi.setQueryParams(apiUpdate.getQueryParams());
        }
        if (apiUpdate.getBodyTemplate() != null) {
            existingApi.setBodyTemplate(apiUpdate.getBodyTemplate());
        }
        if (apiUpdate.getVariables() != null) {
            existingApi.setVariables(apiUpdate.getVariables());
        }
        if (apiUpdate.getTimeoutMs() != null) {
            existingApi.setTimeoutMs(apiUpdate.getTimeoutMs());
        }
        if (apiUpdate.getRetryConfig() != null) {
            existingApi.setRetryConfig(apiUpdate.getRetryConfig());
        }
        
        Api savedApi = apiRepository.save(existingApi);
        logger.info("API updated successfully: {}", savedApi.getId());
        
        return savedApi;
    }
    
    /**
     * 删除API
     */
    public void deleteApi(Long apiId) {
        logger.info("Deleting API: {}", apiId);
        
        Api api = getApiById(apiId);
        apiRepository.delete(api);
        
        logger.info("API deleted successfully: {}", apiId);
    }
    
    /**
     * 根据标签获取API列表
     */
    @Transactional(readOnly = true)
    public List<Api> getApisByTag(Long systemId, String tag) {
        logger.debug("Getting APIs by tag: {} for system: {}", tag, systemId);
        
        // 验证系统是否存在
        if (!systemService.existsById(systemId)) {
            throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + systemId);
        }
        
        return apiRepository.findBySystemIdAndTag(systemId, tag);
    }
    
    /**
     * 检查API是否存在
     */
    @Transactional(readOnly = true)
    public boolean existsById(Long apiId) {
        return apiRepository.existsById(apiId);
    }
    
    /**
     * 统计系统中的API数量
     */
    @Transactional(readOnly = true)
    public long countBySystemId(Long systemId) {
        return apiRepository.countBySystemId(systemId);
    }
}
