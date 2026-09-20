-- R6：微信运动步数与独立估算消耗（MySQL 5.7+，可重复执行）。
-- 估算值不写入 workouts，也不参与 R5 已验收的实际训练消耗和能量差。
USE `fitness_diary`;

CREATE TABLE IF NOT EXISTS `activity_daily_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `activity_date` DATE NOT NULL,
  `steps` INT UNSIGNED NOT NULL,
  `estimated_calories` DECIMAL(10,2) NOT NULL,
  `reference_weight` DECIMAL(5,2) NOT NULL,
  `source` VARCHAR(30) NOT NULL,
  `estimation_version` VARCHAR(30) NOT NULL,
  `source_timestamp` BIGINT NOT NULL,
  `synced_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user_source_date` (`user_id`, `source`, `activity_date`),
  KEY `idx_activity_user_date` (`user_id`, `activity_date`),
  CONSTRAINT `fk_activity_daily_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS `activity_daily_rows`, COUNT(DISTINCT `user_id`) AS `users_with_activity`
FROM `activity_daily_records`;
