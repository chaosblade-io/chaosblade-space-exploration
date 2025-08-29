package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.ApiQueryRequest;
import com.chaosblade.svc.topo.model.ApiQueryResponse;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.service.ApiQueryService;
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

    // 注意：在实际应用中，currentTopology应该从某个服务中获取
    // 这里为了演示目的，我们假设它已经被初始化
    private TopologyGraph currentTopology = new TopologyGraph();

    @Autowired
    private ApiQueryService apiQueryService;

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
            // 在实际应用中，这里应该从某个服务获取currentTopology
            // 例如：topologyService.getCurrentTopology()
            ApiQueryResponse response = apiQueryService.queryApisFromTopology(currentTopology, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询API列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
