package com.zephyr.croj.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;

/**
 * 提交代码的数据传输对象
 */
@Data
public class SubmissionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目ID
     */
    @NotNull(message = "题目ID不能为空")
    private Long problemId;

    /**
     * 比赛ID；为空时按普通题库提交处理。
     */
    private Long contestId;

    /**
     * 编程语言
     */
    @NotBlank(message = "编程语言不能为空")
    private String language;

    /**
     * 代码内容
     */
    @NotBlank(message = "代码内容不能为空")
    @Size(max = 65535, message = "代码长度不能超过65535个字符")
    private String code;
}
