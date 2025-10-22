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

import com.chaosblade.svc.taskresource.entity.TaskExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    @Query("SELECT te FROM TaskExecution te WHERE te.taskId = :taskId " +
            "AND te.status IN ('INIT','GENERATING_CASES','ANALYZING_PATTERNS','RECORDING_READY','INJECTING_AND_REPLAYING','RULES_READY','LOAD_TEST_BASELINE')")
    List<TaskExecution> findRunningByTaskId(@Param("taskId") Long taskId);

    Optional<TaskExecution> findTop1ByTaskIdOrderByStartedAtDesc(Long taskId);

    Page<TaskExecution> findByTaskIdOrderByStartedAtDesc(Long taskId, Pageable pageable);

    @Query("SELECT te FROM TaskExecution te WHERE " +
            "(:taskId IS NULL OR te.taskId = :taskId) AND " +
            "(:status IS NULL OR te.status = :status) AND " +
            "(:namespace IS NULL OR te.namespace = :namespace) AND " +
            "(:startDate IS NULL OR te.startedAt >= :startDate) AND " +
            "(:endDate IS NULL OR te.startedAt <= :endDate) " +
            "ORDER BY te.startedAt DESC")
    Page<TaskExecution> findByConditions(@Param("taskId") Long taskId,
                                         @Param("status") String status,
                                         @Param("namespace") String namespace,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate,
                                         Pageable pageable);
}

