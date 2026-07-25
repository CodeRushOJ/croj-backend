package com.zephyr.croj.model.vo;

public record AdminProblemVersionSourceVO(
        long problemId,
        long versionId,
        int versionNo,
        String state,
        boolean specialJudge,
        String checkerSource,
        String checkerLanguage,
        int judgeMode) {}
