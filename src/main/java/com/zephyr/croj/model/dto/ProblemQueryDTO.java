package com.zephyr.croj.model.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 问题查询参数数据传输对象
 */
@Data
public class ProblemQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词（题目标题或编号）
     */
    private String keyword;

    /**
     * 题目难度（1-简单，2-中等，3-困难）
     */
    private Integer difficulty;

    /**
     * 题目状态（0-公开，1-私有，2-比赛中）
     */
    private Integer status;

    /**
     * 标签ID列表
     */
    private List<Long> tagIds;

    /**
     * 当前页码
     */
    @Min(value = 1, message = "当前页码不能小于1")
    private long current = 1;

    /**
     * 每页数量
     */
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能大于100")
    private long size = 10;
}