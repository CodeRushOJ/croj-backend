package com.zephyr.croj.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProblemDifficultyEnum {
    DRAFT(0, "简单"),
    PUBLISHED(1, "中等"),
    HIDDEN(2, "困难");

    /**
     * 题目难度码
     */
    private final Integer code;

    /**
     * 题目难度码描述
     */
    private final String desc;

    /**
     * 根据code获取枚举
     *
     * @param code 题目难度码
     * @return 题目难度码描述
     */
    public static ProblemDifficultyEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProblemDifficultyEnum difficultyEnum : ProblemDifficultyEnum.values()) {
            if (difficultyEnum.getCode().equals(code)) {
                return difficultyEnum;
            }
        }
        return null;
    }
}
