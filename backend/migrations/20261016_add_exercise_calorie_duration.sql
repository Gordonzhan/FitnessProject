-- 增加动作级热量计算时长快照（MySQL 5.7+，可重复执行）。
-- 必须先执行 20261015_unify_workout_calorie_estimation.sql。
-- 历史热量是用户已看到的事实快照：本迁移只反推时长，不重算或覆盖 calories。
USE `fitness_diary`;

SET @calorie_duration_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='exercises'
    AND COLUMN_NAME='calorie_duration_seconds'
);
SET @calorie_duration_sql = IF(@calorie_duration_exists=0,
  'ALTER TABLE exercises ADD COLUMN calorie_duration_seconds INT UNSIGNED NULL COMMENT ''该动作热量计算使用的总秒数快照'' AFTER calories',
  'SELECT 1');
PREPARE calorie_duration_stmt FROM @calorie_duration_sql;
EXECUTE calorie_duration_stmt;
DEALLOCATE PREPARE calorie_duration_stmt;

-- 优先依据现有动作热量、动作库 MET 和训练参考体重反推，保持已验收热量口径不变。
UPDATE `exercises` e
INNER JOIN `workouts` w ON w.id=e.workout_id
INNER JOIN `exercise_database` ed ON ed.name=e.name AND ed.met>0
SET e.calorie_duration_seconds=GREATEST(
  1,
  LEAST(86400, ROUND(e.calories * 3600 / (ed.met * w.calorie_reference_weight)))
)
WHERE (e.calorie_duration_seconds IS NULL OR e.calorie_duration_seconds=0)
  AND w.calorie_reference_weight>0
  AND e.calories>0;

-- 零热量或无法匹配动作目录的历史行使用确定性结构时长；不删除记录，也不修改原热量。
UPDATE `exercises`
SET calorie_duration_seconds=GREATEST(1, sets * 30 + GREATEST(sets - 1, 0) * 60)
WHERE calorie_duration_seconds IS NULL OR calorie_duration_seconds=0;

ALTER TABLE `exercises`
  MODIFY COLUMN `calorie_duration_seconds` INT UNSIGNED NOT NULL
  COMMENT '该动作热量计算使用的总秒数快照';

-- 上线核验：missing_duration 必须为0，duration_seconds应为正数，calories保持迁移前数值。
SELECT COUNT(*) AS exercise_count,
       SUM(CASE WHEN calorie_duration_seconds IS NULL OR calorie_duration_seconds=0 THEN 1 ELSE 0 END)
         AS missing_duration,
       MIN(calorie_duration_seconds) AS min_duration_seconds,
       MAX(calorie_duration_seconds) AS max_duration_seconds
FROM `exercises`;
