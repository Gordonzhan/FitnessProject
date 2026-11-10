-- AI 训练阶段 A4：用户训练偏好、稳定动作编号和跟练说明。
-- 仅增加可选偏好和动作目录元数据，不修改 workouts / exercises 的记录语义。
USE `fitness_diary`;

CREATE TABLE IF NOT EXISTS `user_training_preferences` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `experience_level` VARCHAR(20) NOT NULL,
  `weekly_days` TINYINT UNSIGNED NOT NULL,
  `session_minutes` SMALLINT UNSIGNED NOT NULL,
  `training_location` VARCHAR(20) NOT NULL,
  `training_goal` VARCHAR(30) NOT NULL,
  `equipment_tags` VARCHAR(500) NULL,
  `limitation_tags` VARCHAR(300) NULL,
  `preferred_exercise_codes` TEXT NULL,
  `excluded_exercise_codes` TEXT NULL,
  `target_rpe_min` TINYINT UNSIGNED NOT NULL,
  `target_rpe_max` TINYINT UNSIGNED NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_training_preferences_user` (`user_id`),
  CONSTRAINT `fk_training_preferences_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MySQL 5.7 没有 ADD COLUMN IF NOT EXISTS，通过 information_schema 保持脚本可重复执行。
DROP PROCEDURE IF EXISTS `a4_add_exercise_column`;
DELIMITER $$
CREATE PROCEDURE `a4_add_exercise_column`(IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exercise_database' AND COLUMN_NAME = p_column_name
  ) THEN
    SET @a4_sql = CONCAT('ALTER TABLE `exercise_database` ADD COLUMN `', p_column_name, '` ', p_column_definition);
    PREPARE a4_stmt FROM @a4_sql;
    EXECUTE a4_stmt;
    DEALLOCATE PREPARE a4_stmt;
  END IF;
END$$
DELIMITER ;

CALL `a4_add_exercise_column`('exercise_code', 'VARCHAR(32) NULL AFTER `id`');
CALL `a4_add_exercise_column`('instructions', 'TEXT NULL AFTER `sort_order`');
CALL `a4_add_exercise_column`('safety_cues', 'TEXT NULL AFTER `instructions`');
CALL `a4_add_exercise_column`('common_mistakes', 'TEXT NULL AFTER `safety_cues`');
CALL `a4_add_exercise_column`('limitation_tags', 'VARCHAR(255) NULL AFTER `common_mistakes`');
CALL `a4_add_exercise_column`('alternative_exercise_codes', 'VARCHAR(500) NULL AFTER `limitation_tags`');
CALL `a4_add_exercise_column`('cover_url', 'VARCHAR(500) NULL AFTER `alternative_exercise_codes`');
CALL `a4_add_exercise_column`('media_url', 'VARCHAR(500) NULL AFTER `cover_url`');
CALL `a4_add_exercise_column`('media_type', 'VARCHAR(20) NOT NULL DEFAULT ''NONE'' AFTER `media_url`');
CALL `a4_add_exercise_column`('default_sets', 'TINYINT UNSIGNED NULL AFTER `media_type`');
CALL `a4_add_exercise_column`('default_reps', 'SMALLINT UNSIGNED NULL AFTER `default_sets`');
CALL `a4_add_exercise_column`('default_duration_seconds', 'SMALLINT UNSIGNED NULL AFTER `default_reps`');
CALL `a4_add_exercise_column`('default_rest_seconds', 'SMALLINT UNSIGNED NULL AFTER `default_duration_seconds`');
CALL `a4_add_exercise_column`('default_rpe', 'TINYINT UNSIGNED NULL AFTER `default_rest_seconds`');
CALL `a4_add_exercise_column`('tempo', 'VARCHAR(20) NULL AFTER `default_rpe`');
CALL `a4_add_exercise_column`('follow_along_enabled', 'TINYINT(1) NOT NULL DEFAULT 1 AFTER `tempo`');
DROP PROCEDURE `a4_add_exercise_column`;

-- 编号仅在为空时生成；同一标准名称在不同环境得到相同编号，后续改名也不会重写已有编号。
UPDATE `exercise_database`
SET `exercise_code` = CONCAT('EX_', UPPER(SUBSTRING(SHA2(`name`, 256), 1, 16)))
WHERE `exercise_code` IS NULL OR `exercise_code` = '';

DROP PROCEDURE IF EXISTS `a4_finalize_exercise_code`;
DELIMITER $$
CREATE PROCEDURE `a4_finalize_exercise_code`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exercise_database'
      AND COLUMN_NAME = 'exercise_code' AND IS_NULLABLE = 'YES'
  ) THEN
    ALTER TABLE `exercise_database` MODIFY COLUMN `exercise_code` VARCHAR(32) NOT NULL;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exercise_database'
      AND INDEX_NAME = 'uk_exercise_code'
  ) THEN
    ALTER TABLE `exercise_database` ADD UNIQUE KEY `uk_exercise_code` (`exercise_code`);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exercise_database'
      AND INDEX_NAME = 'idx_exercise_follow_equipment'
  ) THEN
    ALTER TABLE `exercise_database`
      ADD INDEX `idx_exercise_follow_equipment` (`enabled`, `follow_along_enabled`, `equipment`, `sort_order`);
  END IF;
