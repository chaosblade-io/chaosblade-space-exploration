package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.ApiQueryRequest;
import com.chaosblade.svc.topo.model.ApiQueryResponse;
import com.chaosblade.svc.topo.model.TopologyByApiRequest;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.service.ApiQueryService;
import com.chaosblade.svc.topo.service.TopologyConverterService;
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
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
            }

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
            // 从TopologyConverterService获取当前拓扑图
            TopologyGraph currentTopology = topologyConverterService.getCurrentTopology();
            if (currentTopology == null) {
                logger.warn("当前拓扑图为空");
                currentTopology = new TopologyGraph(); // 返回空的拓扑图而不是null
            }

            TopologyGraph response = apiQueryService.queryTopologyByApiId(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询拓扑信息失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
