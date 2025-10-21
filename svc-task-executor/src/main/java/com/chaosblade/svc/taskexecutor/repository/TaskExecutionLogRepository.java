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

import com.chaosblade.svc.taskexecutor.entity.TaskExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {

    @Query("SELECT l FROM TaskExecutionLog l WHERE l.executionId = :executionId " +
           "AND (:minLevel IS NULL OR l.level >= :minLevel) ORDER BY l.ts ASC")
    Page<TaskExecutionLog> findByExecutionIdOrderByTsAsc(@Param("executionId") Long executionId,
                                                         @Param("minLevel") Integer minLevel,
                                                         Pageable pageable);
}

