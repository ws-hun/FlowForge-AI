package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskHistoryResponse(
        UUID id,
        String input,
        String summary,
        String result,
        LocalDateTime createdAt
) {
}
