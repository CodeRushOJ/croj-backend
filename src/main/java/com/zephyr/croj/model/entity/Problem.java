package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 问题实体类
 */
@Data
@TableName(value = "t_problem", autoResultMap = true)
public class Problem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 题目编号（外部展示）
     */
    private String problemNo;

    /**
     * 题目标题
     */
    private String title;

    /**
     * 题目描述（HTML格式或者Markdown格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String description;

    /**
     * 输入描述
     */
    private String inputDescription;

    /**
     * 输出描述
     */
    private String outputDescription;

    /**
     * 提示与说明
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> hints;

    /**
     * 测试样例JSON，包含输入、输出和解释
     * 示例: [{"input": "1 2", "output": "3", "explanation": ""}, {"input": "10 20", "output": "30"}]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> samples;

    /**
     * 时间限制（ms）
     */
    private Integer timeLimit;

    /**
     * 内存限制（MB）
     */
    private Integer memoryLimit;

    /**
     * 题目难度（1-简单，2-中等，3-困难）
     */
    private Integer difficulty;

    /**
     * 是否为特判题目（Special Judge）
     */
    private Boolean isSpecialJudge;

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
     * 创建人ID
     */
    private Long createUserId;

    /**
     * 提交次数
     */
    private Integer submitCount;

    /**
     * 通过次数
     */
    private Integer acceptedCount;

    /**
     * 题目状态（0-公开，1-私有，2-比赛中）
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否删除（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer isDeleted;
}