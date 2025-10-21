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

import com.chaosblade.svc.taskresource.entity.TaskSlo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 任务SLO数据访问接口
 */
@Repository
public interface TaskSloRepository extends JpaRepository<TaskSlo, Long> {

    @Query("SELECT t FROM TaskSlo t WHERE (:p95 IS NULL OR t.p95 = :p95) " +
           "AND (:p99 IS NULL OR t.p99 = :p99) " +
           "AND (:errRate IS NULL OR t.errRate = :errRate) " +
           "AND (:taskId IS NULL OR t.taskId = :taskId) " +
           "AND (:nodeId IS NULL OR t.nodeId = :nodeId)")
    Page<TaskSlo> findByConditions(@Param("p95") Integer p95,
                                   @Param("p99") Integer p99,
                                   @Param("errRate") Integer errRate,
                                   @Param("taskId") Long taskId,
                                   @Param("nodeId") Long nodeId,
                                   Pageable pageable);
}
