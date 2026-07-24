ALTER TABLE `t_forum_post`
  ADD INDEX `idx_forum_post_public_feed` (`status`, `pinned`, `created_at`);

ALTER TABLE `t_forum_comment`
  ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_at`,
  ADD INDEX `idx_forum_comment_public_feed` (`post_id`, `status`, `created_at`);

ALTER TABLE `t_solution`
  ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3) AFTER `published_at`;

INSERT INTO `t_problem_version` (
  `problem_id`, `version_no`, `state`, `statement_json`, `limits_json`, `judge_config_json`,
  `created_by`, `created_at`, `published_at`
)
SELECT
  p.`id`, 1, IF(p.`status` = 0, 'PUBLISHED', 'DRAFT'),
  JSON_OBJECT(
    'title', p.`title`, 'description', p.`description`,
    'inputDescription', p.`input_description`, 'outputDescription', p.`output_description`,
    'hints', p.`hints`, 'samples', p.`samples`
  ),
  JSON_OBJECT('timeLimit', p.`time_limit`, 'memoryLimit', p.`memory_limit`, 'totalScore', p.`total_score`),
  JSON_OBJECT(
    'specialJudge', p.`is_special_judge`, 'specialJudgeCode', p.`special_judge_code`,
    'specialJudgeLanguage', p.`special_judge_language`, 'judgeMode', p.`judge_mode`
  ),
  p.`create_user_id`, p.`create_time`, IF(p.`status` = 0, p.`create_time`, NULL)
FROM `t_problem` p
WHERE p.`is_deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_problem_version` v
    WHERE v.`problem_id` = p.`id` AND v.`version_no` = 1
  );

UPDATE `t_problem` p
JOIN `t_problem_version` v ON v.`problem_id` = p.`id` AND v.`version_no` = 1
SET p.`published_version_id` = v.`id`
WHERE p.`status` = 0 AND p.`published_version_id` IS NULL;
