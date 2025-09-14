package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.svc.taskexecutor.entity.TaskExecutionLog;
import com.chaosblade.svc.taskexecutor.repository.TaskExecutionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskExecutionLogService {

    private final TaskExecutionLogRepository repository;

    public TaskExecutionLogService(TaskExecutionLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskExecutionLog append(Long executionId, TaskExecutionLog.LogLevel level, String message) {
        return repository.save(new TaskExecutionLog(executionId, level, message));
    }

    @Transactional(readOnly = true)
    public Page<TaskExecutionLog> getLogs(Long executionId, Integer minLevel, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page-1), size);
        return repository.findByExecutionIdOrderByTsAsc(executionId, minLevel, pageable);
    }
}

