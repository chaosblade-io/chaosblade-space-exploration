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

package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.ApiTopologyEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * API拓扑边数据访问接口
 */
@Repository
public interface ApiTopologyEdgeRepository extends JpaRepository<ApiTopologyEdge, Long> {
    
    /**
     * 根据拓扑ID查找所有边
     */
    List<ApiTopologyEdge> findByTopologyId(Long topologyId);
    
    /**
     * 根据起始节点ID查找边
     */
    List<ApiTopologyEdge> findByFromNodeId(Long fromNodeId);
    
    /**
     * 根据目标节点ID查找边
     */
    List<ApiTopologyEdge> findByToNodeId(Long toNodeId);
    
    /**
     * 根据拓扑ID和起始节点ID查找边
     */
    List<ApiTopologyEdge> findByTopologyIdAndFromNodeId(Long topologyId, Long fromNodeId);
    
    /**
     * 根据拓扑ID和目标节点ID查找边
     */
    List<ApiTopologyEdge> findByTopologyIdAndToNodeId(Long topologyId, Long toNodeId);
    
    /**
     * 根据拓扑ID删除所有边
     */
    void deleteByTopologyId(Long topologyId);
    
    /**
     * 统计拓扑中的边数量
     */
    long countByTopologyId(Long topologyId);
}
