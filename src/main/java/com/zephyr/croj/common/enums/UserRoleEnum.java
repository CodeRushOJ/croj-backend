package com.zephyr.croj.common.enums;

import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
public enum UserRoleEnum {

    /**
     * 普通用户
     */
    USER(0, "普通用户"),

    /**
     * 管理员
     */
    ADMIN(1, "管理员"),

    /**
     * 超级管理员
     */
    SUPER_ADMIN(2, "超级管理员");

    /**
     * 角色码
     */
    private final Integer code;

    /**
     * 角色描述
     */
    private final String desc;

    UserRoleEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

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