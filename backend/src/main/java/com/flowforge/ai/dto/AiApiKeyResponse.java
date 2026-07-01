package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AiApiKeyResponse(
        UUID id,
        String provider,
        String maskedKey,
        String baseUrl,
        String model,
        boolean active,
        LocalDateTime updatedAt
) {
}
