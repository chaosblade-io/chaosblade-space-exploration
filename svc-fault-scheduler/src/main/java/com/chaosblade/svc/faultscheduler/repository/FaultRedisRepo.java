package com.chaosblade.svc.faultscheduler.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 故障信息 Redis 持久化仓库
 * 负责故障元数据的存储、检索和删除
 */
@Repository
public class FaultRedisRepo {
    
    private static final Logger logger = LoggerFactory.getLogger(FaultRedisRepo.class);
    
    private static final String FAULT_KEY_PREFIX = "faults:";
    private static final String FAULT_INDEX_KEY = "faults:index";
    
    private final StringRedisTemplate redis;
    
    public FaultRedisRepo(StringRedisTemplate redis) {
        this.redis = redis;
    }
    
    /**
     * 保存故障信息
     * 
     * @param bladeName 故障名称
     * @param data 故障数据
     * @param ttlSec TTL 秒数，0 表示不过期
     */
    public void save(String bladeName, Map<String, String> data, long ttlSec) {
        String key = FAULT_KEY_PREFIX + bladeName;
        
        try {
            logger.debug("Saving fault data for bladeName: {}, ttl: {}s", bladeName, ttlSec);
            
            // 保存故障数据
            redis.opsForHash().putAll(key, data);
            
            // 设置 TTL
            if (ttlSec > 0) {
                redis.expire(key, Duration.ofSeconds(ttlSec));
                logger.debug("Set TTL for fault {}: {}s", bladeName, ttlSec);
            }
            
            // 添加到索引
            redis.opsForSet().add(FAULT_INDEX_KEY, bladeName);
            
            logger.info("Successfully saved fault data for bladeName: {}", bladeName);
        } catch (Exception e) {
            logger.error("Failed to save fault data for bladeName: {}", bladeName, e);
            throw new RuntimeException("Failed to save fault data", e);
        }
    }
    
    /**
     * 获取故障信息
     * 
     * @param bladeName 故障名称
     * @return 故障数据，如果不存在返回空 Map
     */
    public Map<Object, Object> get(String bladeName) {
        String key = FAULT_KEY_PREFIX + bladeName;
        
        try {
            logger.debug("Retrieving fault data for bladeName: {}", bladeName);
            
            Map<Object, Object> data = redis.opsForHash().entries(key);
            
            if (data.isEmpty()) {
                logger.debug("No fault data found for bladeName: {}", bladeName);
            } else {
                logger.debug("Retrieved fault data for bladeName: {}, fields: {}", 
                           bladeName, data.keySet());
            }
            
            return data;
        } catch (Exception e) {
            logger.error("Failed to retrieve fault data for bladeName: {}", bladeName, e);
            throw new RuntimeException("Failed to retrieve fault data", e);
        }
    }
    
    /**
     * 删除故障信息
     * 
     * @param bladeName 故障名称
     */
    public void delete(String bladeName) {
        String key = FAULT_KEY_PREFIX + bladeName;
        
        try {
            logger.debug("Deleting fault data for bladeName: {}", bladeName);
            
            // 删除故障数据
            redis.delete(key);
            
            // 从索引中移除
            redis.opsForSet().remove(FAULT_INDEX_KEY, bladeName);
            
            logger.info("Successfully deleted fault data for bladeName: {}", bladeName);
        } catch (Exception e) {
            logger.error("Failed to delete fault data for bladeName: {}", bladeName, e);
            throw new RuntimeException("Failed to delete fault data", e);
        }
    }
    
    /**
     * 检查故障是否存在
     * 
     * @param bladeName 故障名称
     * @return 是否存在
     */
    public boolean exists(String bladeName) {
        String key = FAULT_KEY_PREFIX + bladeName;
        
        try {
            Boolean exists = redis.hasKey(key);
            logger.debug("Fault existence check for bladeName: {}, exists: {}", bladeName, exists);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("Failed to check fault existence for bladeName: {}", bladeName, e);
            return false;
        }
    }
    
    /**
     * 获取所有故障名称
     * 
     * @return 故障名称集合
     */
    public Set<String> getAllFaultNames() {
        try {
            logger.debug("Retrieving all fault names from index");
            
            Set<String> faultNames = redis.opsForSet().members(FAULT_INDEX_KEY);
            
            if (faultNames == null) {
                logger.debug("No fault names found in index");
                return Set.of();
            }
            
            logger.debug("Retrieved {} fault names from index", faultNames.size());
            return faultNames;
        } catch (Exception e) {
            logger.error("Failed to retrieve fault names from index", e);
            throw new RuntimeException("Failed to retrieve fault names", e);
        }
    }
    
    /**
     * 更新故障状态
     * 
     * @param bladeName 故障名称
     * @param status 新状态
     */
    public void updateStatus(String bladeName, String status) {
        String key = FAULT_KEY_PREFIX + bladeName;
        
        try {
            logger.debug("Updating status for bladeName: {}, status: {}", bladeName, status);
            
            redis.opsForHash().put(key, "status", status);
            redis.opsForHash().put(key, "lastUpdated", String.valueOf(System.currentTimeMillis()));
            
            logger.debug("Successfully updated status for bladeName: {}", bladeName);
        } catch (Exception e) {
            logger.error("Failed to update status for bladeName: {}", bladeName, e);
            throw new RuntimeException("Failed to update fault status", e);
        }
    }
}
