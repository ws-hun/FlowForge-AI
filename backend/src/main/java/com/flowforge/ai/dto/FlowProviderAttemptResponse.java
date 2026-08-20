package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One immutable invocation attempt owned by an AI Task node artifact.
 */
public record FlowProviderAttemptResponse(
        UUID id,
        Integer attemptNumber,
        String triggerType,
        UUID previousAttemptId,
        String status,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
}
