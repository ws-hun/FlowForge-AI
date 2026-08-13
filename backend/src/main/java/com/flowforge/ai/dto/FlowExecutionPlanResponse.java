package com.flowforge.ai.dto;

import java.util.List;

/**
 * Versioned, deterministic node schedule compiled from one immutable Flow snapshot.
 */
public record FlowExecutionPlanResponse(
        String version,
        String scheduling,
        List<FlowExecutionStepResponse> steps
) {
}
