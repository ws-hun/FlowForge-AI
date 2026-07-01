package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunTaskRequest(
        @NotBlank(message = "input is required")
        @Size(max = 8000, message = "input must be less than 8000 characters")
        String input
) {
}
