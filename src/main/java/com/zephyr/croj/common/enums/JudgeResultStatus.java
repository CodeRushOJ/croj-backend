package com.zephyr.croj.common.enums;

import com.zephyr.croj.common.exception.JudgeResultConflictException;
import java.util.Locale;

public enum JudgeResultStatus {
    ACCEPTED(SubmissionStatusEnum.ACCEPTED.getCode()),
    COMPILE_ERROR(SubmissionStatusEnum.COMPILE_ERROR.getCode()),
    WRONG_ANSWER(SubmissionStatusEnum.WRONG_ANSWER.getCode()),
    TIME_LIMIT_EXCEEDED(SubmissionStatusEnum.TIME_LIMIT_EXCEEDED.getCode()),
    MEMORY_LIMIT_EXCEEDED(SubmissionStatusEnum.MEMORY_LIMIT_EXCEEDED.getCode()),
    RUNTIME_ERROR(SubmissionStatusEnum.RUNTIME_ERROR.getCode()),
    SYSTEM_ERROR(SubmissionStatusEnum.SYSTEM_ERROR.getCode());

    private final int submissionCode;

    JudgeResultStatus(int submissionCode) {
        this.submissionCode = submissionCode;
    }

    public int submissionCode() {
        return submissionCode;
    }

    public static JudgeResultStatus parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new JudgeResultConflictException("unsupported terminal judge status");
        }
    }
}
