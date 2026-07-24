ALTER TABLE `t_forum_post`
  ADD COLUMN `resource_type` VARCHAR(16) NOT NULL DEFAULT 'GENERAL' AFTER `author_id`,
  ADD COLUMN `resource_id` BIGINT NULL AFTER `resource_type`,
  ADD CONSTRAINT `chk_forum_resource_ref` CHECK (
    (`resource_type` = 'GENERAL' AND `resource_id` IS NULL)
    OR (`resource_type` IN ('PROBLEM', 'CONTEST') AND `resource_id` IS NOT NULL)
  ),
  ADD INDEX `idx_forum_resource_feed`
    (`resource_type`, `resource_id`, `status`, `pinned`, `created_at`);
