package com.flowforge.ai.dto;

import java.util.UUID;

public record TaskRunResponse(
        String summary,
        String result,
        String raw,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long durationMs,
        UUID rerunOfTaskId,
        UUID continuedFromTaskId,
        String executionInput,
        UUID taskId,
        FlowRunSnapshotResponse flowRunSnapshot
) {
}
