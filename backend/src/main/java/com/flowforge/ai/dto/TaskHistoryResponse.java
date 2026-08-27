package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskHistoryResponse(
        UUID id,
        String input,
        String summary,
        String result,
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
        String status,
        String errorMessage,
        UUID sourcePromptId,
        String sourcePromptTitle,
        UUID sourceFlowId,
        String sourceFlowTitle,
        FlowRunSnapshotResponse flowRunSnapshot,
        FlowRunTraceResponse flowRunTrace,
        LocalDateTime createdAt
) {
    public TaskHistoryResponse(
            UUID id,
            String input,
            String summary,
            String result,
            String provider,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            Long durationMs,
            UUID rerunOfTaskId,
            UUID continuedFromTaskId,
            UUID inputVariantOfTaskId,
            String status,
            String errorMessage,
            UUID sourcePromptId,
            String sourcePromptTitle,
            UUID sourceFlowId,
            String sourceFlowTitle,
            FlowRunSnapshotResponse flowRunSnapshot,
            FlowRunTraceResponse flowRunTrace,
            LocalDateTime createdAt
    ) {
        this(
                id, input, summary, result, provider, model, inputTokens, outputTokens,
                totalTokens, durationMs, rerunOfTaskId, null, continuedFromTaskId,
                inputVariantOfTaskId, status, errorMessage, sourcePromptId,
                sourcePromptTitle, sourceFlowId, sourceFlowTitle, flowRunSnapshot,
                flowRunTrace, createdAt
        );
    }
}
