package com.zephyr.croj.common.enums;

import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
public enum ResultCodeEnum {

    /**
     * 成功
     */
    SUCCESS(20000, "操作成功"),

    /**
     * 失败
     */
    ERROR(50000, "操作失败"),

    /**
     * 未登录或token已过期
     */
    UNAUTHORIZED(40100, "未登录或token已过期"),

    /**
     * 无权限
     */
    FORBIDDEN(40300, "无权限操作"),

    /**
     * 资源不存在
     */
    NOT_FOUND(40400, "资源不存在"),

    /**
     * 参数错误
     */
    PARAM_ERROR(40000, "参数错误"),

    /**
     * 用户名或密码错误
     */
    ACCOUNT_ERROR(40001, "用户名或密码错误"),

    /**
     * 用户已存在
     */
    ACCOUNT_EXIST(40002, "用户已存在"),

    /**
     * 验证码错误
     */
    CAPTCHA_ERROR(40003, "验证码错误或已过期"),

    /**
     * 账号已禁用
     */
    ACCOUNT_DISABLED(40004, "账号已被禁用"),

    /**
     * 密码错误
     */
    PASSWORD_ERROR(40005, "密码错误"),

    /**
     * 两次密码不一致
     */
    PASSWORD_NOT_MATCH(40006, "两次密码不一致"),

    /**
     * 用户不存在
     */
    USER_NOT_EXIST(40007, "用户不存在"),

    /**
     * 邮箱验证码错误
     */
    EMAIL_CODE_ERROR(40008, "邮箱验证码错误或已过期"),

    /**
     * 邮箱已存在
     */
    EMAIL_EXIST(40009, "邮箱已存在"),

    /**
     * 禁用错误
     */
    DISABLED_ERROR(40010, "无法禁用账户"),

    /**
     * 邮箱已验证
     */
    EMAIL_VERIFIED(40011, "邮箱已验证"),

    /**
     * 更新失败
     */
    UPDATE_ERROR(40012, "更新失败"),

    /**
     * 上传失败
     */
    UPLOAD_ERROR(40013, "上传失败"),

    /**
     * 题目不存在
     */
    PROBLEM_NOT_EXIST(40014, "题目不存在"),

    /**
     * 删除失败
     */
    DELETE_ERROR(40015, "删除失败"),

    /**
     * 创建失败
     */
    CREATE_ERROR(40016, "创建失败"),

    /**
     * 系统错误
     */
    SYSTEM_ERROR(40017, "系统错误");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 消息
     */
    private final String message;

    ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}