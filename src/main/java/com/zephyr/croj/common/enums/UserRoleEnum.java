package com.zephyr.croj.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
@AllArgsConstructor
public enum UserRoleEnum {

    /**
     * 普通用户
     */
    USER(0, "USER"),

    /**
     * 管理员
     */
    ADMIN(1, "ADMIN"),

    /**
     * 超级管理员
     */
    SUPER_ADMIN(2, "SUPER_ADMIN");

    /**
     * 角色码
     */
    private final Integer code;

    /**
     * 角色描述
     */
    private final String desc;

    /**
     * 根据code获取枚举
     *
     * @param code 角色码
     * @return 角色枚举
     */
    public static UserRoleEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserRoleEnum roleEnum : UserRoleEnum.values()) {
            if (roleEnum.getCode().equals(code)) {
                return roleEnum;
            }
        }
        return null;
    }
}