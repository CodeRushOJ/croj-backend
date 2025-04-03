package com.zephyr.croj.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提交记录视图对象
 */
@Data
public class SubmissionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提交ID
     */
    private Long id;

    /**
     * 题目ID
     */
    private Long problemId;

    /**
     * 题目编号
     */
    private String problemNo;

    /**
     * 题目标题
     */
    private String problemTitle;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 提交状态：0-排队中，1-已通过，2-编译错误，3-答案错误，4-运行超时，5-内存超限，6-运行错误，7-系统错误
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusText;

    /**
     * 运行时间（ms）
     */
    private Integer runTime;

    /**
     * 运行内存（KB）
     */
    private Integer memory;

    /**
     * 评测详情（JSON格式，包含每个测试点的详细信息）
     */
    private String judgeInfo;

    /**
     * 评测得分（OI模式下有效）
     */
    private Integer score;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 代码内容
     */
    private String code;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}