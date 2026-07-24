CREATE TABLE `t_judge_result_receipt` (
  `result_id` VARCHAR(128) NOT NULL,
  `submission_id` BIGINT NOT NULL,
  `attempt_no` INT NOT NULL,
  `payload_sha256` CHAR(64) NOT NULL,
  `final_status` VARCHAR(32) NOT NULL,
  `received_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`result_id`),
  KEY `idx_judge_result_submission` (`submission_id`, `attempt_no`, `received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `t_judge_attempt` (`submission_id`, `attempt_no`, `status`)
SELECT `submission`.`id`, 1, 'QUEUED'
FROM `t_submission` AS `submission`
LEFT JOIN `t_judge_attempt` AS `attempt`
  ON `attempt`.`submission_id` = `submission`.`id`
 AND `attempt`.`attempt_no` = 1
WHERE `submission`.`status` = 0
  AND `submission`.`is_deleted` = 0
  AND `attempt`.`id` IS NULL;
