package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthProfileUpdateRequest(
        @NotBlank(message = "displayName is required")
        @Size(max = 80, message = "displayName must be less than 80 characters")
        String displayName
) {
}
