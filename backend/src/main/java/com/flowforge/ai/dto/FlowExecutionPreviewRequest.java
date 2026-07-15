package com.flowforge.ai.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Runtime values supplied for a non-persistent preview of a saved Flow.
 */
public record FlowExecutionPreviewRequest(
        @Size(max = 8000, message = "runtime context must be less than 8000 characters")
        String runtimeContext,
        @Size(max = 50, message = "flow variables must contain at most 50 entries")
        Map<String, String> variableValues
) {
}
