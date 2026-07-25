ALTER TABLE `t_contest`
  ADD COLUMN `description_markdown` LONGTEXT NULL AFTER `title`,
  ADD COLUMN `lifecycle` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER `visibility`,
  ADD COLUMN `registration_opens_at` DATETIME(3) NULL AFTER `lifecycle`,
  ADD COLUMN `registration_closes_at` DATETIME(3) NULL AFTER `registration_opens_at`,
  ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_at`,
  ADD INDEX `idx_contest_public_feed` (`visibility`, `lifecycle`, `starts_at`, `id`);

UPDATE `t_contest`
SET `registration_opens_at` = `created_at`,
    `registration_closes_at` = `starts_at`
WHERE `registration_opens_at` IS NULL OR `registration_closes_at` IS NULL;

ALTER TABLE `t_contest`
  MODIFY COLUMN `registration_opens_at` DATETIME(3) NOT NULL,
  MODIFY COLUMN `registration_closes_at` DATETIME(3) NOT NULL;

ALTER TABLE `t_submission`
  ADD COLUMN `contest_id` BIGINT NULL AFTER `problem_version_id`,
  ADD INDEX `idx_submission_contest_scoreboard`
    (`contest_id`, `user_id`, `problem_id`, `create_time`, `status`);

CREATE TABLE `t_contest_registration` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `contest_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
  `registered_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `managed_by` BIGINT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_registration` (`contest_id`, `user_id`),
  KEY `idx_contest_registration_roster` (`contest_id`, `status`, `registered_at`),
  KEY `idx_contest_registration_user` (`user_id`, `status`, `contest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `t_contest_announcement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `contest_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `content_markdown` LONGTEXT NOT NULL,
  `published_by` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_contest_announcement_feed` (`contest_id`, `published_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `t_contest_clarification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `contest_id` BIGINT NOT NULL,
  `problem_id` BIGINT NULL,
  `asked_by` BIGINT NOT NULL,
  `question_markdown` TEXT NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_contest_clarification_feed` (`contest_id`, `created_at`, `id`),
  KEY `idx_contest_clarification_author` (`contest_id`, `asked_by`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `t_contest_clarification_reply` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `clarification_id` BIGINT NOT NULL,
  `reply_markdown` TEXT NOT NULL,
  `replied_by` BIGINT NOT NULL,
  `is_public` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_contest_clarification_reply` (`clarification_id`, `created_at`, `id`),
  KEY `idx_contest_public_reply` (`clarification_id`, `is_public`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `t_contest_scoreboard_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `contest_id` BIGINT NOT NULL,
  `view_type` VARCHAR(16) NOT NULL,
  `cutoff_at` DATETIME(3) NOT NULL,
  `source_version` VARCHAR(160) NOT NULL,
  `payload` JSON NOT NULL,
  `generated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_scoreboard_snapshot` (`contest_id`, `view_type`, `cutoff_at`),
  KEY `idx_contest_scoreboard_cache` (`contest_id`, `view_type`, `generated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
