package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlowCanvasEdgeDto(
        @NotBlank
        @Size(max = 120)
        String id,

        @NotBlank
        @Size(max = 80)
        String source,

        @NotBlank
        @Size(max = 80)
        String target,

        @Size(max = 40)
        String sourceHandle,

        @Size(max = 40)
        String targetHandle
) {
}
