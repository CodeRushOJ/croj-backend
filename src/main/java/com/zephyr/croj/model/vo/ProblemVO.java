package com.zephyr.croj.model.vo;

import com.zephyr.croj.model.entity.ProblemTag;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 问题视图对象
 */
@Data
public class ProblemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目ID
     */
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
     * 题目描述（HTML格式）
     */
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
    private String hints;

    /**
     * 测试样例，包含输入、输出和解释
     */
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
     * 题目标签列表
     */
    private List<ProblemTagVO> tags;

    /**
     * 用户提交状态（0-未提交，1-已通过，2-未通过）
     */
    private Integer userStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}