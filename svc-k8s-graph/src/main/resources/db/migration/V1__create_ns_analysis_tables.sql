-- 命名空间分析任务表
CREATE TABLE IF NOT EXISTS ns_analysis_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE COMMENT '任务唯一标识',
    namespace VARCHAR(128) NOT NULL COMMENT '命名空间',
    system_id BIGINT COMMENT '关联系统ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED',
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '触发类型: MANUAL, SCHEDULED, API',
    top_n INT DEFAULT 5 COMMENT 'Top N风险数',
    config JSON COMMENT '任务配置JSON',
    started_at DATETIME COMMENT '开始时间',
    finished_at DATETIME COMMENT '完成时间',
    total_time_ms BIGINT COMMENT '总耗时(毫秒)',
    current_phase INT COMMENT '当前阶段',
    progress_percent INT DEFAULT 0 COMMENT '进度百分比',
    error_code VARCHAR(64) COMMENT '错误码',
    error_message TEXT COMMENT '错误信息',
    created_by VARCHAR(64) COMMENT '创建者',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_namespace (namespace),
    INDEX idx_namespace_status (namespace, status),
    INDEX idx_namespace_created (namespace, created_at),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='命名空间分析任务表';

-- 命名空间分析结果表
CREATE TABLE IF NOT EXISTS ns_analysis_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    namespace VARCHAR(128) NOT NULL COMMENT '命名空间',
    system_id BIGINT COMMENT '关联系统ID',
    total_services INT COMMENT '服务总数',
    total_pods INT COMMENT 'Pod总数',
    total_containers INT COMMENT '容器总数',
    total_nodes INT COMMENT '节点总数',
    total_edges INT COMMENT '边总数',
    topology_json LONGTEXT COMMENT '拓扑结构JSON',
    services_json LONGTEXT COMMENT '服务列表JSON',
    pods_json LONGTEXT COMMENT 'Pod列表JSON',
    containers_json LONGTEXT COMMENT '容器列表JSON',
    risk_summary_json LONGTEXT COMMENT '风险摘要JSON',
    top_risks_json LONGTEXT COMMENT 'Top风险JSON',
    metrics_json LONGTEXT COMMENT '指标数据JSON',
    analysis_time_ms BIGINT COMMENT '分析耗时(毫秒)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_task_id (task_id),
    INDEX idx_namespace (namespace),
    INDEX idx_namespace_created (namespace, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='命名空间分析结果表';

-- 命名空间风险分析表
CREATE TABLE IF NOT EXISTS ns_risk_analyses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    namespace VARCHAR(128) NOT NULL COMMENT '命名空间',
    service_name VARCHAR(256) NOT NULL COMMENT '服务名称',
    service_uid VARCHAR(64) COMMENT '服务UID',
    risk_level VARCHAR(20) NOT NULL COMMENT '风险级别: CRITICAL, HIGH, MEDIUM, LOW, INFO',
    risk_score DECIMAL(10,2) COMMENT '风险分数',
    risk_category VARCHAR(64) COMMENT '风险类别',
    risk_title VARCHAR(512) COMMENT '风险标题',
    risk_description TEXT COMMENT '风险描述',
    risk_details_json LONGTEXT COMMENT '风险详情JSON',
    recommendations_json LONGTEXT COMMENT '建议措施JSON',
    affected_pods_json LONGTEXT COMMENT '受影响Pod列表JSON',
    metrics_json LONGTEXT COMMENT '相关指标JSON',
    llm_analysis_json LONGTEXT COMMENT 'LLM分析结果JSON',
    rank_order INT COMMENT '排名顺序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_task_id (task_id),
    INDEX idx_namespace (namespace),
    INDEX idx_service_name (service_name),
    INDEX idx_risk_level (risk_level),
    INDEX idx_risk_score (risk_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='命名空间风险分析表';

