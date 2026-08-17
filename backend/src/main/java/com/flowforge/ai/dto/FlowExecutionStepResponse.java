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
        boolean providerBoundary,
        FlowArtifactContractResponse inputArtifact,
        FlowArtifactContractResponse outputArtifact
) {
    public FlowExecutionStepResponse(
            int sequence,
            String nodeId,
            String nodeType,
            String title,
            String operation,
            List<String> dependsOnNodeIds,
            boolean providerBoundary
    ) {
        this(sequence, nodeId, nodeType, title, operation, dependsOnNodeIds, providerBoundary, null, null);
    }
}
