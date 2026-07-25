package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_test_bundle")
public class TestBundle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long problemVersionId;
    private String objectKey;
    private String sha256;
    private Long sizeBytes;
    private String manifestJson;
    @TableField("created_at")
    private LocalDateTime createdAt;
}

