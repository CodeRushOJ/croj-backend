package com.zephyr.croj.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户视图对象
 *
 
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 角色：0-普通用户，1-管理员，2-超级管理员
     */
    private Integer role;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 状态：0-正常，1-禁用
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * GitHub账号
     */
    private String github;

    /**
     * 学校
     */
    private String school;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;
}