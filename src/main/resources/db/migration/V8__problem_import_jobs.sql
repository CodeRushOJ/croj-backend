CREATE TABLE `t_problem_import_job` (
  `id` CHAR(36) NOT NULL,
  `actor_id` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `detected_format` VARCHAR(32) NOT NULL,
  `file_sha256` CHAR(64) NOT NULL,
  `staging_object_key` VARCHAR(512) NOT NULL,
  `summary_json` JSON NOT NULL,
  `imported_count` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `expires_at` DATETIME(3) NOT NULL,
  `committed_at` DATETIME(3),
  PRIMARY KEY (`id`),
  KEY `idx_problem_import_actor` (`actor_id`, `status`, `created_at`),
  KEY `idx_problem_import_expiry` (`status`, `expires_at`),
  CONSTRAINT `chk_problem_import_status`
    CHECK (`status` IN ('VALIDATED', 'COMMITTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
