-- MySQL DDL for task_conclusion table
CREATE TABLE IF NOT EXISTS `task_conclusion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `model_content` LONGTEXT NULL,
  `execution_id` BIGINT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_execution_id` (`execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

