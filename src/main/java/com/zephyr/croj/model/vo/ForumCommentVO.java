package com.zephyr.croj.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ForumCommentVO {
    private Long id;
    private Long postId;
    private Long parentId;
    private Long authorId;
    private String authorName;
    private String contentMarkdown;
    private String contentHtml;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
