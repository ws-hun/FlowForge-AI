package com.flowforge.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record FlowRequest(
        @NotBlank(message = "title is required")
        @Size(max = 120, message = "title must be less than 120 characters")
        String title,

        @NotBlank(message = "description is required")
        @Size(max = 2000, message = "description must be less than 2000 characters")
        String description,

        @NotEmpty(message = "nodes are required")
        @Size(max = 50, message = "flow can contain at most 50 nodes")
        List<@Valid FlowNodeDto> nodes,

        UUID sourceFlowId,

        UUID sourceFlowVersionId
) {
}
