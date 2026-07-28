package com.flowforge.ai.dto;

import java.time.LocalDateTime;

public record HealthResponse(
        String status,
        String database,
        LocalDateTime timestamp
) {
}
