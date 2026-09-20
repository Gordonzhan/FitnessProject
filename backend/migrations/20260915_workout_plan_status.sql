-- R3：训练计划与实际训练记录分离（MySQL 5.7+，可重复执行）。
-- 既有训练全部按已完成记录迁移，预计与实际消耗均沿用原 total_calories。
USE `fitness_diary`;

SET @status_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='workouts' AND COLUMN_NAME='status'
);
SET @status_sql = IF(@status_exists=0,
  'ALTER TABLE workouts ADD COLUMN status VARCHAR(20) NULL COMMENT ''PLANNED、COMPLETED或CANCELLED'' AFTER total_calories',
  'SELECT 1');
PREPARE status_stmt FROM @status_sql;
EXECUTE status_stmt;
DEALLOCATE PREPARE status_stmt;

SET @estimated_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='workouts' AND COLUMN_NAME='estimated_calories'
);
SET @estimated_sql = IF(@estimated_exists=0,
  'ALTER TABLE workouts ADD COLUMN estimated_calories DECIMAL(10,2) NULL COMMENT ''计划或动作计算的预计消耗'' AFTER status',
  'SELECT 1');
PREPARE estimated_stmt FROM @estimated_sql;
EXECUTE estimated_stmt;
DEALLOCATE PREPARE estimated_stmt;

SET @actual_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='workouts' AND COLUMN_NAME='actual_calories'
);
SET @actual_sql = IF(@actual_exists=0,
  'ALTER TABLE workouts ADD COLUMN actual_calories DECIMAL(10,2) NULL COMMENT ''确认完成后计入统计的实际消耗'' AFTER estimated_calories',
  'SELECT 1');
PREPARE actual_stmt FROM @actual_sql;
EXECUTE actual_stmt;
DEALLOCATE PREPARE actual_stmt;

UPDATE `workouts` SET `status`='COMPLETED' WHERE `status` IS NULL;
UPDATE `workouts` SET `estimated_calories`=`total_calories` WHERE `estimated_calories` IS NULL;
UPDATE `workouts` SET `actual_calories`=`total_calories`
WHERE `status`='COMPLETED' AND `actual_calories` IS NULL;

ALTER TABLE `workouts`
  MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  MODIFY COLUMN `estimated_calories` DECIMAL(10,2) NOT NULL DEFAULT 0;

SET @status_date_index_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='workouts' AND INDEX_NAME='idx_workouts_user_status_date'
);
SET @status_date_index_sql = IF(@status_date_index_exists=0,
  'ALTER TABLE workouts ADD INDEX idx_workouts_user_status_date (user_id, status, date)',
  'SELECT 1');
PREPARE status_date_index_stmt FROM @status_date_index_sql;
EXECUTE status_date_index_stmt;
DEALLOCATE PREPARE status_date_index_stmt;

SELECT `status`, COUNT(*) AS records,
       SUM(`estimated_calories`) AS estimated_calories,
       SUM(COALESCE(`actual_calories`, 0)) AS actual_calories
FROM `workouts`
GROUP BY `status`;
