package com.flowforge.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record FlowCanvasViewportDto(
        @NotNull
        @DecimalMin(value = "-100000")
        @DecimalMax(value = "100000")
        Double x,

        @NotNull
        @DecimalMin(value = "-100000")
        @DecimalMax(value = "100000")
        Double y,

        @NotNull
        @DecimalMin(value = "0.2")
        @DecimalMax(value = "1.6")
        Double zoom
) {
}
