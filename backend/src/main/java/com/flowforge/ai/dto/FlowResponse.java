package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FlowResponse(
        UUID id,
        String title,
        String description,
        List<FlowNodeDto> nodes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
