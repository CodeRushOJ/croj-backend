ALTER TABLE `t_problem`
  ADD COLUMN `checker` VARCHAR(16) NOT NULL DEFAULT 'exact' AFTER `judge_mode`;

UPDATE `t_problem`
SET `checker` = 'special'
WHERE `is_special_judge` = 1;

ALTER TABLE `t_problem`
  ADD CONSTRAINT `chk_problem_checker`
    CHECK (`checker` IN ('exact', 'token', 'special')),
  ADD CONSTRAINT `chk_problem_special_checker`
    CHECK (
      (`is_special_judge` = 1 AND `checker` = 'special')
      OR
      (`is_special_judge` = 0 AND `checker` IN ('exact', 'token'))
    );
