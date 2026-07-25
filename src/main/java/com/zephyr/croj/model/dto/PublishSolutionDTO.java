package com.zephyr.croj.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublishSolutionDTO {
    @NotBlank @Size(max = 255)
    private String title;
    @NotBlank @Size(max = 100_000)
    private String contentMarkdown;
}
