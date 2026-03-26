-- proxy_instance: proxy-agent Pod 部署实例信息
CREATE TABLE IF NOT EXISTS proxy_instance (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    recording_id      VARCHAR(64)  NOT NULL COMMENT '关联的录制会话 ID',
    namespace         VARCHAR(128) NOT NULL COMMENT 'K8s 命名空间',
    target_service    VARCHAR(128) NOT NULL COMMENT '被代理的目标服务名',
    proxy_pod_ip      VARCHAR(45)           COMMENT 'proxy-agent Pod IP',
    proxy_port        INT          NOT NULL DEFAULT 8080 COMMENT 'proxy 监听端口',
    control_port      INT          NOT NULL DEFAULT 9090 COMMENT 'Control API 端口',
    original_selector JSON                  COMMENT '被劫持前的原始 Service selector',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DEPLOYING/RUNNING/RESTORING/DESTROYED/ERROR',
    deployment_name   VARCHAR(128)          COMMENT 'proxy-agent Deployment 名称',
    error_message     VARCHAR(512)          COMMENT '错误信息',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_proxy_recording (recording_id),
    INDEX idx_proxy_ns_svc (namespace, target_service),
    INDEX idx_proxy_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='proxy-agent 部署实例';

-- proxy_snapshot: 录制的请求/响应快照
CREATE TABLE IF NOT EXISTS proxy_snapshot (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    recording_id      VARCHAR(64)  NOT NULL COMMENT '关联的录制会话 ID',
    signature_hash    VARCHAR(128) NOT NULL COMMENT '请求签名 SHA256',
    protocol          VARCHAR(8)   NOT NULL DEFAULT 'http' COMMENT 'http 或 grpc',
    method            VARCHAR(16)           COMMENT 'HTTP method 或 GRPC',
    path              VARCHAR(512)          COMMENT 'URL path 或 gRPC method',
    request_headers   JSON                  COMMENT '请求头 JSON',
    request_body      MEDIUMBLOB            COMMENT '请求体原始字节',
    response_status   INT                   COMMENT 'HTTP status code 或 gRPC code',
    response_headers  JSON                  COMMENT '响应头 JSON',
    response_body     MEDIUMBLOB            COMMENT '响应体原始字节',
    latency_ms        INT          NOT NULL DEFAULT 0 COMMENT '响应延迟（毫秒）',
    recorded_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_snap_recording (recording_id),
    INDEX idx_snap_sig (recording_id, signature_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='proxy-agent 录制快照';
