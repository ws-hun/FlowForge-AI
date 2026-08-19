package com.flowforge.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * The ordered path from a node artifact back to its immutable Flow source.
 */
public record FlowNodeArtifactLineageResponse(
        UUID taskId,
        String requestedArtifactKey,
        boolean complete,
        String termination,
        List<FlowNodeArtifactLineageEntryResponse> path
) {
}
