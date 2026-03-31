-- =============================================================================
-- ChaosBlade Space Exploration - Combined Database Initialization
-- =============================================================================
-- This script creates both databases and all required tables.
-- It is idempotent: safe to run multiple times.
-- =============================================================================

-- =============================================
-- 1. Create databases
-- =============================================

CREATE DATABASE IF NOT EXISTS `spaceexploration`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `chaosblade`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================
-- 2. spaceexploration schema
-- =============================================

USE `spaceexploration`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- systems (referenced by many tables, must come first)
CREATE TABLE IF NOT EXISTS `systems` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Surrogate PK',
  `system_key` varchar(64) NOT NULL COMMENT 'External system identifier',
  `name` varchar(255) NOT NULL COMMENT 'System name',
  `description` text COMMENT 'System description',
  `owner` varchar(64) DEFAULT NULL COMMENT 'Owner/user/group',
  `default_environment` varchar(64) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_systems_system_key` (`system_key`),
  KEY `idx_systems_owner` (`owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- apis
CREATE TABLE IF NOT EXISTS `apis` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `system_id` bigint unsigned NOT NULL,
  `operation_id` varchar(128) NOT NULL COMMENT 'OpenAPI operationId',
  `method` enum('GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS','TRACE') NOT NULL,
  `path` varchar(512) NOT NULL,
  `summary` varchar(512) DEFAULT NULL,
  `tags` json DEFAULT NULL,
  `version` varchar(64) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `base_url` varchar(512) DEFAULT NULL,
  `content_type` varchar(64) NOT NULL DEFAULT 'application/json',
  `headers_template` json DEFAULT NULL,
  `auth_type` enum('NONE','TOKEN','COOKIE','BASIC','PROFILE') NOT NULL DEFAULT 'NONE',
  `auth_template` json DEFAULT NULL,
  `path_params` json DEFAULT NULL,
  `query_params` json DEFAULT NULL,
  `body_template` json DEFAULT NULL,
  `variables` json DEFAULT NULL,
  `timeout_ms` int unsigned NOT NULL DEFAULT '15000',
  `retry_config` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apis_system_op` (`system_id`,`operation_id`),
  KEY `idx_apis_system` (`system_id`),
  KEY `idx_apis_method_path` (`method`,`path`),
  CONSTRAINT `fk_apis_system` FOREIGN KEY (`system_id`) REFERENCES `systems` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- api_topologies
CREATE TABLE IF NOT EXISTS `api_topologies` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `system_id` bigint unsigned NOT NULL,
  `api_id` bigint unsigned NOT NULL,
  `discovered_at` datetime NOT NULL,
  `source_version` varchar(64) DEFAULT NULL,
  `notes` text,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_topologies_api` (`api_id`,`discovered_at`),
  KEY `idx_topologies_system` (`system_id`),
  CONSTRAINT `fk_topologies_api` FOREIGN KEY (`api_id`) REFERENCES `apis` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_topologies_system` FOREIGN KEY (`system_id`) REFERENCES `systems` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- api_topology_nodes
CREATE TABLE IF NOT EXISTS `api_topology_nodes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `topology_id` bigint unsigned NOT NULL,
  `node_key` varchar(128) NOT NULL,
  `name` varchar(255) NOT NULL,
  `layer` smallint unsigned NOT NULL DEFAULT '1',
  `protocol` enum('HTTP','gRPC','DB','MQ','OTHER') NOT NULL DEFAULT 'HTTP',
  `metadata` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nodes_topology_nodekey` (`topology_id`,`node_key`),
  KEY `idx_nodes_topology` (`topology_id`),
  KEY `idx_nodes_layer` (`layer`),
  CONSTRAINT `fk_nodes_topology` FOREIGN KEY (`topology_id`) REFERENCES `api_topologies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- api_topology_edges
