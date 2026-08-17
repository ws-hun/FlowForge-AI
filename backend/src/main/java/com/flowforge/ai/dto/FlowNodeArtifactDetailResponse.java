package com.flowforge.ai.dto;

import java.time.LocalDateTime;
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
        LocalDateTime createdAt
) {
}
