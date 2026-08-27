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
        UUID recoveryOfTaskId,
        UUID continuedFromTaskId,
        UUID inputVariantOfTaskId,
        String executionInput,
        UUID taskId,
        FlowRunSnapshotResponse flowRunSnapshot,
        FlowRunTraceResponse flowRunTrace
) {
    public TaskRunResponse(
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
            UUID inputVariantOfTaskId,
            String executionInput,
            UUID taskId,
            FlowRunSnapshotResponse flowRunSnapshot,
            FlowRunTraceResponse flowRunTrace
    ) {
        this(
                summary, result, raw, provider, model, inputTokens, outputTokens,
                totalTokens, durationMs, rerunOfTaskId, null, continuedFromTaskId,
                inputVariantOfTaskId, executionInput, taskId, flowRunSnapshot, flowRunTrace
        );
    }
}