CREATE TABLE IF NOT EXISTS `api_topology_edges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `topology_id` bigint unsigned NOT NULL,
  `from_node_id` bigint unsigned NOT NULL,
  `to_node_id` bigint unsigned NOT NULL,
  `metadata` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_edges_topology` (`topology_id`),
  KEY `idx_edges_from_to` (`from_node_id`,`to_node_id`),
  KEY `fk_edges_to_node` (`to_node_id`),
  CONSTRAINT `fk_edges_from_node` FOREIGN KEY (`from_node_id`) REFERENCES `api_topology_nodes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_edges_to_node` FOREIGN KEY (`to_node_id`) REFERENCES `api_topology_nodes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_edges_topology` FOREIGN KEY (`topology_id`) REFERENCES `api_topologies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- audit_logs
CREATE TABLE IF NOT EXISTS `audit_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(32) NOT NULL,
  `entity_id` bigint unsigned NOT NULL,
  `action` varchar(32) NOT NULL,
  `actor` varchar(64) NOT NULL,
  `details` json DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_entity` (`entity_type`,`entity_id`),
  KEY `idx_audit_action` (`action`),
  KEY `idx_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- baggage_map
CREATE TABLE IF NOT EXISTS `baggage_map` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL,
  `service_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exec_service` (`execution_id`,`service_name`),
  KEY `idx_service` (`service_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- detection_tasks
CREATE TABLE IF NOT EXISTS `detection_tasks` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` text,
  `system_id` bigint unsigned NOT NULL,
  `api_id` bigint unsigned NOT NULL,
  `created_by` varchar(64) NOT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `archived_at` datetime DEFAULT NULL,
  `fault_configurations_id` bigint DEFAULT NULL,
  `slo_id` bigint DEFAULT NULL,
  `request_num` int NOT NULL,
  `max_fault_services` int NOT NULL DEFAULT 2 COMMENT '最大故障服务数',
  `api_definition_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tasks_system` (`system_id`),
  KEY `idx_tasks_api` (`api_id`),
  KEY `idx_tasks_created_at` (`created_at`),
  CONSTRAINT `fk_tasks_api` FOREIGN KEY (`api_id`) REFERENCES `apis` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tasks_system` FOREIGN KEY (`system_id`) REFERENCES `systems` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- fault_config
CREATE TABLE IF NOT EXISTS `fault_config` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `node_id` bigint unsigned NOT NULL,
  `faultscript` text NOT NULL,
  `type` varchar(30) DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_node_id` (`node_id`),
  CONSTRAINT `fk_faultcfg_node` FOREIGN KEY (`node_id`) REFERENCES `api_topology_nodes` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- fault_types
CREATE TABLE IF NOT EXISTS `fault_types` (
  `fault_type_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `fault_code` varchar(64) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` text,
  `category` varchar(64) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` int NOT NULL DEFAULT '0',
  `param_config` json NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`fault_type_id`),
  UNIQUE KEY `uk_fault_code` (`fault_code`),
  KEY `idx_category_enabled` (`category`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- http_req_def
CREATE TABLE IF NOT EXISTS `http_req_def` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `method` enum('GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS') NOT NULL,
  `url_template` varchar(1024) NOT NULL,
  `headers` json DEFAULT NULL,
  `query_params` json DEFAULT NULL,
  `body_mode` enum('NONE','JSON','FORM','RAW') NOT NULL DEFAULT 'NONE',
  `content_type` varchar(128) DEFAULT NULL,
  `body_template` json DEFAULT NULL,
  `raw_body` mediumtext,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `api_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_method` (`method`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- http_req_variables
CREATE TABLE IF NOT EXISTS `http_req_variables` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `req_def_id` bigint NOT NULL,
  `variable_name` varchar(64) COLLATE utf16_bin NOT NULL,
  `variable_value` text COLLATE utf16_bin NOT NULL,
  `variable_type` enum('STRING','NUMBER','BOOLEAN') COLLATE utf16_bin DEFAULT 'STRING',
  `description` varchar(256) COLLATE utf16_bin DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_req_var` (`req_def_id`,`variable_name`),
  CONSTRAINT `http_req_variables_ibfk_1` FOREIGN KEY (`req_def_id`) REFERENCES `http_req_def` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- intercept_replay_results
CREATE TABLE IF NOT EXISTS `intercept_replay_results` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `execution_id` bigint DEFAULT NULL,
  `service_name` varchar(200) NOT NULL,
  `fault_type` varchar(128) NOT NULL,
  `request_url` varchar(2048) NOT NULL,
  `request_method` varchar(16) NOT NULL,
  `request_headers` json DEFAULT NULL,
  `request_body` longtext,
  `response_status` smallint unsigned NOT NULL,
  `response_headers` json DEFAULT NULL,
  `response_body` longtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_service` (`task_id`,`service_name`),
  KEY `idx_fault` (`fault_type`),
  KEY `idx_method_url` (`request_method`,`request_url`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- intercept_rules
CREATE TABLE IF NOT EXISTS `intercept_rules` (
  `id` bigint NOT NULL,
  `service_name` varchar(255) COLLATE utf16_bin DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `fault_type` varchar(255) COLLATE utf16_bin DEFAULT NULL,
  `request_url` varchar(255) COLLATE utf16_bin DEFAULT NULL,
  `request_method` varchar(10) COLLATE utf16_bin DEFAULT NULL,
  `request_body` text COLLATE utf16_bin,
  `request_header` text COLLATE utf16_bin,
  `response_body` text COLLATE utf16_bin,
  `execution_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- request_patterns
CREATE TABLE IF NOT EXISTS `request_patterns` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL,
  `service_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `method` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `url` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_headers` json DEFAULT NULL,
  `request_body` json DEFAULT NULL,
  `response_headers` json DEFAULT NULL,
  `response_body` json DEFAULT NULL,
  `response_status` smallint unsigned NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`execution_id`),
  KEY `idx_service` (`service_name`),
  KEY `idx_task_service` (`execution_id`,`service_name`),
  KEY `idx_method_url` (`method`,`url`(255)),
  KEY `idx_status` (`response_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- task_conclusion
CREATE TABLE IF NOT EXISTS `task_conclusion` (
  `id` int NOT NULL AUTO_INCREMENT,
  `model_content` text COLLATE utf16_bin,
  `execution_id` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- task_execution
CREATE TABLE IF NOT EXISTS `task_execution` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `namespace` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `req_def_id` bigint DEFAULT NULL,
  `request_num` int NOT NULL DEFAULT '1',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INIT',
  `analyze_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `record_id` bigint DEFAULT NULL,
  `intercept_record_id` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime DEFAULT NULL,
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_msg` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_status` (`status`),
  KEY `idx_record` (`record_id`),
  KEY `idx_intercept_record` (`intercept_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- task_execution_log
CREATE TABLE IF NOT EXISTS `task_execution_log` (
  `id` bigint NOT NULL,
  `execution_id` bigint NOT NULL,
  `ts` timestamp NULL DEFAULT NULL,
  `level` tinyint DEFAULT NULL,
  `message` text COLLATE utf16_bin,
  `created_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- task_slo
CREATE TABLE IF NOT EXISTS `task_slo` (
  `id` bigint NOT NULL,
  `p95` int DEFAULT NULL,
  `p99` int DEFAULT NULL,
  `err_rate` int DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `node_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- test_cases
CREATE TABLE IF NOT EXISTS `test_cases` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id` bigint unsigned NOT NULL,
  `case_type` enum('BASELINE','SINGLE','DUAL') NOT NULL,
  `target_count` tinyint unsigned NOT NULL,
  `faults_json` json NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_detection_task_id` (`task_id`),
  CONSTRAINT `fk_tc_detection_task` FOREIGN KEY (`task_id`) REFERENCES `detection_tasks` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_target_count` CHECK ((`target_count` in (0,1,2)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- test_result
CREATE TABLE IF NOT EXISTS `test_result` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `execution_id` varchar(64) NOT NULL,
  `test_case_id` bigint NOT NULL,
  `p50` int DEFAULT NULL,
  `p95` int DEFAULT NULL,
  `p99` int DEFAULT NULL,
  `err_rate` decimal(5,2) DEFAULT NULL,
  `request_url` text,
  `request_method` varchar(10) DEFAULT NULL,
  `response_code` int DEFAULT NULL,
  `response_body` longtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_exec_case` (`execution_id`,`test_case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- fixtures (V3 migration - svc-reqrsp-proxy)
CREATE TABLE IF NOT EXISTS `fixtures` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `namespace` VARCHAR(100) NOT NULL COMMENT 'Namespace',
  `record_id` VARCHAR(200) COMMENT 'Record ID for grouping and cleanup',
  `service_name` VARCHAR(100) NOT NULL COMMENT 'Service name',
  `method` VARCHAR(10) NOT NULL COMMENT 'HTTP method',
  `path` VARCHAR(500) NOT NULL COMMENT 'Request path',
  `baggage_tokens` TEXT NOT NULL COMMENT 'Baggage token matching conditions (JSON array)',
  `resp_status` INT NOT NULL COMMENT 'Response status code',
  `resp_headers` TEXT COMMENT 'Response headers (JSON)',
  `resp_body` TEXT NOT NULL COMMENT 'Response body',
  `expires_at` DATETIME NOT NULL COMMENT 'Expiration time',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_fixture_namespace_service` (`namespace`, `service_name`),
  INDEX `idx_fixture_record_id` (`record_id`),
  INDEX `idx_fixture_expires_at` (`expires_at`),
  INDEX `idx_fixture_match` (`namespace`, `service_name`, `method`, `path`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- proxy_instance (V4 migration)
CREATE TABLE IF NOT EXISTS `proxy_instance` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `recording_id` VARCHAR(64) NOT NULL COMMENT 'Associated recording session ID',
  `namespace` VARCHAR(128) NOT NULL COMMENT 'K8s namespace',
  `target_service` VARCHAR(128) NOT NULL COMMENT 'Target service being proxied',
  `proxy_pod_ip` VARCHAR(45) COMMENT 'proxy-agent Pod IP',
  `proxy_port` INT NOT NULL DEFAULT 8080 COMMENT 'Proxy listen port',
  `control_port` INT NOT NULL DEFAULT 9090 COMMENT 'Control API port',
  `original_selector` JSON COMMENT 'Original Service selector before hijack',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DEPLOYING/RUNNING/RESTORING/DESTROYED/ERROR',
  `deployment_name` VARCHAR(128) COMMENT 'proxy-agent Deployment name',
  `error_message` VARCHAR(512) COMMENT 'Error message',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_proxy_recording` (`recording_id`),
  INDEX `idx_proxy_ns_svc` (`namespace`, `target_service`),
  INDEX `idx_proxy_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- proxy_snapshot (V4 migration)
CREATE TABLE IF NOT EXISTS `proxy_snapshot` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `recording_id` VARCHAR(64) NOT NULL COMMENT 'Associated recording session ID',
  `signature_hash` VARCHAR(128) NOT NULL COMMENT 'Request signature SHA256',
  `protocol` VARCHAR(8) NOT NULL DEFAULT 'http' COMMENT 'http or grpc',
  `method` VARCHAR(16) COMMENT 'HTTP method or GRPC',
  `path` VARCHAR(512) COMMENT 'URL path or gRPC method',
  `request_headers` JSON COMMENT 'Request headers JSON',
  `request_body` MEDIUMBLOB COMMENT 'Request body raw bytes',
  `response_status` INT COMMENT 'HTTP status code or gRPC code',
  `response_headers` JSON COMMENT 'Response headers JSON',
  `response_body` MEDIUMBLOB COMMENT 'Response body raw bytes',
  `latency_ms` INT NOT NULL DEFAULT 0 COMMENT 'Response latency in ms',
  `recorded_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_snap_recording` (`recording_id`),
  INDEX `idx_snap_sig` (`recording_id`, `signature_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;


-- =============================================
-- 3. chaosblade schema (chaosblade-box 专属)
-- =============================================

CREATE DATABASE IF NOT EXISTS `chaosblade`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
