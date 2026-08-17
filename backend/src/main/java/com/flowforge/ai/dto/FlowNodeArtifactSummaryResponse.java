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
        LocalDateTime createdAt
) {
}
