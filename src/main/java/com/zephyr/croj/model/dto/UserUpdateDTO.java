package com.zephyr.croj.model.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;

/**
 * 用户信息更新数据传输对象
 *
 
 */
@Data
public class UserUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 个人简介
     */
    @Size(max = 255, message = "个人简介不能超过255个字符")
    private String bio;

    /**
     * GitHub账号
     */
    @Size(max = 100, message = "GitHub账号不能超过100个字符")
    private String github;

    /**
     * 学校
     */
    @Size(max = 100, message = "学校名称不能超过100个字符")
    private String school;
}