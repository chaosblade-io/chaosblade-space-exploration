--
-- Copyright 2025 The ChaosBlade Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

/*
 Navicat Premium Data Transfer

 Source Server         : hw-mysql
 Source Server Type    : MySQL
 Source Server Version : 90400 (9.4.0)
 Source Host           : 116.63.51.45:3306
 Source Schema         : spaceexploration

 Target Server Type    : MySQL
 Target Server Version : 90400 (9.4.0)
 File Encoding         : 65001

 Date: 14/09/2025 11:01:08
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for api_topologies
-- ----------------------------
DROP TABLE IF EXISTS `api_topologies`;
CREATE TABLE `api_topologies` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `system_id` bigint unsigned NOT NULL,
  `api_id` bigint unsigned NOT NULL,
  `discovered_at` datetime NOT NULL COMMENT 'Topology discovery timestamp',
  `source_version` varchar(64) DEFAULT NULL COMMENT 'Source API/spec version or hash',
  `notes` text,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_topologies_api` (`api_id`,`discovered_at`),
  KEY `idx_topologies_system` (`system_id`),
  CONSTRAINT `fk_topologies_api` FOREIGN KEY (`api_id`) REFERENCES `apis` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_topologies_system` FOREIGN KEY (`system_id`) REFERENCES `systems` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for api_topology_edges
-- ----------------------------
DROP TABLE IF EXISTS `api_topology_edges`;
CREATE TABLE `api_topology_edges` (
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
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for api_topology_nodes
-- ----------------------------
DROP TABLE IF EXISTS `api_topology_nodes`;
CREATE TABLE `api_topology_nodes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `topology_id` bigint unsigned NOT NULL,
  `node_key` varchar(128) NOT NULL COMMENT 'Stable logical node id/key',
  `name` varchar(255) NOT NULL,
  `layer` smallint unsigned NOT NULL DEFAULT '1',
  `protocol` enum('HTTP','gRPC','DB','MQ','OTHER') NOT NULL DEFAULT 'HTTP',
  `metadata` json DEFAULT NULL COMMENT 'Additional metadata',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nodes_topology_nodekey` (`topology_id`,`node_key`),
  KEY `idx_nodes_topology` (`topology_id`),
  KEY `idx_nodes_layer` (`layer`),
  CONSTRAINT `fk_nodes_topology` FOREIGN KEY (`topology_id`) REFERENCES `api_topologies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for apis
-- ----------------------------
DROP TABLE IF EXISTS `apis`;
CREATE TABLE `apis` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `system_id` bigint unsigned NOT NULL,
  `operation_id` varchar(128) NOT NULL COMMENT 'OpenAPI operationId',
  `method` enum('GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS','TRACE') NOT NULL COMMENT 'HTTP method',
  `path` varchar(512) NOT NULL COMMENT 'HTTP path',
  `summary` varchar(512) DEFAULT NULL,
  `tags` json DEFAULT NULL COMMENT 'Array of tags',
  `version` varchar(64) DEFAULT NULL COMMENT 'API definition version',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `base_url` varchar(512) DEFAULT NULL COMMENT '协议+主机+端口，不含path',
  `content_type` varchar(64) NOT NULL DEFAULT 'application/json' COMMENT '请求Content-Type',
  `headers_template` json DEFAULT NULL COMMENT '请求头模板，支持变量占位',
  `auth_type` enum('NONE','TOKEN','COOKIE','BASIC','PROFILE') NOT NULL DEFAULT 'NONE' COMMENT '认证类型',
  `auth_template` json DEFAULT NULL COMMENT '认证配置（如Bearer Token变量名等）',
  `path_params` json DEFAULT NULL COMMENT '路径参数定义',
  `query_params` json DEFAULT NULL COMMENT '查询参数定义',
  `body_template` json DEFAULT NULL COMMENT '请求体模板（JSON），支持变量占位',
  `variables` json DEFAULT NULL COMMENT '变量定义数组：name/type/required/default/desc/source',
  `timeout_ms` int unsigned NOT NULL DEFAULT '15000' COMMENT '超时毫秒',
  `retry_config` json DEFAULT NULL COMMENT '重试策略（可选）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apis_system_op` (`system_id`,`operation_id`),
  KEY `idx_apis_system` (`system_id`),
  KEY `idx_apis_method_path` (`method`,`path`),
  CONSTRAINT `fk_apis_system` FOREIGN KEY (`system_id`) REFERENCES `systems` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for audit_logs
-- ----------------------------
DROP TABLE IF EXISTS `audit_logs`;
CREATE TABLE `audit_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(32) NOT NULL COMMENT 'TASK,TEMPLATE,EXECUTION,...',
  `entity_id` bigint unsigned NOT NULL,
  `action` varchar(32) NOT NULL COMMENT 'CREATE,UPDATE,DELETE,EXECUTE,PAUSE,RESUME,ARCHIVE',
  `actor` varchar(64) NOT NULL,
  `details` json DEFAULT NULL COMMENT 'Delta, payload, or other context',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_entity` (`entity_type`,`entity_id`),
  KEY `idx_audit_action` (`action`),
  KEY `idx_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for baggage_map
-- ----------------------------
DROP TABLE IF EXISTS `baggage_map`;
CREATE TABLE `baggage_map` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `execution_id` bigint NOT NULL COMMENT 'task_execution.id',
  `service_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务名，如 ts-order-service',
  `value` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'baggage 串，如 chaos.f1=...,chaos.f2=...',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exec_service` (`execution_id`,`service_name`),
  KEY `idx_service` (`service_name`)
) ENGINE=InnoDB AUTO_INCREMENT=343 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for detection_tasks
-- ----------------------------
DROP TABLE IF EXISTS `detection_tasks`;
CREATE TABLE `detection_tasks` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` text,
  `system_id` bigint unsigned NOT NULL,
  `api_id` bigint unsigned NOT NULL COMMENT 'Selected API',
  `created_by` varchar(64) NOT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `archived_at` datetime DEFAULT NULL,
  `fault_configurations_id` bigint DEFAULT NULL COMMENT '故障配置id',
  `slo_id` bigint DEFAULT NULL COMMENT 'slo id',
  `request_num` int NOT NULL COMMENT '单测试请求数',
  `api_definition_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tasks_system` (`system_id`),
  KEY `idx_tasks_api` (`api_id`),
  KEY `idx_tasks_created_at` (`created_at`),
  CONSTRAINT `fk_tasks_api` FOREIGN KEY (`api_id`) REFERENCES `apis` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tasks_system` FOREIGN KEY (`system_id`) REFERENCES `systems` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for fault_config
-- ----------------------------
DROP TABLE IF EXISTS `fault_config`;
CREATE TABLE `fault_config` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `node_id` bigint unsigned NOT NULL,
  `faultscript` text NOT NULL,
  `type` varchar(30) DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_node_id` (`node_id`),
  CONSTRAINT `fk_faultcfg_node` FOREIGN KEY (`node_id`) REFERENCES `api_topology_nodes` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=257 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for fault_types
-- ----------------------------
DROP TABLE IF EXISTS `fault_types`;
CREATE TABLE `fault_types` (
  `fault_type_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `fault_code` varchar(64) NOT NULL COMMENT '故障类型编码（唯一标识）',
  `name` varchar(255) NOT NULL COMMENT '故障名称',
  `description` text COMMENT '故障描述',
  `category` varchar(64) NOT NULL COMMENT '故障分类：CPU、Memory、Disk、Network、Process、Internal',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0禁用',
  `display_order` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `param_config` json NOT NULL COMMENT '参数配置定义（字段名、类型、默认值、是否必填、描述等）',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`fault_type_id`),
  UNIQUE KEY `uk_fault_code` (`fault_code`),
  KEY `idx_category_enabled` (`category`,`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='故障类型字典表';

-- ----------------------------
-- Table structure for http_req_def
-- ----------------------------
DROP TABLE IF EXISTS `http_req_def`;
CREATE TABLE `http_req_def` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(64) NOT NULL COMMENT '请求定义编码',
  `name` varchar(128) NOT NULL COMMENT '名称',
  `method` enum('GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS') NOT NULL COMMENT 'HTTP方法',
  `url_template` varchar(1024) NOT NULL COMMENT 'URL模板，路径占位用 {var}',
  `headers` json DEFAULT NULL COMMENT '请求头，如 {"Accept":"application/json","X-Req":"{{rid}}"}',
  `query_params` json DEFAULT NULL COMMENT '查询参数，如 {"q":"{{kw}}","page":"{{p}}"}',
  `body_mode` enum('NONE','JSON','FORM','RAW') NOT NULL DEFAULT 'NONE' COMMENT '请求体模式',
  `content_type` varchar(128) DEFAULT NULL COMMENT '显式Content-Type（可为空）',
  `body_template` json DEFAULT NULL COMMENT '当 body_mode=JSON/FORM：键值模板，如 {"name":"{{n}}"}',
  `raw_body` mediumtext COMMENT '当 body_mode=RAW：原文模板（可含 {{var}}）',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `api_id` bigint DEFAULT NULL COMMENT '所属API id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_method` (`method`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HTTP请求定义（极简）';

-- ----------------------------
-- Table structure for http_req_variables
-- ----------------------------
DROP TABLE IF EXISTS `http_req_variables`;
CREATE TABLE `http_req_variables` (
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

-- ----------------------------
-- Table structure for intercept_replay_results
-- ----------------------------
DROP TABLE IF EXISTS `intercept_replay_results`;
CREATE TABLE `intercept_replay_results` (
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
) ENGINE=InnoDB AUTO_INCREMENT=165 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for intercept_rules
-- ----------------------------
DROP TABLE IF EXISTS `intercept_rules`;
CREATE TABLE `intercept_rules` (
  `id` bigint NOT NULL,
  `service_name` varchar(255) COLLATE utf16_bin DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `fault_type` varchar(255) COLLATE utf16_bin DEFAULT NULL,
  `request_url` varchar(255) COLLATE utf16_bin DEFAULT NULL,
  `request_method` varchar(10) CHARACTER SET utf16 COLLATE utf16_bin DEFAULT NULL,
  `request_body` text COLLATE utf16_bin,
  `request_header` text COLLATE utf16_bin,
  `response_body` text COLLATE utf16_bin,
  `execution_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- ----------------------------
-- Table structure for request_patterns
-- ----------------------------
DROP TABLE IF EXISTS `request_patterns`;
CREATE TABLE `request_patterns` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `execution_id` bigint NOT NULL COMMENT '检测任务ID（如 task_...）',
  `service_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务名（如 ts-order-service）',
  `method` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'HTTP方法：GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE',
  `url` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '完整URL路径（含路径与查询串）',
  `request_headers` json DEFAULT NULL COMMENT '请求头（JSON 对象）',
  `request_body` json DEFAULT NULL COMMENT '请求体（JSON；若非JSON可改用 LONGTEXT）',
  `response_headers` json DEFAULT NULL COMMENT '响应头（JSON 对象）',
  `response_body` json DEFAULT NULL COMMENT '响应体（JSON；若非JSON可改用 LONGTEXT）',
  `response_status` smallint unsigned NOT NULL COMMENT 'HTTP响应码，如200/404/503',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`execution_id`),
  KEY `idx_service` (`service_name`),
  KEY `idx_task_service` (`execution_id`,`service_name`),
  KEY `idx_method_url` (`method`,`url`(255)),
  KEY `idx_status` (`response_status`)
) ENGINE=InnoDB AUTO_INCREMENT=545 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for systems
-- ----------------------------
DROP TABLE IF EXISTS `systems`;
CREATE TABLE `systems` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Surrogate PK',
  `system_key` varchar(64) NOT NULL COMMENT 'External system identifier (frontend systemId)',
  `name` varchar(255) NOT NULL COMMENT 'System name',
  `description` text COMMENT 'System description',
  `owner` varchar(64) DEFAULT NULL COMMENT 'Owner/user/group',
  `default_environment` varchar(64) DEFAULT NULL COMMENT 'Default environment if applicable',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_systems_system_key` (`system_key`),
  KEY `idx_systems_owner` (`owner`)
) ENGINE=InnoDB AUTO_INCREMENT=2002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for task_conclusion
-- ----------------------------
DROP TABLE IF EXISTS `task_conclusion`;
CREATE TABLE `task_conclusion` (
  `id` int NOT NULL AUTO_INCREMENT,
  `model_content` text COLLATE utf16_bin COMMENT '模型总结内容',
  `execution_id` int DEFAULT NULL COMMENT '执行id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- ----------------------------
-- Table structure for task_execution
-- ----------------------------
DROP TABLE IF EXISTS `task_execution`;
CREATE TABLE `task_execution` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'execution_id',
  `task_id` bigint NOT NULL COMMENT '业务任务ID',
  `namespace` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '如 train-ticket',
  `req_def_id` bigint DEFAULT NULL COMMENT '请求定义ID',
  `request_num` int NOT NULL DEFAULT '1' COMMENT '入口并发',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INIT' COMMENT 'INIT/GENERATING_CASES/ANALYZING_PATTERNS/RECORDING_READY/INJECTING_AND_REPLAYING/RULES_READY/LOAD_TEST_BASELINE/DONE/FAILED',
  `analyze_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'proxy 分析任务ID（task_...）',
  `record_id` bigint DEFAULT NULL COMMENT '请求模式分析批次ID（用于回看）',
  `intercept_record_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拦截器下发记录ID（如 exec_...）',
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
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for task_execution_log
-- ----------------------------
DROP TABLE IF EXISTS `task_execution_log`;
CREATE TABLE `task_execution_log` (
  `id` bigint NOT NULL,
  `execution_id` bigint NOT NULL,
  `ts` timestamp NULL DEFAULT NULL,
  `level` tinyint DEFAULT NULL COMMENT '0=DEBUG,1=INFO,2=WARN,3=ERROR',
  `message` text COLLATE utf16_bin,
  `created_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- ----------------------------
-- Table structure for task_slo
-- ----------------------------
DROP TABLE IF EXISTS `task_slo`;
CREATE TABLE `task_slo` (
  `id` bigint NOT NULL,
  `p95` int DEFAULT NULL COMMENT 'p95限制',
  `p99` int DEFAULT NULL COMMENT 'p99限制',
  `err_rate` int DEFAULT NULL COMMENT '错误率限制',
  `task_id` bigint DEFAULT NULL,
  `node_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_bin;

-- ----------------------------
-- Table structure for test_cases
-- ----------------------------
DROP TABLE IF EXISTS `test_cases`;
CREATE TABLE `test_cases` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint unsigned NOT NULL COMMENT '关联 detection_tasks.id',
  `case_type` enum('BASELINE','SINGLE','DUAL') NOT NULL COMMENT '用例类型',
  `target_count` tinyint unsigned NOT NULL COMMENT '目标数量：0/1/2',
  `faults_json` json NOT NULL COMMENT 'faults 快照：[ {namespace, serviceName, faultDefinition}, ... ]',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `execution_id` bigint DEFAULT NULL COMMENT '记录id',
  PRIMARY KEY (`id`),
  KEY `idx_detection_task_id` (`task_id`),
  CONSTRAINT `fk_tc_detection_task` FOREIGN KEY (`task_id`) REFERENCES `detection_tasks` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_target_count` CHECK ((`target_count` in (0,1,2)))
) ENGINE=InnoDB AUTO_INCREMENT=3097 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试用例定义（增强版简化用例快照）';

-- ----------------------------
-- Table structure for test_result
-- ----------------------------
DROP TABLE IF EXISTS `test_result`;
CREATE TABLE `test_result` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `execution_id` varchar(64) NOT NULL,
  `test_case_id` bigint NOT NULL,
  `p50` int DEFAULT NULL COMMENT 'ms',
  `p95` int DEFAULT NULL,
  `p99` int DEFAULT NULL,
  `err_rate` decimal(5,2) DEFAULT NULL COMMENT '0-100',
  `request_url` text,
  `request_method` varchar(10) DEFAULT NULL,
  `response_code` int DEFAULT NULL,
  `response_body` longtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_exec_case` (`execution_id`,`test_case_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1205 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
