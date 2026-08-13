package com.flowforge.ai.dto;

import java.util.List;

/**
 * One ordered node responsibility in the compiled Flow execution plan.
 */
public record FlowExecutionStepResponse(
        int sequence,
        String nodeId,
        String nodeType,
        String title,
        String operation,
        List<String> dependsOnNodeIds,
        boolean providerBoundary
) {
}
