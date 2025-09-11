package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.ApiQueryRequest;
import com.chaosblade.svc.topo.model.ApiQueryResponse;
import com.chaosblade.svc.topo.model.MetricsByApiRequest;
import com.chaosblade.svc.topo.model.MetricsByApiResponse;
import com.chaosblade.svc.topo.model.TopologyByApiRequest;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.service.ApiQueryService;
import com.chaosblade.svc.topo.service.TopologyConverterService;
import com.chaosblade.svc.topo.service.TopologyCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API查询控制器
 * 提供查询指定命名空间、指定时间范围内API列表的功能
 */
@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "*")
public class ApiQueryController {

    private static final Logger logger = LoggerFactory.getLogger(ApiQueryController.class);

    @Autowired
    private ApiQueryService apiQueryService;

    @Autowired
    private TopologyConverterService topologyConverterService;
    
    // 添加缓存服务依赖
    @Autowired
    private TopologyCacheService topologyCacheService;

    /**
     * 查询API列表
     * 获取指定命名空间、指定时间范围内的API列表
     *
     * @param request API查询请求对象
     * @return API查询响应对象
     */
    @PostMapping("/apis/end2end")
    public ResponseEntity<ApiQueryResponse> queryApis(@RequestBody ApiQueryRequest request) {
        logger.info("收到API查询请求: namespace={}, services={}",
            request.getNamespace(),
            request.getAppSelector() != null ? request.getAppSelector().getServices() : "all");

        try {
            // 尝试从缓存中获取拓扑图
            TopologyGraph currentTopology = getTopologyFromCacheOrCurrentForApiQuery(request);
            
            ApiQueryResponse response = apiQueryService.queryApisFromTopology(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询API列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据API ID查询拓扑信息
     * 返回该节点的唯一上游节点，和所有下游节点，以及相关的边
     * 需要先通过 /apis/end2end 拿到正确的 apiId。
     *
     * @param request 拓扑查询请求对象
     * @return 包含指定API节点上下游信息的拓扑图
     */
    @PostMapping("/topology/byapi")
    public ResponseEntity<TopologyGraph> queryTopologyByApi(@RequestBody TopologyByApiRequest request) {
        logger.info("收到拓扑查询请求: namespace={}, apiId={}",
            request.getNamespace(),
            request.getApiId());

        try {
            // 尝试从缓存中获取拓扑图
            TopologyGraph currentTopology = getTopologyFromCacheOrCurrentForTopologyQuery(request);
            
            TopologyGraph response = apiQueryService.queryTopologyByApiId(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询拓扑信息失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据API ID查询性能指标
     * 返回根API的性能数据：错误率、吞吐量、p50/p95/p99
     *
     * @param request 指标查询请求对象
     * @return 性能指标响应对象
     */
    @PostMapping("/metrics/byapi")
    public ResponseEntity<MetricsByApiResponse> queryMetricsByApi(@RequestBody MetricsByApiRequest request) {
        logger.info("收到指标查询请求: apiId={}", request.getApiId());

        try {
            // 尝试从缓存中获取拓扑图
            TopologyGraph currentTopology = getTopologyFromCacheOrCurrentForMetricsQuery(request);
            
            MetricsByApiResponse response = apiQueryService.queryMetricsByApiId(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询性能指标失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 从缓存或当前拓扑中获取拓扑图（用于API查询）
     * 
     * @param request API查询请求对象（用于提取时间范围）
     * @return 拓扑图
     */
    private TopologyGraph getTopologyFromCacheOrCurrentForApiQuery(ApiQueryRequest request) {
        TopologyGraph currentTopology = null;
        
        // 如果请求包含时间范围，尝试从缓存中获取
        if (request != null && request.getTimeRange() != null) {
            Long start = request.getTimeRange().getStart();
            Long end = request.getTimeRange().getEnd();
            
            if (start != null && end != null) {
                currentTopology = topologyCacheService.get(start, end);
                if (currentTopology != null) {
                    logger.debug("从缓存中获取到拓扑图，时间范围: {}-{}", start, end);
                }
            }
        }
        
        // 如果缓存中没有找到，从当前拓扑获取
        if (currentTopology == null) {
            currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
            }
        }
        
        return currentTopology;
    }
    
    /**
     * 从缓存或当前拓扑中获取拓扑图（用于指标查询）
     * 
     * @param request 指标查询请求对象（用于提取时间范围）
     * @return 拓扑图
     */
    private TopologyGraph getTopologyFromCacheOrCurrentForMetricsQuery(MetricsByApiRequest request) {
        // MetricsByApiRequest也包含时间范围，可以类似处理
        if (request != null && request.getTimeRange() != null) {
            Long start = request.getTimeRange().getStart();
            Long end = request.getTimeRange().getEnd();
            
            if (start != null && end != null) {
                TopologyGraph cachedTopology = topologyCacheService.get(start, end);
                if (cachedTopology != null) {
                    logger.debug("从缓存中获取到拓扑图，时间范围: {}-{}", start, end);
                    return cachedTopology;
                }
            }
        }
        
        // 如果缓存中没有找到，从当前拓扑获取
        TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
        if (currentTopology == null) {
            logger.warn("当前拓扑图为空");
            currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
        }
        
        return currentTopology;
    }
    
    /**
     * 从缓存或当前拓扑中获取拓扑图（用于拓扑查询）
     * 
     * @param request 拓扑查询请求对象
     * @return 拓扑图
     */
    private TopologyGraph getTopologyFromCacheOrCurrentForTopologyQuery(TopologyByApiRequest request) {
        // TopologyByApiRequest不包含时间范围，直接从当前拓扑获取
        TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
        if (currentTopology == null) {
            logger.warn("当前拓扑图为空");
            currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
        }
        
        return currentTopology;
    }
}