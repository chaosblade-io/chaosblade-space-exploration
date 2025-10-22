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

package com.chaosblade.svc.taskresource.service;

import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.entity.TaskSlo;
import com.chaosblade.svc.taskresource.repository.TaskSloRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskSloService {

  private static final Logger logger = LoggerFactory.getLogger(TaskSloService.class);

  @Autowired private TaskSloRepository repository;

  @Transactional(readOnly = true)
  public PageResponse<TaskSlo> pageQuery(
      Integer p95, Integer p99, Integer errRate, Long taskId, Long nodeId, int page, int size) {
    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
    Page<TaskSlo> p = repository.findByConditions(p95, p99, errRate, taskId, nodeId, pageable);
    return PageResponse.of(p.getContent(), p.getTotalElements(), page, size);
  }

  @Transactional(readOnly = true)
  public TaskSlo getById(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new BusinessException("TASK_SLO_NOT_FOUND", "任务SLO不存在: " + id));
  }

  public TaskSlo create(TaskSlo slo) {
    // 基本限制：至少有一个限制项
    if (slo.getP95() == null && slo.getP99() == null && slo.getErrRate() == null) {
      throw new BusinessException("TASK_SLO_INVALID", "至少设置一个限制项: p95/p99/errRate");
    }
    TaskSlo saved = repository.save(slo);
    logger.info(
        "TaskSlo created id={}, p95={}, p99={}, errRate={}, taskId={}, nodeId={}",
        saved.getId(),
        saved.getP95(),
        saved.getP99(),
        saved.getErrRate(),
        saved.getTaskId(),
        saved.getNodeId());
    return saved;
  }

  public TaskSlo update(Long id, TaskSlo slo) {
    TaskSlo existing = getById(id);
    slo.setId(existing.getId());
    if (slo.getP95() == null && slo.getP99() == null && slo.getErrRate() == null) {
      throw new BusinessException("TASK_SLO_INVALID", "至少设置一个限制项: p95/p99/errRate");
    }
    TaskSlo saved = repository.save(slo);
    logger.info(
        "TaskSlo updated id={}, p95={}, p99={}, errRate={}, taskId={}, nodeId={}",
        saved.getId(),
        saved.getP95(),
        saved.getP99(),
        saved.getErrRate(),
        saved.getTaskId(),
        saved.getNodeId());
    return saved;
  }

  public void delete(Long id) {
    TaskSlo existing = getById(id);
    repository.delete(existing);
    logger.info("TaskSlo deleted id={}", id);
  }
}
