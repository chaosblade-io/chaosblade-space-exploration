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

import com.chaosblade.svc.taskexecutor.entity.TaskExecution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
  @Query(
      "SELECT te FROM TaskExecution te WHERE te.taskId = :taskId AND te.status IN"
          + " ('INIT','GENERATING_CASES','ANALYZING_PATTERNS','RECORDING_READY','INJECTING_AND_REPLAYING','RULES_READY','LOAD_TEST_BASELINE')")
  List<TaskExecution> findRunningByTaskId(@Param("taskId") Long taskId);

  Optional<TaskExecution> findTop1ByTaskIdOrderByIdDesc(Long taskId);

  @Modifying
  @Query("UPDATE TaskExecution te SET te.status = :status WHERE te.id = :id")
  int updateStatus(@Param("id") Long id, @Param("status") String status);
}
