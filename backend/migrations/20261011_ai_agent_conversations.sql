-- A7 Fitness Agent 对话：Java 持久化会话与脱敏消息，Python 不直接连接业务数据库。
USE `fitness_diary`;

CREATE TABLE IF NOT EXISTS `ai_agent_sessions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` VARCHAR(36) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `title` VARCHAR(80) NOT NULL DEFAULT 'Fitness Agent',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_session_id` (`session_id`),
  KEY `idx_ai_agent_session_user_updated` (`user_id`, `status`, `updated_at`),
  CONSTRAINT `fk_ai_agent_session_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_agent_messages` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `message_id` VARCHAR(36) NOT NULL,
  `session_db_id` BIGINT NOT NULL,
  `role` VARCHAR(16) NOT NULL,
  `content` TEXT NOT NULL,
  `provider` VARCHAR(30) NULL,
  `tool_summary` VARCHAR(500) NULL,
  `action_type` VARCHAR(30) NULL,
  `action_payload` MEDIUMTEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_message_id` (`message_id`),
  KEY `idx_ai_agent_message_session` (`session_db_id`, `id`),
  CONSTRAINT `fk_ai_agent_message_session` FOREIGN KEY (`session_db_id`) REFERENCES `ai_agent_sessions` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_ai_agent_message_role` CHECK (`role` IN ('USER','ASSISTANT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS a7_session_table_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_agent_sessions';
SELECT COUNT(*) AS a7_message_table_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_agent_messages';
