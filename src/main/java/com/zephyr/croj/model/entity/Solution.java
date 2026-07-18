package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_solution")
public class Solution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long problemId;
    private Long problemVersionId;
    private Long authorId;
    private String title;
    private String contentMarkdown;
    private String contentHtml;
    private String status;
    private Boolean featured;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("published_at")
    private LocalDateTime publishedAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
