package com.zephyr.croj.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 问题列表项视图对象（精简版，用于列表展示）
 */
@Data
public class ProblemListItemVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目ID
     */
    private Long id;

    /**
     * 题目编号
     */
    private String problemNo;

    /**
     * 题目标题
     */
    private String title;

    /**
     * 题目难度（1-简单，2-中等，3-困难）
     */
    private Integer difficulty;

    /**
     * 题目标签列表
     */
    private List<ProblemTagVO> tags;

    /**
     * 提交次数
     */
    private Integer submitCount;

    /**
     * 通过次数
     */
    private Integer acceptedCount;

    /**
     * 通过率
     */
    private Double acceptRate;

    /**
     * 用户提交状态（0-未提交，1-已通过，2-未通过）
     */
    private Integer userStatus;
}