ALTER TABLE `t_outbox_event`
  ADD COLUMN `claimed_by` VARCHAR(64) NULL AFTER `last_error`,
  ADD COLUMN `claimed_at` DATETIME(3) NULL AFTER `claimed_by`,
  DROP INDEX `idx_outbox_pending`,
  ADD INDEX `idx_outbox_pending` (`published_at`, `next_attempt_at`, `claimed_at`, `created_at`),
  ADD INDEX `idx_outbox_claim` (`claimed_by`);
