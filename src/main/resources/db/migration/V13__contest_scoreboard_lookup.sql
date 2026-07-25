ALTER TABLE `t_submission`
  ADD INDEX `idx_submission_contest_time`
    (`contest_id`, `is_deleted`, `create_time`, `id`);
