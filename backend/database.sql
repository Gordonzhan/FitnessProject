-- 健身饮食日记小程序数据库初始化脚本
-- MySQL 5.7.26 兼容

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `fitness_diary` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `fitness_diary`;

-- 删除旧表（如果存在）
DROP TABLE IF EXISTS `exercise_database`;
DROP TABLE IF EXISTS `food_database`;
DROP TABLE IF EXISTS `operation_logs`;
DROP TABLE IF EXISTS `comparison_daily_samples`;
DROP TABLE IF EXISTS `activity_daily_records`;
DROP TABLE IF EXISTS `weight_records`;
DROP TABLE IF EXISTS `daily_intake`;
DROP TABLE IF EXISTS `exercises`;
DROP TABLE IF EXISTS `workouts`;
DROP TABLE IF EXISTS `ingredients`;
DROP TABLE IF EXISTS `recipe_images`;
DROP TABLE IF EXISTS `recipes`;
DROP TABLE IF EXISTS `users`;

-- 1. 用户表
CREATE TABLE `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `openid` VARCHAR(50) NOT NULL,
  `display_name` VARCHAR(30) NULL COMMENT '用户自填展示昵称',
  `gender` VARCHAR(10) NOT NULL,
  `age` INT NOT NULL,
  `height` DECIMAL(5,2) NOT NULL,
  `weight` DECIMAL(5,2) NOT NULL,
  `goal` VARCHAR(20) NOT NULL,
  `activity_level` DECIMAL(6,3) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 菜谱表
CREATE TABLE `recipes` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `recipe_id` VARCHAR(50) NOT NULL,
  `meal_type` VARCHAR(20) NOT NULL,
  `cover_image` VARCHAR(255),
  `steps` TEXT,
  `protein` DECIMAL(10,2) NOT NULL,
  `carb` DECIMAL(10,2) NOT NULL,
  `fat` DECIMAL(10,2) NOT NULL,
  `calorie` DECIMAL(10,2) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_recipe_id` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 菜谱图片表
CREATE TABLE `recipe_images` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `recipe_id` BIGINT NOT NULL,
  `image_url` VARCHAR(255) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_recipe_id` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 食材表
CREATE TABLE `ingredients` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `recipe_id` BIGINT NOT NULL,
  `food_name` VARCHAR(50) NOT NULL,
  `weight` DECIMAL(10,2) NOT NULL,
  `protein` DECIMAL(10,2) NOT NULL,
  `carb` DECIMAL(10,2) NOT NULL,
  `fat` DECIMAL(10,2) NOT NULL,
  `calorie` DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_recipe_id` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 健身记录表
