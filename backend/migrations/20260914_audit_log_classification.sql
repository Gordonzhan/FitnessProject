-- R2：审计日志分级、目标标识和保留期限支持（MySQL 5.7+，可重复执行）。
-- 依赖：create_operation_log_table.sql、20260908_error_monitoring.sql。
USE `fitness_diary`;

SET @event_type_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='operation_logs' AND COLUMN_NAME='event_type'
);
SET @event_type_sql = IF(@event_type_exists=0,
  'ALTER TABLE operation_logs ADD COLUMN event_type VARCHAR(50) NULL COMMENT ''审计事件类型，旧日志为空'' AFTER request_id',
  'SELECT 1');
PREPARE event_type_stmt FROM @event_type_sql;
EXECUTE event_type_stmt;
DEALLOCATE PREPARE event_type_stmt;

SET @target_ref_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='operation_logs' AND COLUMN_NAME='target_ref'
);
SET @target_ref_sql = IF(@target_ref_exists=0,
  'ALTER TABLE operation_logs ADD COLUMN target_ref VARCHAR(100) NULL COMMENT ''目标业务编号或不可逆摘要'' AFTER event_type',
  'SELECT 1');
PREPARE target_ref_stmt FROM @target_ref_sql;
EXECUTE target_ref_stmt;
DEALLOCATE PREPARE target_ref_stmt;

SET @result_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='operation_logs' AND COLUMN_NAME='result'
);
SET @result_sql = IF(@result_exists=0,
  'ALTER TABLE operation_logs ADD COLUMN result VARCHAR(10) NULL COMMENT ''SUCCESS或FAILURE'' AFTER target_ref',
  'SELECT 1');
PREPARE result_stmt FROM @result_sql;
EXECUTE result_stmt;
DEALLOCATE PREPARE result_stmt;

SET @event_time_index_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='operation_logs' AND INDEX_NAME='idx_operation_logs_event_time'
);
SET @event_time_index_sql = IF(@event_time_index_exists=0,
  'ALTER TABLE operation_logs ADD INDEX idx_operation_logs_event_time (event_type, operation_time)',
  'SELECT 1');
PREPARE event_time_index_stmt FROM @event_time_index_sql;
EXECUTE event_time_index_stmt;
DEALLOCATE PREPARE event_time_index_stmt;

SET @result_time_index_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='operation_logs' AND INDEX_NAME='idx_operation_logs_result_time'
);
SET @result_time_index_sql = IF(@result_time_index_exists=0,
  'ALTER TABLE operation_logs ADD INDEX idx_operation_logs_result_time (result, operation_time)',
  'SELECT 1');
PREPARE result_time_index_stmt FROM @result_time_index_sql;
EXECUTE result_time_index_stmt;
DEALLOCATE PREPARE result_time_index_stmt;

SHOW COLUMNS FROM `operation_logs` WHERE Field IN ('event_type', 'target_ref', 'result');
SHOW INDEX FROM `operation_logs` WHERE Key_name IN
  ('idx_operation_logs_event_time', 'idx_operation_logs_result_time');
