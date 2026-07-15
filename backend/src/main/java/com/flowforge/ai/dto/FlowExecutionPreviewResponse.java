package com.flowforge.ai.dto;

/**
 * Server-compiled input and immutable context that would be used for a Flow run.
 */
public record FlowExecutionPreviewResponse(
        String executionInput,
        FlowRunSnapshotResponse flowRunSnapshot
) {
}
