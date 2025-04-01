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
