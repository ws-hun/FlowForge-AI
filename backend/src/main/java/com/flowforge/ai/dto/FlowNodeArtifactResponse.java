package com.flowforge.ai.dto;

/**
 * Immutable proof that one planned node output was materialized, failed, or skipped.
 */
public record FlowNodeArtifactResponse(
        String key,
        String type,
        String storage,
        String state,
        String contentFingerprint
) {
}
