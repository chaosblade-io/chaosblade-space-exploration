package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 拓扑数据缓存服务
 *
 * <p>实现基于哈希表的拓扑数据缓存，按时间范围进行索引 使用start转秒后整除15秒的结果作为时间索引，通过参数控制缓存项数量
 */
@Service
public class TopologyCacheService {

  private static final Logger logger = LoggerFactory.getLogger(TopologyCacheService.class);

  // 缓存最大容量，可通过配置文件设置
  @Value("${topology.cache.max-size:100}")
  private int maxCacheSize;

  // 缓存存储结构：使用LinkedHashMap实现LRU淘汰策略
  private final Map<TimeKey, TopologyGraph> cache =
      new LinkedHashMap<TimeKey, TopologyGraph>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TimeKey, TopologyGraph> eldest) {
          // 当缓存大小超过最大容量时，移除最老的条目
          return size() > maxCacheSize;
        }
      };

  // 读写锁保证线程安全
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  /** 时间键类，用于作为缓存的键 */
  public static class TimeKey {
    private final long start;
    private final long end;
    private final int timeIndex; // start转秒后整除15秒的结果

    public TimeKey(long start, long end) {
      this.start = start;
      this.end = end;
      // 使用start转秒后整除15秒的结果作为时间索引
      this.timeIndex = (int) ((start / 1000) / 15); // 毫秒转秒后整除15
    }

    public long getStart() {
      return start;
    }

    public long getEnd() {
      return end;
    }

    public int getTimeIndex() {
      return timeIndex;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TimeKey timeKey = (TimeKey) o;

      if (start != timeKey.start) return false;
      return end == timeKey.end;
    }

    @Override
    public int hashCode() {
      int result = (int) (start ^ (start >>> 32));
      result = 31 * result + (int) (end ^ (end >>> 32));
      return result;
    }

    @Override
    public String toString() {
      return "TimeKey{" + "start=" + start + ", end=" + end + ", timeIndex=" + timeIndex + '}';
    }
  }

  @PostConstruct
  public void init() {
    logger.info("拓扑数据缓存服务初始化完成，最大缓存容量: {}", maxCacheSize);
  }

  /**
   * 将拓扑图存入缓存
   *
   * @param start 查询开始时间（微秒）
   * @param end 查询结束时间（微秒）
   * @param topologyGraph 拓扑图数据
   */
  public void put(long start, long end, TopologyGraph topologyGraph) {
    TimeKey key = new TimeKey(start, end);

    lock.writeLock().lock();
    try {
      cache.put(key, topologyGraph);
      logger.debug("拓扑数据已存入缓存，时间范围: {}-{}，时间索引: {}", start, end, key.getTimeIndex());
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 从缓存中获取拓扑图
   *
   * @param start 查询开始时间（微秒）
   * @param end 查询结束时间（微秒）
   * @return 拓扑图数据，如果未找到则返回null
   */
  public TopologyGraph get(long start, long end) {
    TimeKey key = new TimeKey(start, end);

    lock.readLock().lock();
    try {
      TopologyGraph topologyGraph = cache.get(key);
      if (topologyGraph != null) {
        logger.debug("从缓存中获取到拓扑数据，时间范围: {}-{}，时间索引: {}", start, end, key.getTimeIndex());
      } else {
        logger.debug("缓存中未找到拓扑数据，时间范围: {}-{}，时间索引: {}", start, end, key.getTimeIndex());
      }
      return topologyGraph;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 按时间索引获取缓存项（start转秒后整除15秒的结果）
   *
   * @param timeIndex 时间索引
   * @return 匹配时间索引的缓存项列表
   */
  public Map<TimeKey, TopologyGraph> getByTimeIndex(int timeIndex) {
    Map<TimeKey, TopologyGraph> result = new LinkedHashMap<>();

    lock.readLock().lock();
    try {
      for (Map.Entry<TimeKey, TopologyGraph> entry : cache.entrySet()) {
        if (entry.getKey().getTimeIndex() == timeIndex) {
          result.put(entry.getKey(), entry.getValue());
        }
      }
      logger.debug("按时间索引 {} 获取到 {} 个缓存项", timeIndex, result.size());
      return result;
    } finally {
      lock.readLock().unlock();
    }
  }

  /** 清空缓存 */
  public void clear() {
    lock.writeLock().lock();
    try {
      cache.clear();
      logger.info("拓扑数据缓存已清空");
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 获取缓存大小
   *
   * @return 当前缓存中的条目数
   */
  public int size() {
    lock.readLock().lock();
    try {
      return cache.size();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 获取缓存统计信息
   *
   * @return 缓存统计信息字符串
   */
  public String getCacheStats() {
    lock.readLock().lock();
    try {
      return String.format("缓存大小: %d/%d", cache.size(), maxCacheSize);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 获取最新的缓存项
   *
   * @return 最新的拓扑图数据，如果缓存为空则返回null
   */
  public TopologyGraph getLatest() {
    lock.readLock().lock();
    try {
      if (cache.isEmpty()) {
        return null;
      }

      // 获取最后一个条目（最新的）
      TimeKey lastKey = null;
      TopologyGraph lastValue = null;
      for (Map.Entry<TimeKey, TopologyGraph> entry : cache.entrySet()) {
        lastKey = entry.getKey();
        lastValue = entry.getValue();
      }

      if (lastKey != null && lastValue != null) {
        logger.debug("获取到最新的缓存项，时间范围: {}-{}", lastKey.getStart(), lastKey.getEnd());
        return lastValue;
      }

      return null;
    } finally {
      lock.readLock().unlock();
    }
  }
}
