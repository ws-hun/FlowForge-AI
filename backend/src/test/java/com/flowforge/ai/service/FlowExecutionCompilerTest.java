package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowExecutionFailurePolicyResponse;
import com.flowforge.ai.dto.FlowExecutionPlanResponse;
import com.flowforge.ai.dto.FlowExecutionStepResponse;
import com.flowforge.ai.dto.FlowArtifactContractResponse;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowNodeRunTraceResponse;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.dto.FlowRunTraceResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowExecutionCompilerTest {

    private final FlowExecutionCompiler compiler = new FlowExecutionCompiler();

    @Test
    void compilesThePersistedFlowIntoOneDeterministicProviderInput() {
        FlowRunSnapshotResponse snapshot = snapshot(Map.of("audience", "product teams"));

        FlowExecutionCompiler.Compilation compilation = compiler.compile(snapshot);

        assertThat(compilation.executionMode()).isEqualTo("single-pass");
        assertThat(compilation.providerCallCount()).isEqualTo(1);
        assertThat(compilation.compilerVersion()).isEqualTo("flow-compiler-v1");
        assertThat(compilation.executionInputFingerprint())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(compiler.fingerprint(compilation.executionInput()));
        assertThat(compilation.executionInput())
                .contains("Flow: Launch workspace")
                .contains("本次运行上下文:\nShip a focused first release.")
                .contains("## Audience context\nPrioritize product teams.")
                .contains("## Launch prompt\nCreate a launch plan for product teams.")
                .contains("## AI Execution\nMake tradeoffs explicit for product teams.")
                .contains("## Structured Result\nEnd with next actions for product teams.")
                .endsWith("请输出：1. Summary 2. Key Points 3. Result 4. Next Actions");
        assertThat(compilation.sections())
                .extracting(section -> section.kind())
                .containsExactly(
                        "objective",
                        "input-context",
                        "runtime-context",
                        "prompt",
                        "execution-guidance",
                        "delivery-focus",
                        "response-contract"
                );
        assertThat(compilation.plan().version()).isEqualTo("flow-plan-v5");
        assertThat(compilation.plan().scheduling()).isEqualTo("linear");
        assertThat(compilation.plan().failurePolicy()).isEqualTo(
                new FlowExecutionFailurePolicyResponse(
                        "flow-failure-policy-v1",
                        "stop-run",
                        "skip",
                        "none",
                        1
                )
        );
        assertThat(compilation.plan().inputResolutionContract()).isEqualTo(
                new com.flowforge.ai.dto.FlowInputResolutionContractResponse(
                        "flow-input-resolution-v1",
                        "compiled-reference",
                        List.of("compiled-reference", "persisted-artifact"),
                        false,
                        "node-sequential-runtime"
                )
        );
        assertThat(compilation.plan().steps())
                .extracting(step -> step.nodeId())
                .containsExactly("input-1", "input-2", "prompt-1", "ai-task-1", "output-1");
        assertThat(compilation.plan().steps())
                .extracting(step -> step.operation())
                .containsExactly(
                        "supply-context",
                        "supply-context",
                        "supply-instructions",
                        "invoke-provider",
                        "define-delivery"
                );
        assertThat(compilation.plan().steps()).satisfies(steps -> {
            assertThat(steps).allSatisfy(step -> assertThat(step.inputResolution())
                    .isEqualTo("compiled-reference"));
            assertThat(steps.get(0).sequence()).isEqualTo(1);
            assertThat(steps.get(0).dependsOnNodeIds()).isEmpty();
            assertThat(steps.get(0).inputArtifact().key()).isEqualTo("flow:objective");
            assertThat(steps.get(0).inputArtifact().type()).isEqualTo("flow-objective");
            assertThat(steps.get(0).inputArtifact().storage()).isEqualTo("flow-snapshot");
            assertThat(steps.get(0).outputArtifact().type()).isEqualTo("context-contribution");
            assertThat(steps.get(0).outputArtifact().storage()).isEqualTo("node-artifact");
            assertThat(steps.get(1).dependsOnNodeIds()).containsExactly("input-1");
            assertThat(steps.get(1).inputArtifact()).isEqualTo(steps.get(0).outputArtifact());
            assertThat(steps.get(2).dependsOnNodeIds()).containsExactly("input-2");
            assertThat(steps.get(2).inputArtifact()).isEqualTo(steps.get(1).outputArtifact());
            assertThat(steps.get(2).outputArtifact().type()).isEqualTo("instruction-contribution");
            assertThat(steps.get(3).dependsOnNodeIds())
                    .containsExactly("input-1", "input-2", "prompt-1");
            assertThat(steps.get(3).providerBoundary()).isTrue();
            assertThat(steps.get(3).inputArtifact()).isEqualTo(steps.get(2).outputArtifact());
            assertThat(steps.get(3).providerInputArtifacts())
                    .extracting(FlowArtifactContractResponse::key)
                    .containsExactly(
                            "flow:objective",
                            "node:input-1:context-contribution",
                            "node:input-2:context-contribution",
                            "node:prompt-1:instruction-contribution"
                    );
            assertThat(steps.get(3).providerInputArtifacts())
                    .extracting(FlowArtifactContractResponse::type)
                    .containsExactly(
                            "flow-objective",
                            "context-contribution",
                            "context-contribution",
                            "instruction-contribution"
                    );
            assertThat(steps.get(3).outputArtifact().type()).isEqualTo("provider-result");
            assertThat(steps.get(3).outputArtifact().storage()).isEqualTo("node-artifact");
            assertThat(steps.get(4).dependsOnNodeIds()).containsExactly("ai-task-1");
            assertThat(steps.get(4).providerBoundary()).isFalse();
            assertThat(steps.get(4).providerInputArtifacts()).isNull();
            assertThat(steps.get(4).inputArtifact()).isEqualTo(steps.get(3).outputArtifact());
            assertThat(steps.get(4).outputArtifact().type()).isEqualTo("result-document");
            assertThat(steps.get(4).outputArtifact().storage()).isEqualTo("node-artifact");
        });
    }

    @Test
    void changesTheFingerprintWhenTheCompiledProviderInputChanges() {
        FlowExecutionCompiler.Compilation first = compiler.compile(snapshot(Map.of("audience", "product teams")));
        FlowExecutionCompiler.Compilation second = compiler.compile(snapshot(Map.of("audience", "engineering teams")));

        assertThat(first.executionInputFingerprint()).isNotEqualTo(second.executionInputFingerprint());
        assertThat(compiler.compile(snapshot(Map.of("audience", "product teams"))).executionInputFingerprint())
                .isEqualTo(first.executionInputFingerprint());
    }

    @Test
    void reportsMissingVariablesInFirstUseOrderAndPreservesUnresolvedTokens() {
        FlowRunSnapshotResponse snapshot = snapshot(Map.of("audience", ""));

        assertThat(compiler.findMissingVariables(snapshot)).containsExactly("audience");
        assertThat(compiler.applyVariables("For {audience} and {unknown}", Map.of("audience", "teams")))
                .isEqualTo("For teams and {unknown}");
    }

    @Test
    void validatesTheCurrentStopSkipNoRetryPolicyAgainstPersistedNodeStates() {
        FlowExecutionCompiler.Compilation compilation = compiler.compile(
                snapshot(Map.of("audience", "product teams"))
        );

        assertThatCode(() -> compiler.validateFailurePolicy(trace(
                "failed",
                compilation.plan(),
                List.of("prepared", "prepared", "prepared", "failed", "skipped")
        ))).doesNotThrowAnyException();

        assertThatThrownBy(() -> compiler.validateFailurePolicy(trace(
                "failed",
                compilation.plan(),
                List.of("prepared", "prepared", "prepared", "failed", "completed")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow failure policy contains an invalid node terminal state");
    }

    @Test
    void requiresPreparedContributionsAndTerminalAiTaskStates() {
        FlowExecutionPlanResponse plan = compiler.compile(
                snapshot(Map.of("audience", "product teams"))
        ).plan();

        assertThatThrownBy(() -> compiler.validateFailurePolicy(trace(
                "completed",
                plan,
                List.of("completed", "prepared", "prepared", "completed", "completed")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow failure policy contains an invalid node terminal state");

        assertThatThrownBy(() -> compiler.validateFailurePolicy(trace(
                "failed",
                plan,
                List.of("prepared", "prepared", "prepared", "failed", "completed")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow failure policy contains an invalid node terminal state");
    }

    @Test
    void rejectsSkippedOrFailedContributionNodesEvenWhenTheRunStatusLooksValid() {
        FlowExecutionPlanResponse plan = compiler.compile(
                snapshot(Map.of("audience", "product teams"))
        ).plan();

        assertThatThrownBy(() -> compiler.validateFailurePolicy(trace(
                "completed",
                plan,
                List.of("prepared", "skipped", "prepared", "completed", "completed")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow failure policy contains an invalid node terminal state");

        assertThatThrownBy(() -> compiler.validateFailurePolicy(trace(
                "failed",
                plan,
                List.of("failed", "prepared", "prepared", "failed", "skipped")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow failure policy contains an invalid node terminal state");
    }

    @Test
    void rejectsAPlanThatClaimsRetriesInTheSinglePassRuntime() {
        FlowExecutionCompiler.Compilation compilation = compiler.compile(
                snapshot(Map.of("audience", "product teams"))
        );
        FlowExecutionPlanResponse retryingPlan = new FlowExecutionPlanResponse(
                compilation.plan().version(),
                compilation.plan().scheduling(),
                compilation.plan().steps(),
                new FlowExecutionFailurePolicyResponse(
                        "flow-failure-policy-v1",
                        "stop-run",
                        "skip",
                        "fixed",
                        2
                )
        );

        assertThatThrownBy(() -> compiler.validateFailurePolicy(trace(
                "completed",
                retryingPlan,
                List.of("prepared", "prepared", "prepared", "completed", "completed")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported Flow failure policy");
    }

    @Test
    void rejectsAnInputContractThatActivatesPersistedArtifactsEarly() {
        FlowExecutionCompiler.Compilation compilation = compiler.compile(
                snapshot(Map.of("audience", "product teams"))
        );
        FlowExecutionPlanResponse invalidPlan = new FlowExecutionPlanResponse(
                compilation.plan().version(),
                compilation.plan().scheduling(),
                compilation.plan().steps(),
                compilation.plan().failurePolicy(),
                new com.flowforge.ai.dto.FlowInputResolutionContractResponse(
                        "flow-input-resolution-v1",
                        "persisted-artifact",
                        List.of("compiled-reference", "persisted-artifact"),
                        true,
                        "node-sequential-runtime"
                )
        );

        assertThatThrownBy(() -> compiler.validateFailurePolicy(trace(
                "completed",
                invalidPlan,
                List.of("prepared", "prepared", "prepared", "completed", "completed")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported Flow input resolution contract");
    }

    @Test
    void rejectsDuplicateArtifactsAtTheProviderBoundary() {
        FlowExecutionPlanResponse plan = compiler.compile(snapshot(Map.of("audience", "product teams"))).plan();
        FlowExecutionStepResponse providerStep = providerStep(plan);
        List<FlowArtifactContractResponse> inputs = providerStep.providerInputArtifacts();
        FlowExecutionPlanResponse invalidPlan = replaceProviderStep(
                plan,
                providerStep.dependsOnNodeIds(),
                List.of(inputs.get(0), inputs.get(1), inputs.get(1), inputs.get(3))
        );

        assertThatThrownBy(() -> compiler.validateProviderInputArtifacts(invalidPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow Provider input artifact contract is invalid");
    }

    @Test
    void rejectsArtifactsThatDoNotBelongToAnUpstreamContributionNode() {
        FlowExecutionPlanResponse plan = compiler.compile(snapshot(Map.of("audience", "product teams"))).plan();
        FlowExecutionStepResponse providerStep = providerStep(plan);
        List<FlowArtifactContractResponse> inputs = providerStep.providerInputArtifacts();
        FlowExecutionPlanResponse invalidPlan = replaceProviderStep(
                plan,
                providerStep.dependsOnNodeIds(),
                List.of(
                        inputs.get(0),
                        inputs.get(1),
                        new FlowArtifactContractResponse(
                                "node:unknown:context-contribution",
                                "context-contribution",
                                "node-artifact"
                        ),
                        inputs.get(3)
                )
        );

        assertThatThrownBy(() -> compiler.validateProviderInputArtifacts(invalidPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow Provider input artifact contract is invalid");
    }

    @Test
    void rejectsProviderDependenciesThatDoNotMatchTheDeclaredFanIn() {
        FlowExecutionPlanResponse plan = compiler.compile(snapshot(Map.of("audience", "product teams"))).plan();
        FlowExecutionStepResponse providerStep = providerStep(plan);
        FlowExecutionPlanResponse invalidPlan = replaceProviderStep(
                plan,
                List.of("input-1", "prompt-1"),
                providerStep.providerInputArtifacts()
        );

        assertThatThrownBy(() -> compiler.validateProviderInputArtifacts(invalidPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow Provider input dependencies do not match artifacts");
    }

    private FlowExecutionStepResponse providerStep(FlowExecutionPlanResponse plan) {
        return plan.steps().stream()
                .filter(FlowExecutionStepResponse::providerBoundary)
                .findFirst()
                .orElseThrow();
    }

    private FlowExecutionPlanResponse replaceProviderStep(
            FlowExecutionPlanResponse plan,
            List<String> dependencies,
            List<FlowArtifactContractResponse> providerInputs
    ) {
        List<FlowExecutionStepResponse> steps = plan.steps().stream()
                .map(step -> step.providerBoundary()
                        ? new FlowExecutionStepResponse(
                                step.sequence(),
                                step.nodeId(),
                                step.nodeType(),
                                step.title(),
                                step.operation(),
                                dependencies,
                                true,
                                step.inputArtifact(),
                                providerInputs,
                                step.inputResolution(),
                                step.outputArtifact()
                        )
                        : step)
                .toList();
        return new FlowExecutionPlanResponse(
                plan.version(),
                plan.scheduling(),
                steps,
                plan.failurePolicy(),
                plan.inputResolutionContract()
        );
    }

    private FlowRunTraceResponse trace(
            String status,
            FlowExecutionPlanResponse plan,
            List<String> nodeStatuses
    ) {
        List<FlowNodeRunTraceResponse> nodes = plan.steps().stream()
                .map(step -> new FlowNodeRunTraceResponse(
                        step.nodeId(),
                        step.nodeType(),
                        step.title(),
                        nodeStatuses.get(step.sequence() - 1),
                        "compiled",
                        null,
                        null
                ))
                .toList();
        return new FlowRunTraceResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                "single-pass",
                1,
                "flow-compiler-v1",
                "fingerprint",
                "compiled-flow",
                null,
                plan,
                nodes
        );
    }

    private FlowRunSnapshotResponse snapshot(Map<String, String> variableValues) {
        return new FlowRunSnapshotResponse(
                UUID.randomUUID(),
                "Launch workspace",
                "Prepare a calm product launch",
                List.of(
                        node("input-1", "input", "Intent", "Prepare a calm product launch"),
                        node("input-2", "input", "Audience context", "Prioritize {audience}."),
                        node("prompt-1", "prompt", "Launch prompt", "Create a launch plan for {audience}."),
                        node("ai-task-1", "ai-task", "AI Execution", "Make tradeoffs explicit for {audience}."),
                        node("output-1", "output", "Structured Result", "End with next actions for {audience}.")
                ),
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 7, 29, 10, 0),
                "Ship a focused first release.",
                variableValues
        );
    }

    private FlowNodeDto node(String id, String type, String title, String content) {
        return new FlowNodeDto(id, type, title, title + " description", content, null, null);
    }
}
