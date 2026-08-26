package com.flowforge.ai.dto;

/**
 * Versioned retry and recovery boundary for one Provider attempt chain.
 */
public record FlowProviderAttemptPolicyResponse(
        String version,
        String currentState,
        int recordedAttempts,
        boolean automaticRetryEnabled,
        boolean sameArtifactRecoveryEnabled,
        String failedRunRecoveryAction
) {
}