CREATE TABLE `workouts` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `workout_id` VARCHAR(50) NOT NULL,
  `date` DATE NOT NULL,
  `total_calories` DECIMAL(10,2) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'PLANNED、COMPLETED或CANCELLED',
  `estimated_calories` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '计划或动作计算的预计消耗',
  `actual_calories` DECIMAL(10,2) NULL COMMENT '确认完成后计入统计的实际消耗',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_date` (`date`),
  KEY `idx_workouts_user_status_date` (`user_id`, `status`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 训练动作表
CREATE TABLE `exercises` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `workout_id` BIGINT NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `weight` DECIMAL(10,2),
  `sets` INT NOT NULL,
  `reps` INT NOT NULL,
  `calories` DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_workout_id` (`workout_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 今日饮食表
CREATE TABLE `daily_intake` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `date` DATE NOT NULL,
  `recipe_id` BIGINT NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_date` (`date`),
  KEY `idx_recipe_id` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 食物数据库表
CREATE TABLE `food_database` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `food_name` VARCHAR(50) NOT NULL,
  `protein` DECIMAL(10,2) NOT NULL,
  `carb` DECIMAL(10,2) NOT NULL,
  `fat` DECIMAL(10,2) NOT NULL,
  `calorie` DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_food_name` (`food_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. 训练动作数据库表
CREATE TABLE `exercise_database` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  `met` DECIMAL(3,1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- R5：体重历史；同日允许多次记录，分析按当天最后写入的一条取值
CREATE TABLE `weight_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `record_date` DATE NOT NULL,
  `weight` DECIMAL(5,2) NOT NULL,
  `source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  `recorded_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_weight_records_user_date_id` (`user_id`, `record_date`, `id`),
  CONSTRAINT `fk_weight_records_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- R6：微信运动汇总步数及独立估算消耗，不写入训练实际消耗
CREATE TABLE `activity_daily_records` (
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
  CONSTRAINT `fk_activity_daily_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- R7：匿名人群训练对比日级样本；不保存或暴露用户主键
CREATE TABLE `comparison_daily_samples` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `participant_key` VARCHAR(64) NOT NULL,
  `masked_display_name` VARCHAR(40) NOT NULL DEFAULT '训***者' COMMENT '已脱敏展示名，不保存原始昵称',
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

-- U1：OSS 直传一次性授权；不保存 STS 临时凭证、签名或图片 URL
CREATE TABLE `image_upload_tickets` (
  `ticket_id` CHAR(36) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `object_key` VARCHAR(255) NOT NULL,
  `expected_size` BIGINT UNSIGNED NOT NULL,
  `expected_format` VARCHAR(10) NOT NULL,
  `expected_mime` VARCHAR(30) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
  `expires_at` DATETIME(6) NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `confirmed_at` DATETIME(6) NULL,
  PRIMARY KEY (`ticket_id`),
  UNIQUE KEY `uk_image_upload_ticket_object_key` (`object_key`),
  KEY `idx_image_upload_ticket_user_status_expiry` (`user_id`, `status`, `expires_at`),
  CONSTRAINT `fk_image_upload_ticket_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. 操作日志表
CREATE TABLE `operation_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NULL COMMENT '操作用户ID，无法识别用户时为空',
  `request_id` VARCHAR(64) NULL COMMENT '请求追踪编号',
  `event_type` VARCHAR(50) NULL COMMENT '审计事件类型，旧日志为空',
  `target_ref` VARCHAR(100) NULL COMMENT '目标业务编号或不可逆摘要',
  `result` VARCHAR(10) NULL COMMENT 'SUCCESS或FAILURE',
  `ip` VARCHAR(50) NULL COMMENT '客户端IP地址',
  `interface_name` VARCHAR(255) NOT NULL COMMENT '请求接口路径',
  `method` VARCHAR(20) NOT NULL COMMENT 'HTTP请求方法',
  `request_params` TEXT NULL COMMENT '请求参数',
  `status_code` INT NULL COMMENT '业务或HTTP状态码',
  `error_message` TEXT NULL COMMENT '异常信息',
  `execution_time` BIGINT NOT NULL COMMENT '接口执行耗时，单位毫秒',
  `operation_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operation_logs_user_id` (`user_id`),
  KEY `idx_operation_logs_operation_time` (`operation_time`),
  KEY `idx_operation_logs_interface_name` (`interface_name`),
  KEY `idx_operation_logs_request_id` (`request_id`),
  KEY `idx_operation_logs_status_time` (`status_code`, `operation_time`),
  KEY `idx_operation_logs_event_time` (`event_type`, `operation_time`),
  KEY `idx_operation_logs_result_time` (`result`, `operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务端接口操作日志';

-- 添加外键约束
ALTER TABLE `recipes` ADD CONSTRAINT `fk_recipes_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `recipe_images` ADD CONSTRAINT `fk_recipe_images_recipe_id` FOREIGN KEY (`recipe_id`) REFERENCES `recipes` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `ingredients` ADD CONSTRAINT `fk_ingredients_recipe_id` FOREIGN KEY (`recipe_id`) REFERENCES `recipes` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `workouts` ADD CONSTRAINT `fk_workouts_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `exercises` ADD CONSTRAINT `fk_exercises_workout_id` FOREIGN KEY (`workout_id`) REFERENCES `workouts` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `daily_intake` ADD CONSTRAINT `fk_daily_intake_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `daily_intake` ADD CONSTRAINT `fk_daily_intake_recipe_id` FOREIGN KEY (`recipe_id`) REFERENCES `recipes` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `operation_logs` ADD CONSTRAINT `fk_operation_logs_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- 初始化食物数据库
INSERT INTO `food_database` (`food_name`, `protein`, `carb`, `fat`, `calorie`) VALUES
('鸡胸肉', 25.0, 0.0, 5.0, 165.0),
('米饭', 2.6, 25.6, 0.3, 116.0),
('鸡蛋', 13.0, 1.1, 8.6, 155.0),
('牛奶', 3.4, 5.0, 3.2, 66.0),
('苹果', 0.3, 13.8, 0.2, 52.0),
('牛肉', 26.0, 0.0, 15.0, 250.0),
('猪肉', 20.0, 0.0, 30.0, 350.0),
('鱼肉', 22.0, 0.0, 8.0, 180.0),
('虾', 24.0, 0.0, 2.0, 100.0),
('面包', 9.0, 50.0, 3.0, 265.0),
('面条', 8.0, 25.0, 1.0, 140.0),
('土豆', 2.0, 17.0, 0.1, 77.0),
('西红柿', 1.0, 4.0, 0.2, 18.0),
('黄瓜', 0.8, 3.0, 0.2, 15.0),
('胡萝卜', 1.0, 10.0, 0.2, 41.0),
('西兰花', 2.8, 7.0, 0.4, 34.0),
('生菜', 1.4, 3.0, 0.2, 15.0),
('豆腐', 8.0, 2.0, 4.0, 76.0),
('香蕉', 1.1, 23.0, 0.3, 89.0),
('橙子', 0.9, 12.0, 0.2, 47.0);

-- 初始化训练动作数据库
INSERT INTO `exercise_database` (`name`, `met`) VALUES
('卧推', 5.0),
('深蹲', 5.0),
('硬拉', 6.0),
('引体向上', 8.0),
('跑步', 9.8);

-- 初始化默认用户（测试用）
INSERT INTO `users` (`openid`, `gender`, `age`, `height`, `weight`, `goal`, `activity_level`) VALUES
('test_openid', 'male', 25, 175.0, 70.0, 'maintain', 1.375);

-- 提示信息
SELECT '数据库初始化完成！' AS message;
