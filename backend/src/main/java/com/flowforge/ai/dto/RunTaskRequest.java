package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record RunTaskRequest(
        @NotBlank(message = "input is required")
        @Size(max = 8000, message = "input must be less than 8000 characters")
        String input,
        UUID promptId,
        UUID flowId,
        @Size(max = 8000, message = "flow run context must be less than 8000 characters")
        String flowRunContext,
        @Size(max = 50, message = "flow variables must contain at most 50 entries")
        Map<String, String> flowVariableValues
) {
}
