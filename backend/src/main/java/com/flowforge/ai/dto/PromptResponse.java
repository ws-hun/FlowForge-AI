package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PromptResponse(
        UUID id,
        String title,
        String category,
        String description,
        String content,
        List<String> tags,
        boolean favorite,
        UUID sourceTaskId,
        String sourceTaskSummary,
        UUID sourcePromptId,
        String sourcePromptTitle,
        UUID sourceFlowId,
        String sourceFlowTitle,
        String sourceNodeId,
        String sourceNodeTitle,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
