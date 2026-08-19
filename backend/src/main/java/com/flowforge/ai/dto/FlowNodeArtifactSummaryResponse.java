package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FlowNodeArtifactSummaryResponse(
        UUID id,
        UUID taskId,
        UUID flowId,
        String nodeId,
        Integer sequence,
        String artifactKey,
        String artifactType,
        String state,
        String mediaType,
        String contentFingerprint,
        String inputArtifactKey,
        String inputArtifactType,
        String inputArtifactStorage,
        String inputArtifactState,
        String inputResolution,
        String inputContentFingerprint,
        FlowProviderCallResponse providerCall,
        LocalDateTime createdAt
) {
    public FlowNodeArtifactSummaryResponse(
            UUID id,
            UUID taskId,
            UUID flowId,
            String nodeId,
            Integer sequence,
            String artifactKey,
            String artifactType,
            String state,
            String mediaType,
            String contentFingerprint,
            String inputArtifactKey,
            String inputArtifactType,
            String inputArtifactStorage,
            String inputArtifactState,
            String inputResolution,
            String inputContentFingerprint,
            LocalDateTime createdAt
    ) {
        this(
                id, taskId, flowId, nodeId, sequence, artifactKey, artifactType, state,
                mediaType, contentFingerprint, inputArtifactKey, inputArtifactType,
                inputArtifactStorage, inputArtifactState, inputResolution,
                inputContentFingerprint, null, createdAt
        );
    }
}
