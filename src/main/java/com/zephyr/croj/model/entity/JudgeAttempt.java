package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_judge_attempt")
public class JudgeAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private Integer attemptNo;
    private String jobName;
    private String runnerImage;
    private String status;
    private String resultJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
