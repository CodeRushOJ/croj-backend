package com.zephyr.croj.service;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送邮箱验证码（用于注册）
     *
     * @param to 收件人邮箱
     * @param username 用户名
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String to, String username, String code);

    /**
     * 发送邮箱验证链接（用于已注册用户）
     *
     * @param to 收件人邮箱
     * @param username 用户名
     * @param userId 用户ID
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendVerificationLink(String to, String username, Long userId, String code);

    /**
     * 发送重置密码邮件
     *
     * @param to 收件人邮箱
     * @param username 用户名
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendResetPasswordEmail(String to, String username, String code);
}