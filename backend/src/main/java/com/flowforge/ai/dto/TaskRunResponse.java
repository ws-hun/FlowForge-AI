package com.flowforge.ai.dto;

import java.util.UUID;

public record TaskRunResponse(
        String summary,
        String result,
        String raw,
        String executionInput,
        UUID taskId,
        FlowRunSnapshotResponse flowRunSnapshot
) {
}
