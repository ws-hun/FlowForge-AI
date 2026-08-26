package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowProviderAttemptPolicyResponse;
import com.flowforge.ai.dto.FlowProviderAttemptResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class FlowProviderAttemptPolicy {

    static final String VERSION = "flow-provider-attempt-policy-v1";
    static final String STATE_NOT_RECORDED = "not-recorded";
    static final String RECOVERY_NONE = "none";
    static final String RECOVERY_NEW_RUN = "create-new-run";

    public FlowProviderAttemptPolicyResponse evaluate(List<FlowProviderAttemptResponse> attempts) {
        List<FlowProviderAttemptResponse> immutableAttempts = attempts == null
                ? List.of()
                : List.copyOf(attempts);
        validate(immutableAttempts);

        String currentState = immutableAttempts.isEmpty()
                ? STATE_NOT_RECORDED
                : immutableAttempts.get(immutableAttempts.size() - 1).status();
        String recoveryAction = "failed".equals(currentState)
                ? RECOVERY_NEW_RUN
                : RECOVERY_NONE;
        return new FlowProviderAttemptPolicyResponse(
                VERSION,
                currentState,
                immutableAttempts.size(),
                false,
                false,
                recoveryAction
        );
    }

    void validate(List<FlowProviderAttemptResponse> attempts) {
        FlowProviderAttemptResponse previous = null;
        for (int index = 0; index < attempts.size(); index++) {
            FlowProviderAttemptResponse current = attempts.get(index);
            int expectedNumber = index + 1;
            if (current == null
                    || current.id() == null
                    || current.attemptNumber() == null
                    || current.attemptNumber() != expectedNumber
                    || !List.of("completed", "failed").contains(current.status())
                    || current.durationMs() == null
                    || current.durationMs() < 0) {
                throw new IllegalStateException("Invalid Flow Provider attempt chain");
            }

            if (previous == null) {
                if (!"initial".equals(current.triggerType()) || current.previousAttemptId() != null) {
                    throw new IllegalStateException("Invalid Flow Provider attempt chain");
                }
            } else if (!"failed".equals(previous.status())
                    || !List.of("automatic-retry", "manual-recovery").contains(current.triggerType())
                    || !Objects.equals(current.previousAttemptId(), previous.id())) {
                throw new IllegalStateException("Invalid Flow Provider attempt chain");
            }
            previous = current;
        }
    }
}
