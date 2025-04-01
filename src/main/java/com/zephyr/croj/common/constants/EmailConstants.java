package com.zephyr.croj.common.constants;

/**
 * 邮箱相关常量
 */
public class EmailConstants {

    /**
     * 邮箱验证码key前缀（用于链接验证）
     */
    public static final String EMAIL_VERIFY_CODE_KEY = "emailVerifyCode:";

    /**
     * 邮箱验证码key前缀（用于注册）
     */
    public static final String EMAIL_CODE_KEY = "emailCode:";

    /**
     * 邮箱验证码有效期（分钟）（用于注册）
     */
    public static final Integer EMAIL_CODE_EXPIRATION = 5;

    /**
     * 邮箱验证链接有效期（分钟）
     */
    public static final Integer EMAIL_VERIFY_EXPIRATION = 30;

    /**
     * 重置密码验证码key前缀
     */
    public static final String PASSWORD_RESET_CODE_KEY = "passwordResetCode:";

    /**
     * 重置密码验证码有效期（分钟）
     */
    public static final Integer PASSWORD_RESET_EXPIRATION = 15;
}