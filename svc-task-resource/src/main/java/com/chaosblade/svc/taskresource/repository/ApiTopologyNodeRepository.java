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

import com.chaosblade.svc.taskresource.entity.ApiTopologyNode;
import com.chaosblade.svc.taskresource.entity.Protocol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * API拓扑节点数据访问接口
 */
@Repository
public interface ApiTopologyNodeRepository extends JpaRepository<ApiTopologyNode, Long> {
    
    /**
     * 根据拓扑ID查找所有节点
     */
    List<ApiTopologyNode> findByTopologyId(Long topologyId);
    
    /**
     * 根据拓扑ID分页查找节点
     */
    Page<ApiTopologyNode> findByTopologyId(Long topologyId, Pageable pageable);
    

    
    /**
     * 根据拓扑ID删除所有节点
     */
    void deleteByTopologyId(Long topologyId);
    

    
    /**
     * 统计拓扑中的节点数量
     */
    long countByTopologyId(Long topologyId);
    

}
