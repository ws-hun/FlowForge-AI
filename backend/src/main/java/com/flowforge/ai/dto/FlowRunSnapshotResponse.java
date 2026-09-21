package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable execution context captured when a Flow is run.
 */
public record FlowRunSnapshotResponse(
        UUID flowId,
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
        LocalDateTime flowUpdatedAt,
        String runtimeContext,
        Map<String, String> variableValues
) {

    public FlowRunSnapshotResponse(
            UUID flowId,
            String title,
            String description,
            List<FlowNodeDto> nodes,
            UUID sourceFlowId,
            String sourceFlowTitle,
            UUID sourceFlowVersionId,
            Integer sourceFlowVersionNumber,
            LocalDateTime flowUpdatedAt,
            String runtimeContext,
            Map<String, String> variableValues
    ) {
        this(
                flowId,
                title,
                description,
                nodes,
                null,
                null,
                null,
                null,
                sourceFlowId,
                sourceFlowTitle,
                sourceFlowVersionId,
                sourceFlowVersionNumber,
                flowUpdatedAt,
                runtimeContext,
                variableValues
        );
    }
}
