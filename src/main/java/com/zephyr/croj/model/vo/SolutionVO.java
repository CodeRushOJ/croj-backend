package com.zephyr.croj.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SolutionVO {
    private Long id;
    private Long problemId;
    private Long problemVersionId;
    private Long authorId;
    private String authorName;
    private String title;
    private String contentMarkdown;
    private String contentHtml;
    private Boolean featured;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
