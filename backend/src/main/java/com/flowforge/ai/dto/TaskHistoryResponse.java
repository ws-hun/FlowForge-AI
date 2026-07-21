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
        UUID rerunOfTaskId,
        UUID continuedFromTaskId,
        UUID sourcePromptId,
        String sourcePromptTitle,
        UUID sourceFlowId,
        String sourceFlowTitle,
        FlowRunSnapshotResponse flowRunSnapshot,
        LocalDateTime createdAt
) {
}
