package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FlowNodeDto(
        @NotBlank(message = "node id is required")
        @Size(max = 80, message = "node id must be less than 80 characters")
        String id,

        @NotBlank(message = "node type is required")
        @Size(max = 40, message = "node type must be less than 40 characters")
        String type,

        @NotBlank(message = "node title is required")
        @Size(max = 120, message = "node title must be less than 120 characters")
        String title,

        @NotBlank(message = "node description is required")
        @Size(max = 500, message = "node description must be less than 500 characters")
        String description,

        @Size(max = 12000, message = "node content must be less than 12000 characters")
        String content,

        UUID promptId,

        @Size(max = 120, message = "prompt title must be less than 120 characters")
        String promptTitle
) {
}
