package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record FlowRevisionRequest(
        @NotNull(message = "revision is required")
        @PositiveOrZero(message = "revision must be zero or greater")
        Long revision
) {
}
