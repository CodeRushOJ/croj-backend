package com.zephyr.croj.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateForumCommentDTO {
    @Positive
    private Long parentId;
    @NotBlank @Size(max = 10_000)
    private String contentMarkdown;
}