END$$
DELIMITER ;
CALL `a4_finalize_exercise_code`();
DROP PROCEDURE `a4_finalize_exercise_code`;

-- 首版说明采用可复核的通用动作原则；媒体字段保持空值，避免引用未经审核的外部内容。
UPDATE `exercise_database` SET
  `instructions` = CASE
    WHEN `category` = '有氧' THEN '先以轻松速度热身，保持可控节奏和自然呼吸，再逐步进入目标强度。'
    WHEN `category` = '灵活性' THEN '缓慢进入动作范围，在无明显疼痛的位置保持稳定呼吸，不进行弹震式拉伸。'
    ELSE '先建立稳定起始姿势，收紧核心，以可控速度完成全程动作并保持自然呼吸。'
  END,
  `safety_cues` = CASE
    WHEN `category` = '有氧' THEN '从低强度开始；如出现胸痛、明显呼吸困难、眩晕或急性疼痛，立即停止并寻求专业帮助。'
    ELSE '使用能够保持动作质量的负荷；出现锐痛、麻木、眩晕或明显不适时立即停止。'
  END,
  `common_mistakes` = CASE
    WHEN `category` = '有氧' THEN '起步过快、忽略热身、为了速度牺牲姿势。'
    ELSE '动作速度过快、使用惯性、关节失去稳定、负荷超过当前能力。'
  END,
  `default_sets` = CASE WHEN `category` IN ('有氧','灵活性') THEN NULL ELSE 3 END,
  `default_reps` = CASE WHEN `category` IN ('有氧','灵活性') THEN NULL ELSE 10 END,
  `default_duration_seconds` = CASE WHEN `category` = '有氧' THEN 600 WHEN `category` = '灵活性' THEN 30 ELSE NULL END,
  `default_rest_seconds` = CASE WHEN `intensity` = '高' THEN 120 WHEN `intensity` = '低' THEN 45 ELSE 60 END,
  `default_rpe` = CASE WHEN `intensity` = '高' THEN 8 WHEN `intensity` = '低' THEN 5 ELSE 7 END,
  `tempo` = CASE WHEN `category` IN ('有氧','灵活性') THEN NULL ELSE '2-0-2' END,
  `media_type` = COALESCE(NULLIF(`media_type`, ''), 'NONE'),
  `follow_along_enabled` = COALESCE(`follow_along_enabled`, 1)
WHERE `instructions` IS NULL OR `instructions` = '';

UPDATE `exercise_database`
SET `default_sets` = 3, `default_reps` = NULL, `default_duration_seconds` = 30,
    `default_rest_seconds` = 45, `default_rpe` = 6, `tempo` = NULL
WHERE `name` = '平板支撑';

UPDATE `exercise_database` SET `limitation_tags` =
  CASE
    WHEN `name` IN ('跑步','跑步机跑步','跳绳','开合跳','波比跳','爬楼梯') THEN 'KNEE,ANKLE,CARDIOVASCULAR,BALANCE'
    WHEN `primary_muscles` LIKE '%腿部%' THEN 'KNEE,LOWER_BACK,BALANCE'
    WHEN `primary_muscles` LIKE '%肩部%' OR `primary_muscles` LIKE '%胸部%' THEN 'SHOULDER,WRIST'
    WHEN `primary_muscles` LIKE '%背部%' THEN 'LOWER_BACK,SHOULDER'
    ELSE NULL
  END
WHERE `limitation_tags` IS NULL OR `limitation_tags` = '';

SET @a4_push_up = (SELECT `exercise_code` FROM `exercise_database` WHERE `name` = '俯卧撑' LIMIT 1);
SET @a4_bench = (SELECT `exercise_code` FROM `exercise_database` WHERE `name` = '卧推' LIMIT 1);
SET @a4_pulldown = (SELECT `exercise_code` FROM `exercise_database` WHERE `name` = '高位下拉' LIMIT 1);
SET @a4_pullup = (SELECT `exercise_code` FROM `exercise_database` WHERE `name` = '引体向上' LIMIT 1);
SET @a4_goblet = (SELECT `exercise_code` FROM `exercise_database` WHERE `name` = '高脚杯深蹲' LIMIT 1);
SET @a4_squat = (SELECT `exercise_code` FROM `exercise_database` WHERE `name` = '深蹲' LIMIT 1);
UPDATE `exercise_database` SET `alternative_exercise_codes` = @a4_push_up WHERE `name` = '卧推' AND @a4_push_up IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes` = @a4_bench WHERE `name` = '俯卧撑' AND @a4_bench IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes` = @a4_pulldown WHERE `name` = '引体向上' AND @a4_pulldown IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes` = @a4_pullup WHERE `name` = '高位下拉' AND @a4_pullup IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes` = @a4_goblet WHERE `name` = '深蹲' AND @a4_goblet IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes` = @a4_squat WHERE `name` = '高脚杯深蹲' AND @a4_squat IS NOT NULL;

SELECT COUNT(*) AS preference_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_training_preferences';
SELECT COUNT(*) AS invalid_exercise_code_count
FROM `exercise_database`
WHERE `exercise_code` IS NULL OR `exercise_code` = '';
