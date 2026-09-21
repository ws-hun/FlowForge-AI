package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FlowResponse(
        UUID id,
        String title,
        String description,
        List<FlowNodeDto> nodes,
        UUID sourceTaskId,
        String sourceTaskSummary,
        UUID sourcePromptId,
        String sourcePromptTitle,
        UUID sourceFlowId,
        String sourceFlowTitle,
        UUID sourceFlowVersionId,
        Integer sourceFlowVersionNumber,
        Long revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
