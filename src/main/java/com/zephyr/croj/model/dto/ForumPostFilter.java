package com.zephyr.croj.model.dto;

import com.zephyr.croj.community.ForumResourceType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ForumPostFilter {
    @Positive
    private Long categoryId;
    private ForumResourceType resourceType = ForumResourceType.GENERAL;
    @Positive
    private Long resourceId;

    @AssertTrue(message = "resourceId is required for PROBLEM/CONTEST and forbidden for GENERAL")
    public boolean isResourceReferenceValid() {
        return resourceType == ForumResourceType.GENERAL ? resourceId == null : resourceId != null;
    }
}
