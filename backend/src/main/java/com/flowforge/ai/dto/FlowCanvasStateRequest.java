package com.flowforge.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record FlowCanvasStateRequest(
        @NotNull
        @PositiveOrZero
        Long revision,

        @NotNull
        @Size(max = 50)
        Map<@Size(max = 80) String, @Valid FlowCanvasPositionDto> positions,

        @NotNull
        @Size(max = 500)
        List<@Valid FlowCanvasEdgeDto> edges,

        @Valid FlowCanvasViewportDto viewport
) {
}
