package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ErrorResponse(
        String message,
        LocalDateTime timestamp,
        UUID runId
) {
    public ErrorResponse(String message, LocalDateTime timestamp) {
        this(message, timestamp, null);
    }
}
