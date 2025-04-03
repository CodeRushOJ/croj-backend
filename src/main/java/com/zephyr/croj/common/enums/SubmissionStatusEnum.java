package com.zephyr.croj.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 提交状态枚举
 */
@Getter
@AllArgsConstructor
public enum SubmissionStatusEnum {

    /**
     * 排队中
     */
    PENDING(0, "排队中"),

    /**
     * 已通过
     */
    ACCEPTED(1, "通过"),

    /**
     * 编译错误
     */
    COMPILE_ERROR(2, "编译错误"),

    /**
     * 答案错误
     */
    WRONG_ANSWER(3, "答案错误"),

    /**
     * 运行超时
     */
    TIME_LIMIT_EXCEEDED(4, "运行超时"),

    /**
     * 内存超限
     */
    MEMORY_LIMIT_EXCEEDED(5, "内存超限"),

    /**
     * 运行错误
     */
    RUNTIME_ERROR(6, "运行错误"),

    /**
     * 系统错误
     */
    SYSTEM_ERROR(7, "系统错误");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 状态描述
     */
    private final String desc;

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 状态枚举
     */
    public static SubmissionStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SubmissionStatusEnum statusEnum : SubmissionStatusEnum.values()) {
            if (statusEnum.getCode().equals(code)) {
                return statusEnum;
            }
        }
        return null;
    }
}