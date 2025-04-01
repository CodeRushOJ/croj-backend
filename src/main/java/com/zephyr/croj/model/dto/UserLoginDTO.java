package com.zephyr.croj.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 用户登录数据传输对象
 *
 
 */
@Data
public class UserLoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户名或邮箱
     */
    @NotBlank(message = "用户名/邮箱不能为空")
    private String account;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String captcha;

    /**
     * 验证码key
     */
    @NotBlank(message = "验证码key不能为空")
    private String captchaKey;

    /**
     * 是否记住我
     */
    private Boolean rememberMe = false;
}