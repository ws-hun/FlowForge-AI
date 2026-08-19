package com.flowforge.ai.dto;

import java.util.UUID;

/**
 * One metadata-only step in a persisted artifact lineage path.
 */
public record FlowNodeArtifactLineageEntryResponse(
        UUID id,
        String nodeId,
        Integer sequence,
        String artifactKey,
        String artifactType,
        String storage,
        String state,
        String mediaType,
        String contentFingerprint,
        String inputResolution,
        FlowProviderCallResponse providerCall,
        boolean persisted
) {
    public FlowNodeArtifactLineageEntryResponse(
            UUID id,
            String nodeId,
            Integer sequence,
            String artifactKey,
            String artifactType,
            String storage,
            String state,
            String mediaType,
            String contentFingerprint,
            String inputResolution,
            boolean persisted
    ) {
        this(
                id, nodeId, sequence, artifactKey, artifactType, storage, state,
                mediaType, contentFingerprint, inputResolution, null, persisted
        );
    }
}
