CREATE TABLE `t_announcement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scope` VARCHAR(16) NOT NULL DEFAULT 'GLOBAL',
  `contest_id` BIGINT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content_markdown` LONGTEXT NOT NULL,
  `lifecycle` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  `is_pinned` TINYINT NOT NULL DEFAULT 0,
  `pin_order` INT NOT NULL DEFAULT 0,
  `publish_at` DATETIME(3) NULL,
  `expires_at` DATETIME(3) NULL,
  `created_by` BIGINT NOT NULL,
  `updated_by` BIGINT NOT NULL,
  `published_by` BIGINT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  `archived_at` DATETIME(3) NULL,
  `version` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_announcement_public_feed`
    (`scope`, `lifecycle`, `publish_at`, `expires_at`, `is_pinned`, `pin_order`, `id`),
  KEY `idx_announcement_admin_feed` (`scope`, `updated_at`, `id`),
  KEY `idx_announcement_contest` (`contest_id`, `lifecycle`, `publish_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
