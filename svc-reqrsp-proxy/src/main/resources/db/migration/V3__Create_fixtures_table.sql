-- 创建 fixtures 表
CREATE TABLE IF NOT EXISTS fixtures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace VARCHAR(100) NOT NULL COMMENT '命名空间',
    record_id VARCHAR(200) COMMENT '记录ID，用于分组和清理',
    service_name VARCHAR(100) NOT NULL COMMENT '服务名称',
    method VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
    path VARCHAR(500) NOT NULL COMMENT '请求路径',
    baggage_tokens TEXT NOT NULL COMMENT '行李令牌匹配条件（JSON数组格式）',
    resp_status INT NOT NULL COMMENT '响应状态码',
    resp_headers TEXT COMMENT '响应头（JSON格式）',
    resp_body TEXT NOT NULL COMMENT '响应体',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fixture拦截规则表';

-- 创建索引
CREATE INDEX idx_fixture_namespace_service ON fixtures(namespace, service_name);
CREATE INDEX idx_fixture_record_id ON fixtures(record_id);
CREATE INDEX idx_fixture_expires_at ON fixtures(expires_at);
CREATE INDEX idx_fixture_match ON fixtures(namespace, service_name, method, path, expires_at);

-- 插入示例数据（可选）
INSERT INTO fixtures (
    namespace, record_id, service_name, method, path, 
    baggage_tokens, resp_status, resp_headers, resp_body, expires_at
) VALUES 
(
    'train-ticket', 
    'example_record_001', 
    'ts-order-service', 
    'POST', 
    '/api/v1/orderservice/order',
    '["chaos.f2=orderservice-pod-delay"]',
    200,
    '{"content-type": "application/json;charset=UTF-8"}',
    '{"status":1,"msg":"Success","data":{"type":"条件拦截"}}',
    DATE_ADD(NOW(), INTERVAL 1 HOUR)
),
(
    'train-ticket', 
    'example_record_001', 
    'ts-travel-service', 
    'GET', 
    '/api/v1/travelservice/routes/D1345',
    '["*"]',
    200,
    '{"content-type": "application/json;charset=UTF-8"}',
    '{"status":1,"msg":"Success","data":{"type":"条件拦截"}}',
    DATE_ADD(NOW(), INTERVAL 1 HOUR)
);
