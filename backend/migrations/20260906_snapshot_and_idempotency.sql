-- 第七项：营养快照、业务唯一约束与请求幂等（MySQL 5.7）。
-- 执行前会由人工/发布脚本检查重复键；本脚本不自动删除或合并业务数据。
USE `fitness_diary`;

-- 先以可空列加入，再用当前关联菜谱回填现有饮食记录。
ALTER TABLE `daily_intake`
  ADD COLUMN `request_id` VARCHAR(64) NULL AFTER `recipe_id`,
  ADD COLUMN `recipe_business_id` VARCHAR(50) NULL AFTER `request_id`,
  ADD COLUMN `meal_type_snapshot` VARCHAR(20) NULL AFTER `recipe_business_id`,
  ADD COLUMN `protein_snapshot` DECIMAL(10,2) NULL AFTER `meal_type_snapshot`,
  ADD COLUMN `carb_snapshot` DECIMAL(10,2) NULL AFTER `protein_snapshot`,
  ADD COLUMN `fat_snapshot` DECIMAL(10,2) NULL AFTER `carb_snapshot`,
  ADD COLUMN `calorie_snapshot` DECIMAL(10,2) NULL AFTER `fat_snapshot`;

UPDATE `daily_intake` d
JOIN `recipes` r ON r.`id` = d.`recipe_id`
SET d.`request_id` = CONCAT('legacy-', d.`id`),
    d.`recipe_business_id` = r.`recipe_id`,
    d.`meal_type_snapshot` = r.`meal_type`,
    d.`protein_snapshot` = r.`protein`,
    d.`carb_snapshot` = r.`carb`,
    d.`fat_snapshot` = r.`fat`,
    d.`calorie_snapshot` = r.`calorie`;

ALTER TABLE `daily_intake`
  MODIFY COLUMN `request_id` VARCHAR(64) NOT NULL,
  MODIFY COLUMN `recipe_business_id` VARCHAR(50) NOT NULL,
  MODIFY COLUMN `meal_type_snapshot` VARCHAR(20) NOT NULL,
  MODIFY COLUMN `protein_snapshot` DECIMAL(10,2) NOT NULL,
  MODIFY COLUMN `carb_snapshot` DECIMAL(10,2) NOT NULL,
  MODIFY COLUMN `fat_snapshot` DECIMAL(10,2) NOT NULL,
  MODIFY COLUMN `calorie_snapshot` DECIMAL(10,2) NOT NULL,
  ADD UNIQUE KEY `uq_daily_intake_user_request` (`user_id`, `request_id`);

-- 快照已能独立展示历史数据；删除菜谱时仅清空关联，不再级联删除历史饮食。
ALTER TABLE `daily_intake` DROP FOREIGN KEY `fk_daily_intake_recipe_id`;
ALTER TABLE `daily_intake` MODIFY COLUMN `recipe_id` BIGINT NULL;
ALTER TABLE `daily_intake`
  ADD CONSTRAINT `fk_daily_intake_recipe_id`
  FOREIGN KEY (`recipe_id`) REFERENCES `recipes` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- 业务编号必须在用户范围内唯一；训练页的业务定义是一名用户每天一条训练记录。
ALTER TABLE `recipes`
  ADD UNIQUE KEY `uq_recipes_user_recipe` (`user_id`, `recipe_id`);

ALTER TABLE `workouts`
  ADD UNIQUE KEY `uq_workouts_user_workout` (`user_id`, `workout_id`),
  ADD UNIQUE KEY `uq_workouts_user_date` (`user_id`, `date`);

SELECT COUNT(*) AS `snapshot_rows` FROM `daily_intake`
WHERE `request_id` IS NOT NULL AND `calorie_snapshot` IS NOT NULL;
