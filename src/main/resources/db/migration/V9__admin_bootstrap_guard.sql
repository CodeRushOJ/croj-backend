CREATE TABLE `t_system_bootstrap_lock` (
  `name` VARCHAR(64) NOT NULL,
  `administrator_id` BIGINT,
  `administrator_username` VARCHAR(50),
  `administrator_email` VARCHAR(100),
  `claimed_at` DATETIME(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `t_system_bootstrap_lock` (`name`) VALUES ('first-super-admin');
