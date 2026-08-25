package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthPasswordChangeRequest(
        @NotBlank(message = "currentPassword is required")
        @Size(max = 128, message = "currentPassword must be less than 128 characters")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(min = 10, max = 128, message = "newPassword must contain 10 to 128 characters")
        String newPassword
) {
}
