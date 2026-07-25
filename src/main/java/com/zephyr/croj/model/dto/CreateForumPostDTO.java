package com.zephyr.croj.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateForumPostDTO {
    @NotNull @Positive
    private Long categoryId;
    @NotBlank @Size(max = 255)
    private String title;
    @NotBlank @Size(max = 100_000)
    private String contentMarkdown;
}
