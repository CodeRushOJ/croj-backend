CREATE TABLE `t_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT, `username` VARCHAR(50) NOT NULL, `password` VARCHAR(100) NOT NULL,
  `email` VARCHAR(100) NOT NULL, `avatar` VARCHAR(255), `role` TINYINT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 0, `bio` VARCHAR(255), `github` VARCHAR(100), `school` VARCHAR(100),
  `email_verified` TINYINT NOT NULL DEFAULT 0, `last_login_time` DATETIME(3), `last_login_ip` VARCHAR(50),
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_deleted` TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (`id`), UNIQUE KEY `uk_user_username` (`username`), UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `t_refresh_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT, `user_id` BIGINT NOT NULL, `token_hash` CHAR(64) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL, `revoked_at` DATETIME(3), `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_refresh_token` (`token_hash`), KEY `idx_refresh_user_expiry` (`user_id`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_problem` (
  `id` BIGINT NOT NULL AUTO_INCREMENT, `problem_no` VARCHAR(20) NOT NULL, `title` VARCHAR(255) NOT NULL,
  `description` LONGTEXT NOT NULL, `input_description` LONGTEXT NOT NULL, `output_description` LONGTEXT NOT NULL,
  `hints` JSON, `samples` JSON, `time_limit` INT NOT NULL DEFAULT 1000, `memory_limit` INT NOT NULL DEFAULT 256,
  `difficulty` TINYINT NOT NULL DEFAULT 2, `is_special_judge` TINYINT NOT NULL DEFAULT 0, `special_judge_code` LONGTEXT,
  `special_judge_language` VARCHAR(50), `judge_mode` TINYINT NOT NULL DEFAULT 0, `total_score` INT NOT NULL DEFAULT 100,
  `source` VARCHAR(255), `create_user_id` BIGINT NOT NULL, `submit_count` INT NOT NULL DEFAULT 0, `accepted_count` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1, `published_version_id` BIGINT, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_problem_no` (`problem_no`), KEY `idx_problem_listing` (`status`,`difficulty`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_problem_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT, `problem_id` BIGINT NOT NULL, `version_no` INT NOT NULL, `state` VARCHAR(20) NOT NULL,
  `statement_json` JSON NOT NULL, `limits_json` JSON NOT NULL, `judge_config_json` JSON NOT NULL,
  `created_by` BIGINT NOT NULL, `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), `published_at` DATETIME(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_problem_version` (`problem_id`,`version_no`), KEY `idx_problem_version_state` (`problem_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_test_bundle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT, `problem_version_id` BIGINT NOT NULL, `object_key` VARCHAR(512) NOT NULL,
  `sha256` CHAR(64) NOT NULL, `size_bytes` BIGINT NOT NULL, `manifest_json` JSON NOT NULL, `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_test_bundle_version` (`problem_version_id`), UNIQUE KEY `uk_test_bundle_object` (`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_problem_tag` (`id` BIGINT NOT NULL AUTO_INCREMENT, `name` VARCHAR(50) NOT NULL, `color` VARCHAR(20) NOT NULL DEFAULT '#409EFF', `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), `is_deleted` TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (`id`), UNIQUE KEY `uk_tag_name` (`name`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `t_problem_tag_relation` (`id` BIGINT NOT NULL AUTO_INCREMENT, `problem_id` BIGINT NOT NULL, `tag_id` BIGINT NOT NULL, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`), UNIQUE KEY `uk_problem_tag` (`problem_id`,`tag_id`), KEY `idx_relation_tag` (`tag_id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_submission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT, `problem_id` BIGINT NOT NULL, `problem_version_id` BIGINT, `user_id` BIGINT NOT NULL,
  `language` VARCHAR(20) NOT NULL, `code` MEDIUMTEXT NOT NULL, `status` TINYINT NOT NULL DEFAULT 0, `run_time` INT, `memory` INT,
  `judge_info` JSON, `score` INT, `error_message` TEXT, `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_submission_user_time` (`user_id`,`create_time`), KEY `idx_submission_problem_status` (`problem_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_judge_attempt` (`id` BIGINT NOT NULL AUTO_INCREMENT, `submission_id` BIGINT NOT NULL, `attempt_no` INT NOT NULL, `job_name` VARCHAR(253), `runner_image` VARCHAR(512), `status` VARCHAR(32) NOT NULL, `result_json` JSON, `started_at` DATETIME(3), `finished_at` DATETIME(3), `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`), UNIQUE KEY `uk_judge_attempt` (`submission_id`,`attempt_no`), UNIQUE KEY `uk_judge_job` (`job_name`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `t_outbox_event` (`id` CHAR(36) NOT NULL, `aggregate_type` VARCHAR(64) NOT NULL, `aggregate_id` BIGINT NOT NULL, `event_type` VARCHAR(128) NOT NULL, `payload` JSON NOT NULL, `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), `published_at` DATETIME(3), `attempts` INT NOT NULL DEFAULT 0, `next_attempt_at` DATETIME(3), `last_error` VARCHAR(1000), PRIMARY KEY (`id`), KEY `idx_outbox_pending` (`published_at`,`next_attempt_at`,`created_at`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_contest` (`id` BIGINT NOT NULL AUTO_INCREMENT, `title` VARCHAR(255) NOT NULL, `rule_type` VARCHAR(16) NOT NULL, `visibility` VARCHAR(16) NOT NULL, `starts_at` DATETIME(3) NOT NULL, `ends_at` DATETIME(3) NOT NULL, `freeze_at` DATETIME(3), `created_by` BIGINT NOT NULL, `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`), KEY `idx_contest_time` (`starts_at`,`ends_at`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `t_contest_problem` (`contest_id` BIGINT NOT NULL, `problem_id` BIGINT NOT NULL, `problem_version_id` BIGINT NOT NULL, `label` VARCHAR(16) NOT NULL, `score` INT NOT NULL DEFAULT 100, PRIMARY KEY (`contest_id`,`problem_id`), UNIQUE KEY `uk_contest_label` (`contest_id`,`label`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_forum_category` (`id` BIGINT NOT NULL AUTO_INCREMENT, `name` VARCHAR(80) NOT NULL, `slug` VARCHAR(80) NOT NULL, `sort_order` INT NOT NULL DEFAULT 0, PRIMARY KEY (`id`), UNIQUE KEY `uk_forum_category_slug` (`slug`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `t_forum_post` (`id` BIGINT NOT NULL AUTO_INCREMENT, `category_id` BIGINT NOT NULL, `author_id` BIGINT NOT NULL, `title` VARCHAR(255) NOT NULL, `content_markdown` LONGTEXT NOT NULL, `content_html` LONGTEXT NOT NULL, `status` VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED', `pinned` TINYINT NOT NULL DEFAULT 0, `locked` TINYINT NOT NULL DEFAULT 0, `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`), KEY `idx_forum_feed` (`category_id`,`pinned`,`created_at`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `t_forum_comment` (`id` BIGINT NOT NULL AUTO_INCREMENT, `post_id` BIGINT NOT NULL, `parent_id` BIGINT, `author_id` BIGINT NOT NULL, `content_markdown` TEXT NOT NULL, `content_html` TEXT NOT NULL, `status` VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED', `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`), KEY `idx_comment_post` (`post_id`,`created_at`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_solution` (`id` BIGINT NOT NULL AUTO_INCREMENT, `problem_id` BIGINT NOT NULL, `problem_version_id` BIGINT NOT NULL, `author_id` BIGINT NOT NULL, `title` VARCHAR(255) NOT NULL, `content_markdown` LONGTEXT NOT NULL, `content_html` LONGTEXT NOT NULL, `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT', `featured` TINYINT NOT NULL DEFAULT 0, `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), `published_at` DATETIME(3), PRIMARY KEY (`id`), KEY `idx_solution_problem` (`problem_id`,`status`,`published_at`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `t_content_report` (`id` BIGINT NOT NULL AUTO_INCREMENT, `reporter_id` BIGINT NOT NULL, `content_type` VARCHAR(32) NOT NULL, `content_id` BIGINT NOT NULL, `reason` VARCHAR(64) NOT NULL, `details` VARCHAR(1000), `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN', `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`), KEY `idx_report_queue` (`status`,`created_at`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `t_notification` (`id` BIGINT NOT NULL AUTO_INCREMENT, `user_id` BIGINT NOT NULL, `type` VARCHAR(64) NOT NULL, `payload` JSON NOT NULL, `read_at` DATETIME(3), `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`), KEY `idx_notification_user` (`user_id`,`read_at`,`created_at`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `t_audit_log` (`id` BIGINT NOT NULL AUTO_INCREMENT, `actor_id` BIGINT, `action` VARCHAR(128) NOT NULL, `resource_type` VARCHAR(64) NOT NULL, `resource_id` VARCHAR(128), `request_id` VARCHAR(64), `metadata` JSON, `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (`id`), KEY `idx_audit_resource` (`resource_type`,`resource_id`,`created_at`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
