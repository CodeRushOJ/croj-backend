package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.INPUT)
    private String id;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String payload;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private String claimedBy;
    private LocalDateTime claimedAt;
}
