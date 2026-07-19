package com.zephyr.croj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_forum_post")
public class ForumPost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private Long authorId;
    private String resourceType;
    private Long resourceId;
    private String title;
    private String contentMarkdown;
    private String contentHtml;
    private String status;
    private Boolean pinned;
    private Boolean locked;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
