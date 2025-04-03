package com.zephyr.croj.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * 创建问题的数据传输对象
 */
@Data
public class ProblemCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目标题
     */
    @NotBlank(message = "题目标题不能为空")
    private String title;

    /**
     * 题目描述
     */
    @NotBlank(message = "题目描述不能为空")
    private String description;

    /**
     * 输入描述
     */
    @NotBlank(message = "输入描述不能为空")
    private String inputDescription;

    /**
     * 输出描述
     */
    @NotBlank(message = "输出描述不能为空")
    private String outputDescription;

    /**
     * 提示与说明
     */
    private List<String> hints;

    /**
     * 测试样例，包含输入、输出和解释
     * 示例: [{"input": "1 2", "output": "3", "explanation": ""}, {"input": "10 20", "output": "30"}]
     */
    @NotEmpty(message = "测试样例不能为空")
    private List<Map<String, String>> samples;

    /**
     * 时间限制（ms）
     */
    @NotNull(message = "时间限制不能为空")
    @Min(value = 1, message = "时间限制不能小于1ms")
    @Max(value = 10000, message = "时间限制不能大于10000ms")
    private Integer timeLimit;

    /**
     * 内存限制（MB）
     */
    @NotNull(message = "内存限制不能为空")
    @Min(value = 1, message = "内存限制不能小于1MB")
    @Max(value = 1024, message = "内存限制不能大于1024MB")
    private Integer memoryLimit;

    /**
     * 题目难度（1-简单，2-中等，3-困难）
     */
    @NotNull(message = "题目难度不能为空")
    @Min(value = 1, message = "题目难度不能小于1")
    @Max(value = 3, message = "题目难度不能大于3")
    private Integer difficulty;

    /**
     * 是否为特判题目（Special Judge）
     */
    private Boolean isSpecialJudge = false;

    /**
     * 特判代码（当isSpecialJudge为true时有效）
     */
    private String specialJudgeCode;

    /**
     * 特判代码语言
     */
    private String specialJudgeLanguage;

    /**
     * 评判模式（0-ACM模式，1-OI模式）
     */
    @NotNull(message = "评判模式不能为空")
    @Min(value = 0, message = "评判模式不能小于0")
    @Max(value = 1, message = "评判模式不能大于1")
    private Integer judgeMode;

    /**
     * OI模式下的题目总分
     */
    private Integer totalScore;

    /**
     * 题目来源
     */
    private String source;

    /**
     * 题目状态（0-公开，1-私有，2-比赛中）
     */
    @NotNull(message = "题目状态不能为空")
    @Min(value = 0, message = "题目状态不能小于0")
    @Max(value = 2, message = "题目状态不能大于2")
    private Integer status;

    /**
     * 标签ID列表
     */
    private List<Long> tagIds;
}