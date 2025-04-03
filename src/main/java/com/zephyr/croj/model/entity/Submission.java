package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提交记录实体类
 */
@Data
@TableName("t_submission")
public class Submission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提交ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 题目ID
     */
    private Long problemId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 代码内容
     */
    private String code;

    /**
     * 提交状态：0-排队中，1-已通过，2-编译错误，3-答案错误，4-运行超时，5-内存超限，6-运行错误，7-系统错误
     */
    private Integer status;

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