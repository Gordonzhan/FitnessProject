-- R7：匿名人群训练百分位对比样本（日级匿名快照，MySQL 5.7+，可重复执行）。
-- participant_key 仅为不可逆或合成参与者标识；接口不得返回该字段或任何个人明细。
USE `fitness_diary`;

CREATE TABLE IF NOT EXISTS `comparison_daily_samples` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `participant_key` VARCHAR(64) NOT NULL,
  `sample_date` DATE NOT NULL,
  `goal` VARCHAR(20) NOT NULL,
  `completed_sessions` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `planned_sessions` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `cancelled_sessions` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `actual_calories` DECIMAL(10,2) NOT NULL DEFAULT 0,
  `source` VARCHAR(20) NOT NULL COMMENT 'ANONYMIZED 或 SYNTHETIC',
  `dataset_version` VARCHAR(30) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comparison_dataset_participant_date` (`dataset_version`, `participant_key`, `sample_date`),
  KEY `idx_comparison_source_date_goal` (`source`, `sample_date`, `goal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS `comparison_daily_rows`, COUNT(DISTINCT `participant_key`) AS `comparison_participants`
FROM `comparison_daily_samples`;
