package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowProviderAttemptPolicyResponse;
import com.flowforge.ai.dto.FlowProviderAttemptResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowProviderAttemptPolicyTest {

    private final FlowProviderAttemptPolicy policy = new FlowProviderAttemptPolicy();

    @Test
    void reportsTheCurrentNoRetryBoundaryForACompletedAttempt() {
        FlowProviderAttemptPolicyResponse response = policy.evaluate(List.of(
                attempt(1, "initial", null, "completed")
        ));

        assertThat(response.version()).isEqualTo("flow-provider-attempt-policy-v1");
        assertThat(response.currentState()).isEqualTo("completed");
        assertThat(response.recordedAttempts()).isEqualTo(1);
        assertThat(response.automaticRetryEnabled()).isFalse();
        assertThat(response.sameArtifactRecoveryEnabled()).isFalse();
        assertThat(response.failedRunRecoveryAction()).isEqualTo("none");
    }

    @Test
    void directsFailedRunsToANewRunWithoutMutatingTheAttemptChain() {
        FlowProviderAttemptPolicyResponse response = policy.evaluate(List.of(
                attempt(1, "initial", null, "failed")
        ));

        assertThat(response.currentState()).isEqualTo("failed");
        assertThat(response.failedRunRecoveryAction()).isEqualTo("create-new-run");
        assertThat(response.automaticRetryEnabled()).isFalse();
        assertThat(response.sameArtifactRecoveryEnabled()).isFalse();
    }

    @Test
    void keepsLegacyArtifactsHonestWhenNoAttemptWasRecorded() {
        FlowProviderAttemptPolicyResponse response = policy.evaluate(List.of());

        assertThat(response.currentState()).isEqualTo("not-recorded");
        assertThat(response.recordedAttempts()).isZero();
        assertThat(response.failedRunRecoveryAction()).isEqualTo("none");
    }

    @Test
    void acceptsAContiguousFutureRecoveryChainAfterFailure() {
        FlowProviderAttemptResponse failed = attempt(1, "initial", null, "failed");
        FlowProviderAttemptResponse recovered = attempt(
                2,
                "manual-recovery",
                failed.id(),
                "completed"
        );

        FlowProviderAttemptPolicyResponse response = policy.evaluate(List.of(failed, recovered));

        assertThat(response.currentState()).isEqualTo("completed");
        assertThat(response.recordedAttempts()).isEqualTo(2);
    }

    @Test
    void rejectsBranchesAndAttemptsAfterACompletedCall() {
        FlowProviderAttemptResponse completed = attempt(1, "initial", null, "completed");
        FlowProviderAttemptResponse retry = attempt(
                2,
                "automatic-retry",
                completed.id(),
                "completed"
        );

        assertThatThrownBy(() -> policy.evaluate(List.of(completed, retry)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid Flow Provider attempt chain");
    }

    @Test
    void rejectsMissingImmediateAttemptLinks() {
        FlowProviderAttemptResponse failed = attempt(1, "initial", null, "failed");
        FlowProviderAttemptResponse recovery = attempt(
                2,
                "manual-recovery",
                UUID.randomUUID(),
                "completed"
        );

        assertThatThrownBy(() -> policy.evaluate(List.of(failed, recovery)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid Flow Provider attempt chain");
    }

    private FlowProviderAttemptResponse attempt(
            int number,
            String trigger,
            UUID previousAttemptId,
            String status
    ) {
        return new FlowProviderAttemptResponse(
                UUID.randomUUID(),
                number,
                trigger,
                previousAttemptId,
                status,
                "deepseek",
                "deepseek-chat",
                null,
                null,
                null,
                500L,
                "failed".equals(status) ? "Provider unavailable" : null,
                LocalDateTime.of(2026, 8, 26, 12, number)
        );
    }
}
