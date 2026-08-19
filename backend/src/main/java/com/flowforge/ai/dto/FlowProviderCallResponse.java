package com.flowforge.ai.dto;

/**
 * Provenance for the one real Provider call represented by an AI Task artifact.
 */
public record FlowProviderCallResponse(
        String status,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long durationMs,
        String errorMessage
) {
}
