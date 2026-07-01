package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiApiKeyRequest(
        @NotBlank(message = "provider is required")
        @Size(max = 40, message = "provider must be less than 40 characters")
        String provider,

        @NotBlank(message = "apiKey is required")
        String apiKey,

        @NotBlank(message = "baseUrl is required")
        @Size(max = 300, message = "baseUrl must be less than 300 characters")
        String baseUrl,

        @NotBlank(message = "model is required")
        @Size(max = 120, message = "model must be less than 120 characters")
        String model,

        Boolean active
) {
}
