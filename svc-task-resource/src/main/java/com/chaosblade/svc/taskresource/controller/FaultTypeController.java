package com.chaosblade.svc.taskresource.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.entity.FaultType;
import com.chaosblade.svc.taskresource.service.FaultTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.Map;

/**
 * 故障类型管理控制器
 */
@RestController
@RequestMapping("/api")
public class FaultTypeController {
    
    private static final Logger logger = LoggerFactory.getLogger(FaultTypeController.class);
    
    @Autowired
    private FaultTypeService faultTypeService;
    
    /**
     * 获取故障类型列表
     * GET /api/fault-types
     */
    @GetMapping("/fault-types")
    public ApiResponse<PageResponse<FaultType>> getFaultTypes(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "severityLevel", required = false) String severityLevel,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        
        logger.info("GET /api/fault-types - category: {}, severityLevel: {}, status: {}, name: {}, page: {}, size: {}", 
                   category, severityLevel, status, name, page, size);
        
        PageResponse<FaultType> faultTypes = faultTypeService.getFaultTypes(category, severityLevel, status, name, page, size);
        return ApiResponse.success(faultTypes);
    }
    
    /**
     * 获取故障类型详情
     * GET /api/fault-types/{faultTypeId}
     */
    @GetMapping("/fault-types/{faultTypeId}")
    public ApiResponse<FaultType> getFaultTypeById(@PathVariable Long faultTypeId) {
        logger.info("GET /api/fault-types/{}", faultTypeId);
        
        FaultType faultType = faultTypeService.getFaultTypeById(faultTypeId);
        return ApiResponse.success(faultType);
    }
    
    /**
     * 根据名称获取故障类型详情
     * GET /api/fault-types/by-name/{name}
     */
    @GetMapping("/fault-types/by-name/{name}")
    public ApiResponse<FaultType> getFaultTypeByName(@PathVariable String name) {
        logger.info("GET /api/fault-types/by-name/{}", name);
        
        FaultType faultType = faultTypeService.getFaultTypeByName(name);
        return ApiResponse.success(faultType);
    }
    
    /**
     * 创建新故障类型
     * POST /api/fault-types
     */
    @PostMapping("/fault-types")
    public ApiResponse<FaultType> createFaultType(@Valid @RequestBody FaultType faultType) {
        logger.info("POST /api/fault-types - name: {}", faultType.getName());
        
        FaultType createdFaultType = faultTypeService.createFaultType(faultType);
        return ApiResponse.success(createdFaultType);
    }
    
    /**
     * 更新故障类型信息
     * PUT /api/fault-types/{faultTypeId}
     */
    @PutMapping("/fault-types/{faultTypeId}")
    public ApiResponse<FaultType> updateFaultType(@PathVariable Long faultTypeId,
                                                 @Valid @RequestBody FaultType faultType) {
        logger.info("PUT /api/fault-types/{}", faultTypeId);
        
        FaultType updatedFaultType = faultTypeService.updateFaultType(faultTypeId, faultType);
        return ApiResponse.success(updatedFaultType);
    }
    
    /**
     * 删除故障类型
     * DELETE /api/fault-types/{faultTypeId}
     */
    @DeleteMapping("/fault-types/{faultTypeId}")
    public ApiResponse<Void> deleteFaultType(@PathVariable Long faultTypeId) {
        logger.info("DELETE /api/fault-types/{}", faultTypeId);
        
        faultTypeService.deleteFaultType(faultTypeId);
        return ApiResponse.success();
    }
    
    /**
     * 根据分类获取故障类型列表
     * GET /api/fault-types/category/{category}
     */
    @GetMapping("/fault-types/category/{category}")
    public ApiResponse<List<FaultType>> getFaultTypesByCategory(@PathVariable String category) {
        logger.info("GET /api/fault-types/category/{}", category);
        
        List<FaultType> faultTypes = faultTypeService.getFaultTypesByCategory(category);
        return ApiResponse.success(faultTypes);
    }
    
    /**
     * 根据故障代码获取故障类型列表
     * GET /api/fault-types/code/{code}
     */
    @GetMapping("/fault-types/code/{code}")
    public ApiResponse<List<FaultType>> getFaultTypesByFaultCode(@PathVariable String code) {
        logger.info("GET /api/fault-types/code/{}", code);

        List<FaultType> faultTypes = faultTypeService.getFaultTypesByFaultCode(code);
        return ApiResponse.success(faultTypes);
    }
    
    /**
     * 获取所有分类
     * GET /api/fault-types/categories
     */
    @GetMapping("/fault-types/categories")
    public ApiResponse<List<String>> getAllCategories() {
        logger.info("GET /api/fault-types/categories");
        
        List<String> categories = faultTypeService.getAllCategories();
        return ApiResponse.success(categories);
    }
    

    
    /**
     * 获取分类统计信息
     * GET /api/fault-types/statistics/categories
     */
    @GetMapping("/fault-types/statistics/categories")
    public ApiResponse<Map<String, Long>> getCategoryStatistics() {
        logger.info("GET /api/fault-types/statistics/categories");
        
        Map<String, Long> statistics = faultTypeService.getCategoryStatistics();
        return ApiResponse.success(statistics);
    }
}
