package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FlowNodeArtifactDetailResponse(
        UUID id,
        UUID taskId,
        UUID flowId,
        String nodeId,
        Integer sequence,
        String artifactKey,
        String artifactType,
        String state,
        String mediaType,
        String payload,
        String contentFingerprint,
        String inputArtifactKey,
        String inputArtifactType,
        String inputArtifactStorage,
        String inputArtifactState,
        String inputResolution,
        String inputContentFingerprint,
        FlowProviderCallResponse providerCall,
        List<FlowProviderAttemptResponse> providerAttempts,
        FlowProviderAttemptPolicyResponse providerAttemptPolicy,
        LocalDateTime createdAt
) {
    public FlowNodeArtifactDetailResponse(
            UUID id,
            UUID taskId,
            UUID flowId,
            String nodeId,
            Integer sequence,
            String artifactKey,
            String artifactType,
            String state,
            String mediaType,
            String payload,
            String contentFingerprint,
            String inputArtifactKey,
            String inputArtifactType,
            String inputArtifactStorage,
            String inputArtifactState,
            String inputResolution,
            String inputContentFingerprint,
            FlowProviderCallResponse providerCall,
            List<FlowProviderAttemptResponse> providerAttempts,
            LocalDateTime createdAt
    ) {
        this(
                id, taskId, flowId, nodeId, sequence, artifactKey, artifactType, state,
                mediaType, payload, contentFingerprint, inputArtifactKey, inputArtifactType,
                inputArtifactStorage, inputArtifactState, inputResolution,
                inputContentFingerprint, providerCall, providerAttempts, null, createdAt
        );
    }

    public FlowNodeArtifactDetailResponse(
            UUID id,
            UUID taskId,
            UUID flowId,
            String nodeId,
            Integer sequence,
            String artifactKey,
            String artifactType,
            String state,
            String mediaType,
            String payload,
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
        this(
                id, taskId, flowId, nodeId, sequence, artifactKey, artifactType, state,
                mediaType, payload, contentFingerprint, inputArtifactKey, inputArtifactType,
                inputArtifactStorage, inputArtifactState, inputResolution,
                inputContentFingerprint, providerCall, List.of(), null, createdAt
        );
    }

    public FlowNodeArtifactDetailResponse(
            UUID id,
            UUID taskId,
            UUID flowId,
            String nodeId,
            Integer sequence,
            String artifactKey,
            String artifactType,
            String state,
            String mediaType,
            String payload,
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
                mediaType, payload, contentFingerprint, inputArtifactKey, inputArtifactType,
                inputArtifactStorage, inputArtifactState, inputResolution,
                inputContentFingerprint, null, List.of(), null, createdAt
        );
    }
}
