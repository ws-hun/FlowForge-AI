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
        UUID sourceFlowId,
        String sourceFlowTitle,
        UUID sourceFlowVersionId,
        Integer sourceFlowVersionNumber,
        LocalDateTime flowUpdatedAt,
        String runtimeContext,
        Map<String, String> variableValues
) {
}
