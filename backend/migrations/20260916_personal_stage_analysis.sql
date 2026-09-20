-- R5：个人阶段性饮食与训练分析（MySQL 5.7+，可重复执行）。
-- 体重同日可记录多次；趋势查询按 (record_date, 最大 id) 取当天最后一条。
USE `fitness_diary`;

CREATE TABLE IF NOT EXISTS `weight_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `record_date` DATE NOT NULL,
  `weight` DECIMAL(5,2) NOT NULL,
  `source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  `recorded_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_weight_records_user_date_id` (`user_id`, `record_date`, `id`),
  CONSTRAINT `fk_weight_records_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 旧档案没有可靠的逐日体重历史，仅以档案更新时间回填一条基线，并明确标记来源。
-- 重复执行时，已有 BACKFILL 基线的用户不会再次插入。
INSERT INTO `weight_records` (`user_id`, `record_date`, `weight`, `source`, `recorded_at`)
SELECT u.`id`, DATE(COALESCE(u.`updated_at`, u.`created_at`, CURRENT_TIMESTAMP)),
       u.`weight`, 'BACKFILL', COALESCE(u.`updated_at`, u.`created_at`, CURRENT_TIMESTAMP)
FROM `users` u
WHERE NOT EXISTS (
  SELECT 1 FROM `weight_records` wr
  WHERE wr.`user_id` = u.`id` AND wr.`source` = 'BACKFILL'
);

SELECT COUNT(*) AS `weight_record_rows`,
       COUNT(DISTINCT `user_id`) AS `users_with_weight_history`
FROM `weight_records`;
