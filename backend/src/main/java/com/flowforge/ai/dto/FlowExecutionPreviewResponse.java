package com.flowforge.ai.dto;

import java.util.List;

/**
 * Server-compiled input and immutable context that would be used for a Flow run.
 */
public record FlowExecutionPreviewResponse(
        String executionMode,
        int providerCallCount,
        String compilerVersion,
        String executionInputFingerprint,
        String executionInput,
        FlowRunSnapshotResponse flowRunSnapshot,
        List<FlowExecutionSectionResponse> sections,
        boolean executable,
        List<String> missingVariables,
        List<String> incompleteNodes
) {
}
