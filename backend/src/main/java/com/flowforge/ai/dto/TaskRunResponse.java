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
        UUID rerunOfTaskId,
        String executionInput,
        UUID taskId,
        FlowRunSnapshotResponse flowRunSnapshot
) {
}
