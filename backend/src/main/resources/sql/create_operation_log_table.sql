-- 操作日志表，可在已完成主库初始化的 fitness_diary 数据库中单独执行
USE `fitness_diary`;

CREATE TABLE IF NOT EXISTS `operation_logs` (
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
  KEY `idx_operation_logs_event_time` (`event_type`, `operation_time`),
  KEY `idx_operation_logs_result_time` (`result`, `operation_time`),
  CONSTRAINT `fk_operation_logs_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='服务端接口操作日志';
