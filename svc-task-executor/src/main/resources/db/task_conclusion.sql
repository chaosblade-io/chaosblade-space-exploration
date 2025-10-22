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

-- MySQL DDL for task_conclusion table
CREATE TABLE IF NOT EXISTS `task_conclusion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `model_content` LONGTEXT NULL,
  `execution_id` BIGINT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_execution_id` (`execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

