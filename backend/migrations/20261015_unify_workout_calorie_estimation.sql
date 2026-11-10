-- 统一训练预计消耗口径（MySQL 5.7+，可重复执行）。
-- Java 保存计划时记录完整计划时长和参考体重；历史记录依据动作热量快照反推时长。
USE `fitness_diary`;

SET @duration_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='workouts'
    AND COLUMN_NAME='estimated_duration_minutes'
);
SET @duration_sql = IF(@duration_exists=0,
  'ALTER TABLE workouts ADD COLUMN estimated_duration_minutes SMALLINT UNSIGNED NULL COMMENT ''预计消耗覆盖的完整训练时长'' AFTER estimated_calories',
  'SELECT 1');
PREPARE duration_stmt FROM @duration_sql;
EXECUTE duration_stmt;
DEALLOCATE PREPARE duration_stmt;

SET @weight_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='workouts'
    AND COLUMN_NAME='calorie_reference_weight'
);
SET @weight_sql = IF(@weight_exists=0,
  'ALTER TABLE workouts ADD COLUMN calorie_reference_weight DECIMAL(5,2) NULL COMMENT ''预计消耗使用的体重快照'' AFTER estimated_duration_minutes',
  'SELECT 1');
PREPARE weight_stmt FROM @weight_sql;
EXECUTE weight_stmt;
DEALLOCATE PREPARE weight_stmt;

UPDATE `workouts` w
INNER JOIN `users` u ON u.id=w.user_id
SET w.calorie_reference_weight=u.weight
WHERE w.calorie_reference_weight IS NULL OR w.calorie_reference_weight<=0;

DROP TEMPORARY TABLE IF EXISTS `tmp_workout_duration_backfill`;
CREATE TEMPORARY TABLE `tmp_workout_duration_backfill` AS
SELECT w.id AS workout_id,
       GREATEST(1, ROUND(SUM(e.calories * 60 / (ed.met * w.calorie_reference_weight)))) AS duration_minutes
FROM `workouts` w
INNER JOIN `exercises` e ON e.workout_id=w.id
INNER JOIN `exercise_database` ed ON ed.name=e.name AND ed.met>0
WHERE w.estimated_duration_minutes IS NULL OR w.estimated_duration_minutes=0
GROUP BY w.id;

UPDATE `workouts` w
INNER JOIN `tmp_workout_duration_backfill` b ON b.workout_id=w.id
SET w.estimated_duration_minutes=b.duration_minutes
WHERE w.estimated_duration_minutes IS NULL OR w.estimated_duration_minutes=0;

-- 无法按旧热量快照匹配动作目录时，回退到组次结构时长，不删除也不重写旧热量。
UPDATE `workouts` w
SET w.estimated_duration_minutes=GREATEST(1, CEIL(COALESCE((
  SELECT SUM(e.sets * 30 + GREATEST(e.sets - 1, 0) * 60)
  FROM `exercises` e WHERE e.workout_id=w.id
), 60) / 60))
WHERE w.estimated_duration_minutes IS NULL OR w.estimated_duration_minutes=0;

DROP TEMPORARY TABLE IF EXISTS `tmp_workout_duration_backfill`;

ALTER TABLE `workouts`
  MODIFY COLUMN `estimated_duration_minutes` SMALLINT UNSIGNED NOT NULL,
  MODIFY COLUMN `calorie_reference_weight` DECIMAL(5,2) NOT NULL;

SELECT w.workout_id, w.date, w.estimated_calories,
       w.estimated_duration_minutes, w.calorie_reference_weight
FROM `workouts` w
ORDER BY w.date DESC, w.id DESC;
