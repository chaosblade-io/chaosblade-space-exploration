-- 命名空间分析执行日志表
-- 用于记录分析任务执行过程中的详细日志

CREATE TABLE IF NOT EXISTS ns_analysis_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    level INT NOT NULL DEFAULT 1 COMMENT '日志级别: DEBUG=0, INFO=1, WARN=2, ERROR=3',
    phase INT COMMENT '执行阶段: 1-6',
    message TEXT NOT NULL COMMENT '日志消息',
    details JSON COMMENT '详细信息(JSON)',
    duration_ms BIGINT COMMENT '耗时(毫秒)',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(毫秒精度)',
    
    INDEX idx_task_id (task_id),
    INDEX idx_task_level (task_id, level),
    INDEX idx_task_phase (task_id, phase),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='命名空间分析执行日志表';

