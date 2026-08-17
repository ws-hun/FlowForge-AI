package com.flowforge.ai.dto;

/**
 * Stable reference to one planned Flow artifact and the record that materializes it.
 */
public record FlowArtifactContractResponse(
        String key,
        String type,
        String storage
) {
}
