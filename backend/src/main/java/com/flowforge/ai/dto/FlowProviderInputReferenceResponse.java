package com.flowforge.ai.dto;

import java.util.UUID;

/**
 * One persisted declaration that contributed to the single Provider boundary.
 */
public record FlowProviderInputReferenceResponse(
        Integer inputOrder,
        String artifactKey,
        String artifactType,
        String artifactStorage,
        String artifactState,
        String inputResolution,
        String contentFingerprint,
        UUID sourceArtifactId,
        String sourceNodeId
) {
}
