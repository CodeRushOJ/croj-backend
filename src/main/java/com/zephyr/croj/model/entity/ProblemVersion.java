package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_problem_version")
public class ProblemVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long problemId;
    private Integer versionNo;
    private String state;
    private String statementJson;
    private String limitsJson;
    private String judgeConfigJson;
    private Long createdBy;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("published_at")
    private LocalDateTime publishedAt;
    private Boolean projectionComplete;
}
