package com.flowforge.ai.dto;

import java.util.List;

/**
 * Versioned, deterministic node schedule compiled from one immutable Flow snapshot.
 */
public record FlowExecutionPlanResponse(
        String version,
        String scheduling,
        List<FlowExecutionStepResponse> steps,
        FlowExecutionFailurePolicyResponse failurePolicy,
        FlowInputResolutionContractResponse inputResolutionContract
) {
    public FlowExecutionPlanResponse(
            String version,
            String scheduling,
            List<FlowExecutionStepResponse> steps
    ) {
        this(version, scheduling, steps, null, null);
    }

    public FlowExecutionPlanResponse(
            String version,
            String scheduling,
            List<FlowExecutionStepResponse> steps,
            FlowExecutionFailurePolicyResponse failurePolicy
    ) {
        this(version, scheduling, steps, failurePolicy, null);
    }
}
