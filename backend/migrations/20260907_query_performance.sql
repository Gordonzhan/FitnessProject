-- 第八项：高频查询组合索引（MySQL 5.7）。
-- 仅在已依次执行 database.sql 和前七项迁移的数据库上执行一次。
USE `fitness_diary`;

-- 菜谱列表按用户筛选并按最近更新时间展示。
ALTER TABLE `recipes`
  ADD INDEX `idx_recipes_user_updated` (`user_id`, `updated_at`, `id`);

-- 每日摄入列表、热量汇总和逐条移除都以用户 + 日期为前缀。
ALTER TABLE `daily_intake`
  ADD INDEX `idx_daily_intake_user_date_id` (`user_id`, `date`, `id`);

-- 分类筛选后仍需按推荐顺序分页；同时优化分类列表的 DISTINCT 查询。
ALTER TABLE `food_database`
  ADD INDEX `idx_food_enabled_category_sort` (`enabled`, `category`, `sort_order`);

ALTER TABLE `exercise_database`
  ADD INDEX `idx_exercise_enabled_category_sort` (`enabled`, `category`, `sort_order`);

SHOW INDEX FROM `recipes` WHERE Key_name = 'idx_recipes_user_updated';
SHOW INDEX FROM `daily_intake` WHERE Key_name = 'idx_daily_intake_user_date_id';
SHOW INDEX FROM `food_database` WHERE Key_name = 'idx_food_enabled_category_sort';
SHOW INDEX FROM `exercise_database` WHERE Key_name = 'idx_exercise_enabled_category_sort';
