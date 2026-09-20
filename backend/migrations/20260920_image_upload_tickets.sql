-- U1：持久化一次性 OSS 直传授权，保证云托管多实例之间的用户绑定和单次确认。
-- 不保存 STS 临时凭证、Policy、签名或最终图片 URL。
USE `fitness_diary`;

CREATE TABLE IF NOT EXISTS `image_upload_tickets` (
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
