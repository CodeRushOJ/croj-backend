-- 创建数据库
CREATE DATABASE IF NOT EXISTS `code_rush_oj` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `code_rush_oj`;

-- 用户表
CREATE TABLE IF NOT EXISTS `t_user` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `password` varchar(100) NOT NULL COMMENT '密码（加密存储）',
    `email` varchar(100) NOT NULL COMMENT '邮箱',
    `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
    `role` tinyint(4) NOT NULL DEFAULT '0' COMMENT '角色：0-普通用户，1-管理员，2-超级管理员',
    `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态：0-正常，1-禁用',
    `bio` varchar(255) DEFAULT NULL COMMENT '个人简介',
    `github` varchar(100) DEFAULT NULL COMMENT 'GitHub账号',
    `school` varchar(100) DEFAULT NULL COMMENT '学校',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
    `email_verified` tinyint(1) NOT NULL DEFAULT '0' COMMENT '邮箱是否验证：0-未验证，1-已验证',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_username` (`username`),
    UNIQUE KEY `idx_email` (`email`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- 问题表
CREATE TABLE IF NOT EXISTS `t_problem` (
                                           `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '题目ID',
                                           `problem_no` varchar(20) NOT NULL COMMENT '题目编号（外部展示）',
                                           `title` varchar(255) NOT NULL COMMENT '题目标题',
                                           `description` text NOT NULL COMMENT '题目描述（HTML格式）',
                                           `input_description` text NOT NULL COMMENT '输入描述',
                                           `output_description` text NOT NULL COMMENT '输出描述',
                                           `hints` json DEFAULT NULL COMMENT '提示与说明',
                                           `samples` json DEFAULT NULL COMMENT '测试样例（JSON格式）',
                                           `time_limit` int(11) NOT NULL DEFAULT 1000 COMMENT '时间限制（ms）',
                                           `memory_limit` int(11) NOT NULL DEFAULT 256 COMMENT '内存限制（MB）',
                                           `difficulty` tinyint(4) NOT NULL DEFAULT 2 COMMENT '题目难度（1-简单，2-中等，3-困难）',
                                           `is_special_judge` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为特判题目（0-否，1-是）',
                                           `special_judge_code` text DEFAULT NULL COMMENT '特判代码',
                                           `special_judge_language` varchar(50) DEFAULT NULL COMMENT '特判代码语言',
                                           `judge_mode` tinyint(4) NOT NULL DEFAULT 0 COMMENT '评判模式（0-ACM模式，1-OI模式）',
                                           `total_score` int(11) DEFAULT 100 COMMENT 'OI模式下的题目总分',
                                           `source` varchar(255) DEFAULT NULL COMMENT '题目来源',
                                           `create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
                                           `submit_count` int(11) NOT NULL DEFAULT 0 COMMENT '提交次数',
                                           `accepted_count` int(11) NOT NULL DEFAULT 0 COMMENT '通过次数',
                                           `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '题目状态（0-公开，1-私有，2-比赛中）',
                                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除（0-未删除，1-已删除）',
                                           PRIMARY KEY (`id`),
                                           UNIQUE KEY `idx_problem_no` (`problem_no`),
                                           KEY `idx_create_user_id` (`create_user_id`),
                                           KEY `idx_status` (`status`),
                                           KEY `idx_difficulty` (`difficulty`),
                                           KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问题表';

-- 问题标签表
CREATE TABLE IF NOT EXISTS `t_problem_tag` (
                                               `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
                                               `name` varchar(50) NOT NULL COMMENT '标签名称',
                                               `color` varchar(20) DEFAULT '#409EFF' COMMENT '标签颜色（十六进制颜色码）',
                                               `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                               `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                               `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除（0-未删除，1-已删除）',
                                               PRIMARY KEY (`id`),
                                               UNIQUE KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问题标签表';

-- 问题-标签关联表
CREATE TABLE IF NOT EXISTS `t_problem_tag_relation` (
                                                        `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
                                                        `problem_id` bigint(20) NOT NULL COMMENT '题目ID',
                                                        `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
                                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                        PRIMARY KEY (`id`),
                                                        UNIQUE KEY `idx_problem_tag` (`problem_id`, `tag_id`),
                                                        KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问题-标签关联表';

-- 初始化一些标签数据
INSERT INTO `t_problem_tag` (`name`, `color`)
VALUES
    ('动态规划', '#67C23A'),
    ('贪心算法', '#E6A23C'),
    ('数组', '#409EFF'),
    ('链表', '#F56C6C'),
    ('二叉树', '#909399'),
    ('哈希表', '#9B59B6'),
    ('深度优先搜索', '#1ABC9C'),
    ('广度优先搜索', '#3498DB'),
    ('排序', '#F39C12'),
    ('二分查找', '#27AE60');