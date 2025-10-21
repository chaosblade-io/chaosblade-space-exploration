package com.chaosblade.svc.topo.service;

import static org.junit.jupiter.api.Assertions.*;

import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 拓扑缓存服务测试类 */
class TopologyCacheServiceTest {

  private TopologyCacheService topologyCacheService;

  @BeforeEach
  void setUp() {
    topologyCacheService = new TopologyCacheService();
    // 设置最大缓存大小为3，便于测试
    ReflectionTestUtils.setField(topologyCacheService, "maxCacheSize", 3);
  }

  @Test
  void testPutAndGet() {
    // 创建测试数据
    long start = System.currentTimeMillis() * 1000;
    long end = start + 300000; // 5分钟后
    TopologyGraph topologyGraph = new TopologyGraph();

    // 存入缓存
    topologyCacheService.put(start, end, topologyGraph);

    // 从缓存中获取
    TopologyGraph cachedGraph = topologyCacheService.get(start, end);

    // 验证
    assertNotNull(cachedGraph);
    assertSame(topologyGraph, cachedGraph);
  }

  @Test
  void testGetNonExistent() {
    // 查询不存在的缓存项
    TopologyGraph cachedGraph = topologyCacheService.get(0, 1000);

    // 验证返回null
    assertNull(cachedGraph);
  }

  @Test
  void testCacheEviction() {
    // 添加超过最大容量的缓存项
    for (int i = 0; i < 5; i++) {
      long start = System.currentTimeMillis() * 1000 + i * 1000000;
      long end = start + 300000;
      TopologyGraph topologyGraph = new TopologyGraph();
      topologyCacheService.put(start, end, topologyGraph);
    }

    // 验证缓存大小不超过最大容量
    int cacheSize = topologyCacheService.size();
    assertTrue(cacheSize <= 3, "缓存大小应该不超过最大容量");
  }

  @Test
  void testGetByTimeIndex() {
    // 使用较小的时间值来测试时间索引计算
    long baseTimeSec = 0; // 秒

    // 添加时间索引为1的项 (20秒 / 15 = 1)
    long start1 = (baseTimeSec + 20) * 1000000; // 20秒后（微秒）
    long end1 = start1 + 300000;
    TopologyCacheService.TimeKey key1 = new TopologyCacheService.TimeKey(start1, end1);
    System.out.println("TimeKey1 timeIndex: " + key1.getTimeIndex() + ", start: " + start1);
    topologyCacheService.put(start1, end1, new TopologyGraph());

    // 添加时间索引为2的项 (40秒 / 15 = 2)
    long start2 = (baseTimeSec + 40) * 1000000; // 40秒后（微秒）
    long end2 = start2 + 300000;
    TopologyCacheService.TimeKey key2 = new TopologyCacheService.TimeKey(start2, end2);
    System.out.println("TimeKey2 timeIndex: " + key2.getTimeIndex() + ", start: " + start2);
    topologyCacheService.put(start2, end2, new TopologyGraph());

    // 再添加一个时间索引为2的项 (30秒 / 15 = 2)
    long start3 = (baseTimeSec + 30) * 1000000; // 30秒后（微秒）
    long end3 = start3 + 300000;
    TopologyCacheService.TimeKey key3 = new TopologyCacheService.TimeKey(start3, end3);
    System.out.println("TimeKey3 timeIndex: " + key3.getTimeIndex() + ", start: " + start3);
    topologyCacheService.put(start3, end3, new TopologyGraph());

    // 查询时间索引为1的项
    Map<TopologyCacheService.TimeKey, TopologyGraph> items = topologyCacheService.getByTimeIndex(1);
    System.out.println("Items with timeIndex 1: " + items.size());

    // 验证找到1个匹配项（20秒/15=1）
    assertEquals(1, items.size());

    // 查询时间索引为2的项
    items = topologyCacheService.getByTimeIndex(2);
    System.out.println("Items with timeIndex 2: " + items.size());

    // 验证找到2个匹配项（30秒/15=2 和 40秒/15=2）
    assertEquals(2, items.size());
  }

  @Test
  void testClear() {
    // 添加几个缓存项
    topologyCacheService.put(1000, 2000, new TopologyGraph());
    topologyCacheService.put(2000, 3000, new TopologyGraph());

    // 验证缓存不为空
    assertEquals(2, topologyCacheService.size());

    // 清空缓存
    topologyCacheService.clear();

    // 验证缓存为空
    assertEquals(0, topologyCacheService.size());
  }

  @Test
  void testTimeKeyEqualsAndHashCode() {
    long start = 1000000;
    long end = 2000000;

    TopologyCacheService.TimeKey key1 = new TopologyCacheService.TimeKey(start, end);
    TopologyCacheService.TimeKey key2 = new TopologyCacheService.TimeKey(start, end);
    TopologyCacheService.TimeKey key3 = new TopologyCacheService.TimeKey(start, end + 1000);

    // 验证相等性
    assertEquals(key1, key2);
    assertNotEquals(key1, key3);

    // 验证哈希码
    assertEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1.hashCode(), key3.hashCode());
  }

  @Test
  void testTimeKeyTimeIndexCalculation() {
    // 测试时间索引计算逻辑
    // 20秒 / 15 = 1
    long start1 = 20 * 1000000; // 20秒（微秒）
    TopologyCacheService.TimeKey key1 = new TopologyCacheService.TimeKey(start1, start1 + 1000000);
    System.out.println("TimeKey1 timeIndex: " + key1.getTimeIndex() + ", expected: 1");
    assertEquals(1, key1.getTimeIndex());

    // 30秒 / 15 = 2
    long start2 = 30 * 1000000; // 30秒（微秒）
    TopologyCacheService.TimeKey key2 = new TopologyCacheService.TimeKey(start2, start2 + 1000000);
    System.out.println("TimeKey2 timeIndex: " + key2.getTimeIndex() + ", expected: 2");
    assertEquals(2, key2.getTimeIndex());

    // 0秒 / 15 = 0
    long start3 = 0; // 0秒（微秒）
    TopologyCacheService.TimeKey key3 = new TopologyCacheService.TimeKey(start3, start3 + 1000000);
    System.out.println("TimeKey3 timeIndex: " + key3.getTimeIndex() + ", expected: 0");
    assertEquals(0, key3.getTimeIndex());
  }

  @Test
  void testGetCacheStats() {
    // 初始状态
    String stats = topologyCacheService.getCacheStats();
    assertTrue(stats.contains("缓存大小: 0/3"));

    // 添加一个缓存项
    topologyCacheService.put(1000, 2000, new TopologyGraph());
    stats = topologyCacheService.getCacheStats();
    assertTrue(stats.contains("缓存大小: 1/3"));
  }
}
