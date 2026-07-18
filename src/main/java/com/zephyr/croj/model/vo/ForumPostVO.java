package com.zephyr.croj.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ForumPostVO {
    private Long id;
    private Long categoryId;
    private Long authorId;
    private String authorName;
    private String title;
    private String contentMarkdown;
    private String contentHtml;
    private Boolean pinned;
    private Boolean locked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
