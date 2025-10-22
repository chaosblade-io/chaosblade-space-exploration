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

package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.FaultConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FaultConfigRepository extends JpaRepository<FaultConfig, Long> {
    List<FaultConfig> findByNodeIdIn(Collection<Long> nodeIds);

    @Query("SELECT f FROM FaultConfig f WHERE f.nodeId IN :nodeIds AND (f.taskId IS NULL OR f.taskId = :taskId)")
    List<FaultConfig> findByNodeIdsWithTaskScope(@Param("nodeIds") Collection<Long> nodeIds,
                                                 @Param("taskId") Long taskId);
}

