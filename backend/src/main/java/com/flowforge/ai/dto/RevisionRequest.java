package com.flowforge.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RevisionRequest(
        @NotNull(message = "revision is required")
        @PositiveOrZero(message = "revision must be zero or greater")
        Long revision
) {
}
