package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_judge_result_receipt")
public class JudgeResultReceipt {
    @TableId(value = "result_id", type = IdType.INPUT)
    private String resultId;
    private Long submissionId;
    private Integer attemptNo;
    private String payloadSha256;
    private String finalStatus;
    @TableField("received_at")
    private LocalDateTime receivedAt;
}
