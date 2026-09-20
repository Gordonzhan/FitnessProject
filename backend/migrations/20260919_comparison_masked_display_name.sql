-- R7：为用户档案增加可选展示昵称，并在匿名样本中仅保存掩码后的名称。
-- 该迁移只执行一次；排行榜接口不得读取或返回 users.display_name 原文。
USE `fitness_diary`;

SET @add_user_display_name = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = 'fitness_diary' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'display_name') = 0,
  'ALTER TABLE `users` ADD COLUMN `display_name` VARCHAR(30) NULL COMMENT ''用户自填展示昵称，仅在生成匿名快照时读取'' AFTER `openid`',
  'SELECT 1'
);
PREPARE add_user_display_name_stmt FROM @add_user_display_name;
EXECUTE add_user_display_name_stmt;
DEALLOCATE PREPARE add_user_display_name_stmt;

SET @add_masked_display_name = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = 'fitness_diary' AND TABLE_NAME = 'comparison_daily_samples' AND COLUMN_NAME = 'masked_display_name') = 0,
  'ALTER TABLE `comparison_daily_samples` ADD COLUMN `masked_display_name` VARCHAR(40) NOT NULL DEFAULT ''训***者'' COMMENT ''已脱敏展示名，不保存原始昵称'' AFTER `participant_key`',
  'SELECT 1'
);
PREPARE add_masked_display_name_stmt FROM @add_masked_display_name;
EXECUTE add_masked_display_name_stmt;
DEALLOCATE PREPARE add_masked_display_name_stmt;
