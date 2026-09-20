-- 第九项：为操作日志增加请求追踪号和错误监控索引（MySQL 5.7）。
-- 仅在已完成前八项迁移的数据库执行一次。
USE `fitness_diary`;

ALTER TABLE `operation_logs`
  ADD COLUMN `request_id` VARCHAR(64) NULL AFTER `user_id`,
  ADD INDEX `idx_operation_logs_request_id` (`request_id`),
  ADD INDEX `idx_operation_logs_status_time` (`status_code`, `operation_time`);

SHOW COLUMNS FROM `operation_logs` LIKE 'request_id';
SHOW INDEX FROM `operation_logs` WHERE Key_name IN
  ('idx_operation_logs_request_id', 'idx_operation_logs_status_time');
