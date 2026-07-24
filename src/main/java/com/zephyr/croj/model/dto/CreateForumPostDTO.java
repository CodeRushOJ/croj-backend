package com.zephyr.croj.model.dto;

import com.zephyr.croj.community.ForumResourceType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateForumPostDTO {
    @NotNull @Positive
    private Long categoryId;
    @NotNull
    private ForumResourceType resourceType = ForumResourceType.GENERAL;
    @Positive
    private Long resourceId;
    @NotBlank @Size(max = 255)
    private String title;
    @NotBlank @Size(max = 100_000)
    private String contentMarkdown;

    @AssertTrue(message = "resourceId is required for PROBLEM/CONTEST and forbidden for GENERAL")
    public boolean isResourceReferenceValid() {
        if (resourceType == null) {
            return false;
        }
        return resourceType == ForumResourceType.GENERAL ? resourceId == null : resourceId != null;
    }
}
