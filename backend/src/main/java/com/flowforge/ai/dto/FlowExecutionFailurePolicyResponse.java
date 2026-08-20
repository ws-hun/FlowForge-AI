package com.flowforge.ai.dto;

/**
 * Versioned failure behavior for one compiled Flow execution plan.
 *
 * <p>The first policy describes the current single-pass boundary. It is
 * intentionally separate from the node execution plan version so older
 * plans remain readable when policy fields were not persisted yet.</p>
 */
public record FlowExecutionFailurePolicyResponse(
        String version,
        String onProviderFailure,
        String downstreamNodeAction,
        String retryStrategy,
        int maxAttempts
) {
}
