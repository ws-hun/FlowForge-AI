package com.flowforge.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Persisted trace for the deterministic preparation steps and one shared Provider call of a Flow run.
 */
public record FlowRunTraceResponse(
        UUID runId,
        UUID flowId,
        String status,
        String executionMode,
        Integer providerCallCount,
        String compilerVersion,
        String executionInputFingerprint,
        String inputSource,
        UUID replayedFromTaskId,
        List<FlowNodeRunTraceResponse> nodes
) {
}
