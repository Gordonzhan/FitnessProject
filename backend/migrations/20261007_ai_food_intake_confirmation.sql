-- AI 食物识别 A3：为确认入账保存餐次、实际份数和食用时间。
-- 历史记录保留原快照，餐次标记为未指定，份数按 1 份解释。
USE `fitness_diary`;

ALTER TABLE `daily_intake`
  ADD COLUMN `meal_slot` VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED' AFTER `meal_type_snapshot`,
  ADD COLUMN `serving_count` DECIMAL(4,2) NOT NULL DEFAULT 1.00 AFTER `meal_slot`,
  ADD COLUMN `consumed_time` TIME NULL AFTER `serving_count`,
  ADD INDEX `idx_daily_intake_user_date_slot` (`user_id`, `date`, `meal_slot`, `id`);
