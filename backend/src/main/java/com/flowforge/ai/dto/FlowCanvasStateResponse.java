package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FlowCanvasStateResponse(
        UUID flowId,
        long revision,
        boolean saved,
        Map<String, FlowCanvasPositionDto> positions,
        List<FlowCanvasEdgeDto> edges,
        FlowCanvasViewportDto viewport,
        LocalDateTime updatedAt
) {
}
