-- AI 食材识别 MVP：通用兜底食材、未匹配候选统计，以及菜谱食材映射快照。
-- 通用数据是保守估算，仅作为用户无法确定具体品种时的人工确认选项。
USE `fitness_diary`;

ALTER TABLE `ingredients`
  ADD COLUMN `recognized_name` VARCHAR(100) NULL AFTER `food_name`,
  ADD COLUMN `food_database_id` BIGINT NULL AFTER `recognized_name`,
  ADD COLUMN `mapping_source` VARCHAR(30) NOT NULL DEFAULT 'MANUAL' AFTER `food_database_id`,
  ADD INDEX `idx_ingredients_food_database_id` (`food_database_id`),
  ADD CONSTRAINT `fk_ingredients_food_database_id`
    FOREIGN KEY (`food_database_id`) REFERENCES `food_database` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE `food_database_candidates` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `normalized_name` VARCHAR(100) NOT NULL,
  `recognized_name` VARCHAR(100) NOT NULL,
  `occurrence_count` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `last_cooking_method` VARCHAR(50) NULL,
  `suggested_category` VARCHAR(30) NULL,
  `suggested_aliases` VARCHAR(255) NULL,
  `review_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `first_seen_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_seen_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_candidate_normalized_name` (`normalized_name`),
  KEY `idx_food_candidate_review_count` (`review_status`, `occurrence_count`, `last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `food_database`
(`food_name`,`aliases`,`category`,`serving_state`,`protein`,`carb`,`fat`,`calorie`,
 `source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
('鱼肉（熟，通用）','熟鱼肉,普通鱼肉','肉禽水产','熟重',22.0,0.0,6.0,145,
 'SYSTEM_GENERIC_ESTIMATE','generic-fish-cooked','generic-v1',1,20),
('鸡肉（熟，通用）','熟鸡肉,普通鸡肉','肉禽水产','熟重',27.0,0.0,8.0,180,
 'SYSTEM_GENERIC_ESTIMATE','generic-chicken-cooked','generic-v1',1,20),
('绿叶蔬菜（熟，通用）','熟绿叶菜,熟青菜','蔬菜','熟重',2.0,4.0,0.5,30,
 'SYSTEM_GENERIC_ESTIMATE','generic-leafy-vegetable-cooked','generic-v1',1,20),
('混合蔬菜（熟，通用）','熟混合蔬菜,杂菜','蔬菜','熟重',2.0,8.0,3.0,65,
 'SYSTEM_GENERIC_ESTIMATE','generic-mixed-vegetable-cooked','generic-v1',1,15)
ON DUPLICATE KEY UPDATE
  `aliases`=VALUES(`aliases`),`category`=VALUES(`category`),
  `serving_state`=VALUES(`serving_state`),`protein`=VALUES(`protein`),
  `carb`=VALUES(`carb`),`fat`=VALUES(`fat`),`calorie`=VALUES(`calorie`),
  `source_name`=VALUES(`source_name`),`source_ref`=VALUES(`source_ref`),
  `data_version`=VALUES(`data_version`),`enabled`=VALUES(`enabled`),
  `sort_order`=VALUES(`sort_order`);
