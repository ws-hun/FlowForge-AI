package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FlowVersionResponse(
        UUID id,
        UUID flowId,
        int versionNumber,
        String title,
        String description,
        List<FlowNodeDto> nodes,
        LocalDateTime createdAt
) {
}
