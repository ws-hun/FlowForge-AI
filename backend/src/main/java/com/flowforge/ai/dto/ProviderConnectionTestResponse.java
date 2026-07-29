package com.flowforge.ai.dto;

import java.time.LocalDateTime;

public record ProviderConnectionTestResponse(
        String provider,
        String model,
        String status,
        LocalDateTime checkedAt
) {
}
