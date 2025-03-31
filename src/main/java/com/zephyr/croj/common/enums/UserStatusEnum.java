package com.zephyr.croj.common.enums;

import lombok.Getter;

/**
 * 用户状态枚举
 */
@Getter
public enum UserStatusEnum {

    /**
     * 正常
     */
    NORMAL(0, "正常"),

    /**
     * 禁用
     */
    DISABLED(1, "禁用");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 状态描述
     */
    private final String desc;

    UserStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 状态枚举
     */
    public static UserStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatusEnum statusEnum : UserStatusEnum.values()) {
            if (statusEnum.getCode().equals(code)) {
                return statusEnum;
            }
        }
        return null;
    }
}