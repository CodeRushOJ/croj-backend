package com.zephyr.croj.model.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.Serial;
import java.io.Serializable;

/**
 * 提交记录查询参数数据传输对象
 */
@Data
public class SubmissionQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目ID
     */
    private Long problemId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 提交状态：0-排队中，1-已通过，2-编译错误，3-答案错误，4-运行超时，5-内存超限，6-运行错误，7-系统错误
     */
    private Integer status;

    /**
     * 编程语言
     */
    private String language;

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