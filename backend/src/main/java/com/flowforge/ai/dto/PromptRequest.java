package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PromptRequest(
        @NotBlank(message = "title is required")
        @Size(max = 120, message = "title must be less than 120 characters")
        String title,

        @NotBlank(message = "category is required")
        @Size(max = 80, message = "category must be less than 80 characters")
        String category,

        @NotBlank(message = "description is required")
        @Size(max = 300, message = "description must be less than 300 characters")
        String description,

        @NotBlank(message = "content is required")
        @Size(max = 12000, message = "content must be less than 12000 characters")
        String content,

        List<@Size(max = 32, message = "tag must be less than 32 characters") String> tags,

        Boolean favorite,

        UUID sourceTaskId,

        UUID sourceFlowId,

        @Size(max = 80, message = "sourceNodeId must be less than 80 characters")
        String sourceNodeId
) {
}
