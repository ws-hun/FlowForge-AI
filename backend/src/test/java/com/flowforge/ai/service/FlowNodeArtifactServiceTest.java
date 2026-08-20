package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowArtifactContractResponse;
import com.flowforge.ai.dto.FlowExecutionPlanResponse;
import com.flowforge.ai.dto.FlowExecutionStepResponse;
import com.flowforge.ai.dto.FlowNodeArtifactResponse;
import com.flowforge.ai.dto.FlowNodeRunTraceResponse;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.dto.FlowRunTraceResponse;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.entity.FlowProviderAttempt;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.repository.FlowNodeArtifactRepository;
import com.flowforge.ai.repository.FlowProviderAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowNodeArtifactServiceTest {

    @Mock
    private FlowNodeArtifactRepository artifactRepository;

    @Mock
    private FlowProviderAttemptRepository providerAttemptRepository;

    private final FlowExecutionCompiler compiler = new FlowExecutionCompiler();
    private FlowNodeArtifactService artifactService;

    @BeforeEach
    void setUp() {
        artifactService = new FlowNodeArtifactService(
                artifactRepository,
                providerAttemptRepository,
                compiler
        );
    }

    @Test
    void persistsAddressablePayloadsInNodeOrder() {
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        Task task = completedTask(taskId);
        OpenAiTaskResult result = new OpenAiTaskResult(
                "Focused summary",
                "Detailed result",
                "{}",
                "deepseek",
                "deepseek-chat",
                120,
                80,
                200
        );
        FlowRunTraceResponse trace = trace(flowId, List.of(
                node(
                        "input-1",
                        "input",
                        "Context",
                        "context-contribution",
                        "Product context",
                        compiler.fingerprint("Product context")
                ),
                node(
                        "ai-task-1",
                        "ai-task",
                        "Provider result",
                        "provider-result",
                        "Execution guidance",
                        compiler.fingerprint("Focused summary\nDetailed result")
                ),
                node(
                        "output-1",
                        "output",
                        "Result document",
                        "result-document",
                        "Delivery focus",
                        compiler.fingerprint("Detailed result")
                )
        ));
        when(artifactRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerAttemptRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<FlowNodeArtifact> saved = artifactService.persist(task, snapshot(flowId), trace, result);

        assertThat(saved).extracting(FlowNodeArtifact::getSequenceNumber).containsExactly(1, 2, 3);
        assertThat(saved).extracting(FlowNodeArtifact::getArtifactKey)
                .containsExactly(
                        "node:input-1:context-contribution",
                        "node:ai-task-1:provider-result",
                        "node:output-1:result-document"
                );
        assertThat(saved).extracting(FlowNodeArtifact::getPayload)
                .containsExactly("Product context", "Focused summary\nDetailed result", "Detailed result");
        assertThat(saved).extracting(FlowNodeArtifact::getMediaType)
                .containsExactly("text/plain", "text/markdown", "text/markdown");
        assertThat(saved).extracting(FlowNodeArtifact::getInputArtifactKey)
                .containsExactly(
                        "flow:objective",
                        "node:input-1:context-contribution",
                        "node:ai-task-1:provider-result"
                );
        assertThat(saved).extracting(FlowNodeArtifact::getInputArtifactStorage)
                .containsExactly("flow-snapshot", "node-artifact", "node-artifact");
        assertThat(saved).extracting(FlowNodeArtifact::getInputArtifactState)
                .containsExactly("materialized", "materialized", "materialized");
        assertThat(saved).extracting(FlowNodeArtifact::getInputResolution)
                .containsOnly("compiled-reference");
        assertThat(saved).extracting(FlowNodeArtifact::getInputContentFingerprint)
                .containsExactly(
                        compiler.fingerprint("Flow objective"),
                        compiler.fingerprint("Product context"),
                        compiler.fingerprint("Focused summary\nDetailed result")
                );
        assertThat(saved).extracting(FlowNodeArtifact::getProviderCallStatus)
                .containsExactly(null, "completed", null);
        assertThat(saved).extracting(FlowNodeArtifact::getProviderName)
                .containsExactly(null, "deepseek", null);
        assertThat(saved).extracting(FlowNodeArtifact::getProviderModel)
                .containsExactly(null, "deepseek-chat", null);
        assertThat(saved).extracting(FlowNodeArtifact::getProviderTotalTokens)
                .containsExactly(null, 200, null);
        assertThat(saved).extracting(FlowNodeArtifact::getProviderDurationMs)
                .containsExactly(null, 840L, null);
        assertThat(saved).extracting(FlowNodeArtifact::getProviderErrorMessage)
                .containsOnlyNulls();
        assertThat(saved).allSatisfy(artifact -> {
            assertThat(artifact.getTaskId()).isEqualTo(taskId);
            assertThat(artifact.getFlowId()).isEqualTo(flowId);
            assertThat(artifact.getState()).isEqualTo("materialized");
            assertThat(artifact.getContentFingerprint())
                    .isEqualTo(compiler.fingerprint(artifact.getPayload()));
        });
        verify(artifactRepository).saveAll(saved);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<FlowProviderAttempt>> attemptCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(providerAttemptRepository).saveAll(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getArtifactId()).isEqualTo(saved.get(1).getId());
            assertThat(attempt.getAttemptNumber()).isEqualTo(1);
            assertThat(attempt.getTriggerType()).isEqualTo("initial");
            assertThat(attempt.getPreviousAttemptId()).isNull();
            assertThat(attempt.getStatus()).isEqualTo("completed");
            assertThat(attempt.getProvider()).isEqualTo("deepseek");
            assertThat(attempt.getModel()).isEqualTo("deepseek-chat");
            assertThat(attempt.getInputTokens()).isEqualTo(120);
            assertThat(attempt.getOutputTokens()).isEqualTo(80);
            assertThat(attempt.getTotalTokens()).isEqualTo(200);
            assertThat(attempt.getDurationMs()).isEqualTo(840L);
            assertThat(attempt.getErrorMessage()).isNull();
        });
    }

    @Test
    void rejectsMaterializedPayloadWhenTheTraceFingerprintDoesNotMatch() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        FlowRunTraceResponse trace = trace(UUID.randomUUID(), List.of(node(
                "input-1",
                "input",
                "Context",
                "context-contribution",
                "Product context",
                "incorrect-fingerprint"
        )));

        assertThatThrownBy(() -> artifactService.persist(task, snapshot(trace.flowId()), trace, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow node artifact fingerprint does not match its payload");

        verifyNoInteractions(artifactRepository, providerAttemptRepository);
    }

    @Test
    void persistsFailedAndSkippedArtifactsWithoutInventingPayloads() {
        UUID taskId = UUID.randomUUID();
        Task task = failedTask(taskId);
        UUID flowId = UUID.randomUUID();
        FlowRunTraceResponse trace = trace(flowId, List.of(
                node(
                        "input-1",
                        "input",
                        "Context",
                        "context-contribution",
                        "Product context",
                        "materialized",
                        compiler.fingerprint("Product context")
                ),
                node(
                        "ai-task-1",
                        "ai-task",
                        "Provider result",
                        "provider-result",
                        "Execution guidance",
                        "failed",
                        null
                ),
                node(
                        "output-1",
                        "output",
                        "Result document",
                        "result-document",
                        "Delivery focus",
                        "skipped",
                        null
                )
        ));
        when(artifactRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerAttemptRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<FlowNodeArtifact> saved = artifactService.persist(task, snapshot(flowId), trace, null);

        assertThat(saved).extracting(FlowNodeArtifact::getState)
                .containsExactly("materialized", "failed", "skipped");
        assertThat(saved).extracting(FlowNodeArtifact::getPayload)
                .containsExactly("Product context", null, null);
        assertThat(saved).extracting(FlowNodeArtifact::getContentFingerprint)
                .containsExactly(compiler.fingerprint("Product context"), null, null);
        assertThat(saved).extracting(FlowNodeArtifact::getInputArtifactKey)
                .containsExactly(
                        "flow:objective",
                        "node:input-1:context-contribution",
                        "node:ai-task-1:provider-result"
                );
        assertThat(saved).extracting(FlowNodeArtifact::getInputArtifactState)
                .containsExactly("materialized", "materialized", "failed");
        assertThat(saved).extracting(FlowNodeArtifact::getInputContentFingerprint)
                .containsExactly(
                        compiler.fingerprint("Flow objective"),
                        compiler.fingerprint("Product context"),
                        null
                );
        assertThat(saved).extracting(FlowNodeArtifact::getProviderCallStatus)
                .containsExactly(null, "failed", null);
        assertThat(saved).extracting(FlowNodeArtifact::getProviderName)
                .containsExactly(null, "deepseek", null);
        assertThat(saved).extracting(FlowNodeArtifact::getProviderDurationMs)
                .containsExactly(null, 420L, null);
        assertThat(saved).extracting(FlowNodeArtifact::getProviderErrorMessage)
                .containsExactly(null, "AI API error: provider unavailable", null);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<FlowProviderAttempt>> attemptCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(providerAttemptRepository).saveAll(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getArtifactId()).isEqualTo(saved.get(1).getId());
            assertThat(attempt.getAttemptNumber()).isEqualTo(1);
            assertThat(attempt.getTriggerType()).isEqualTo("initial");
            assertThat(attempt.getStatus()).isEqualTo("failed");
            assertThat(attempt.getDurationMs()).isEqualTo(420L);
            assertThat(attempt.getErrorMessage()).isEqualTo("AI API error: provider unavailable");
        });
    }

    @Test
    void rejectsLineageWhenPlanOrderDoesNotMatchNodeTrace() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        UUID flowId = UUID.randomUUID();
        FlowRunTraceResponse trace = trace(flowId, List.of(node(
                "input-1",
                "input",
                "Context",
                "context-contribution",
                "Product context",
                compiler.fingerprint("Product context")
        )));
        FlowExecutionStepResponse step = trace.executionPlan().steps().get(0);
        FlowExecutionStepResponse mismatchedStep = new FlowExecutionStepResponse(
                step.sequence(),
                "input-other",
                step.nodeType(),
                step.title(),
                step.operation(),
                step.dependsOnNodeIds(),
                step.providerBoundary(),
                step.inputArtifact(),
                step.inputResolution(),
                step.outputArtifact()
        );

        assertThatThrownBy(() -> artifactService.persist(
                task,
                snapshot(flowId),
                withSteps(trace, List.of(mismatchedStep)),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow artifact plan order does not match node trace");

        verifyNoInteractions(artifactRepository, providerAttemptRepository);
    }

    @Test
    void rejectsLineageWhenOutputContractDoesNotMatchNodeTrace() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        UUID flowId = UUID.randomUUID();
        FlowRunTraceResponse trace = trace(flowId, List.of(node(
                "input-1",
                "input",
                "Context",
                "context-contribution",
                "Product context",
                compiler.fingerprint("Product context")
        )));
        FlowExecutionStepResponse step = trace.executionPlan().steps().get(0);
        FlowExecutionStepResponse mismatchedStep = new FlowExecutionStepResponse(
                step.sequence(),
                step.nodeId(),
                step.nodeType(),
                step.title(),
                step.operation(),
                step.dependsOnNodeIds(),
                step.providerBoundary(),
                step.inputArtifact(),
                step.inputResolution(),
                new FlowArtifactContractResponse(
                        "node:input-1:unexpected-output",
                        "unexpected-output",
                        "node-artifact"
                )
        );

        assertThatThrownBy(() -> artifactService.persist(
                task,
                snapshot(flowId),
                withSteps(trace, List.of(mismatchedStep)),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow artifact output contract does not match node trace");

        verifyNoInteractions(artifactRepository, providerAttemptRepository);
    }

    @Test
    void rejectsLineageWhenInputDoesNotResolveToPriorNodeOutput() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        UUID flowId = UUID.randomUUID();
        FlowRunTraceResponse trace = trace(flowId, List.of(
                node(
                        "input-1",
                        "input",
                        "Context",
                        "context-contribution",
                        "Product context",
                        compiler.fingerprint("Product context")
                ),
                node(
                        "prompt-1",
                        "prompt",
                        "Instructions",
                        "instruction-contribution",
                        "Write a concise plan",
                        compiler.fingerprint("Write a concise plan")
                )
        ));
        FlowExecutionStepResponse firstStep = trace.executionPlan().steps().get(0);
        FlowExecutionStepResponse secondStep = trace.executionPlan().steps().get(1);
        FlowExecutionStepResponse unresolvedStep = new FlowExecutionStepResponse(
                secondStep.sequence(),
                secondStep.nodeId(),
                secondStep.nodeType(),
                secondStep.title(),
                secondStep.operation(),
                secondStep.dependsOnNodeIds(),
                secondStep.providerBoundary(),
                new FlowArtifactContractResponse(
                        "node:missing:context-contribution",
                        "context-contribution",
                        "node-artifact"
                ),
                secondStep.inputResolution(),
                secondStep.outputArtifact()
        );

        assertThatThrownBy(() -> artifactService.persist(
                task,
                snapshot(flowId),
                withSteps(trace, List.of(firstStep, unresolvedStep)),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow artifact input does not resolve to a prior node output");

        verifyNoInteractions(artifactRepository, providerAttemptRepository);
    }

    @Test
    void rejectsProviderProvenanceOnANonAiTaskBoundary() {
        UUID flowId = UUID.randomUUID();
        FlowRunTraceResponse trace = trace(flowId, List.of(node(
                "input-1",
                "input",
                "Context",
                "context-contribution",
                "Product context",
                compiler.fingerprint("Product context")
        )));
        FlowExecutionStepResponse step = trace.executionPlan().steps().get(0);
        FlowExecutionStepResponse falseBoundary = new FlowExecutionStepResponse(
                step.sequence(),
                step.nodeId(),
                step.nodeType(),
                step.title(),
                step.operation(),
                step.dependsOnNodeIds(),
                true,
                step.inputArtifact(),
                step.inputResolution(),
                step.outputArtifact()
        );

        assertThatThrownBy(() -> artifactService.persist(
                completedTask(UUID.randomUUID()),
                snapshot(flowId),
                withSteps(trace, List.of(falseBoundary)),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow Provider boundary must produce an AI Task result artifact");

        verifyNoInteractions(artifactRepository, providerAttemptRepository);
    }

    @Test
    void rejectsAnAiTaskArtifactWithoutAProviderBoundary() {
        UUID flowId = UUID.randomUUID();
        OpenAiTaskResult result = new OpenAiTaskResult(
                "Focused summary",
                "Detailed result",
                "{}",
                "deepseek",
                "deepseek-chat",
                120,
                80,
                200
        );
        FlowRunTraceResponse trace = trace(flowId, List.of(node(
                "ai-task-1",
                "ai-task",
                "Provider result",
                "provider-result",
                "Execution guidance",
                compiler.fingerprint("Focused summary\nDetailed result")
        )));
        FlowExecutionStepResponse step = trace.executionPlan().steps().get(0);
        FlowExecutionStepResponse missingBoundary = new FlowExecutionStepResponse(
                step.sequence(),
                step.nodeId(),
                step.nodeType(),
                step.title(),
                step.operation(),
                step.dependsOnNodeIds(),
                false,
                step.inputArtifact(),
                step.inputResolution(),
                step.outputArtifact()
        );

        assertThatThrownBy(() -> artifactService.persist(
                completedTask(UUID.randomUUID()),
                snapshot(flowId),
                withSteps(trace, List.of(missingBoundary)),
                result
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow AI Task artifact requires a Provider boundary");

        verifyNoInteractions(artifactRepository, providerAttemptRepository);
    }

    @Test
    void rejectsProviderProvenanceWhenTaskAndTraceStatusDiffer() {
        UUID flowId = UUID.randomUUID();
        OpenAiTaskResult result = new OpenAiTaskResult(
                "Focused summary",
                "Detailed result",
                "{}",
                "deepseek",
                "deepseek-chat",
                120,
                80,
                200
        );
        FlowRunTraceResponse trace = trace(flowId, List.of(node(
                "ai-task-1",
                "ai-task",
                "Provider result",
                "provider-result",
                "Execution guidance",
                compiler.fingerprint("Focused summary\nDetailed result")
        )));

        assertThatThrownBy(() -> artifactService.persist(
                failedTask(UUID.randomUUID()),
                snapshot(flowId),
                trace,
                result
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow Provider provenance does not match the run status");

        verifyNoInteractions(artifactRepository, providerAttemptRepository);
    }

    private FlowRunTraceResponse withSteps(
            FlowRunTraceResponse trace,
            List<FlowExecutionStepResponse> steps
    ) {
        return new FlowRunTraceResponse(
                trace.runId(),
                trace.flowId(),
                trace.status(),
                trace.executionMode(),
                trace.providerCallCount(),
                trace.compilerVersion(),
                trace.executionInputFingerprint(),
                trace.inputSource(),
                trace.replayedFromTaskId(),
                new FlowExecutionPlanResponse(trace.executionPlan().version(), "linear", steps),
                trace.nodes()
        );
    }

    private FlowRunTraceResponse trace(UUID flowId, List<FlowNodeRunTraceResponse> nodes) {
        List<FlowExecutionStepResponse> steps = new ArrayList<>();
        FlowArtifactContractResponse previousArtifact = new FlowArtifactContractResponse(
                "flow:objective",
                "flow-objective",
                "flow-snapshot"
        );
        String previousNodeId = null;
        for (int index = 0; index < nodes.size(); index++) {
            FlowNodeRunTraceResponse node = nodes.get(index);
            FlowArtifactContractResponse outputArtifact = new FlowArtifactContractResponse(
                    node.outputArtifact().key(),
                    node.outputArtifact().type(),
                    node.outputArtifact().storage()
            );
            steps.add(new FlowExecutionStepResponse(
                    index + 1,
                    node.nodeId(),
                    node.nodeType(),
                    node.title(),
                    operation(node.nodeType()),
                    previousNodeId == null ? List.of() : List.of(previousNodeId),
                    "ai-task".equals(node.nodeType()),
                    previousArtifact,
                    "compiled-reference",
                    outputArtifact
            ));
            previousArtifact = outputArtifact;
            previousNodeId = node.nodeId();
        }
        String runStatus = nodes.stream()
                .anyMatch(node -> "failed".equals(node.outputArtifact().state()))
                ? Task.STATUS_FAILED
                : Task.STATUS_COMPLETED;
        return new FlowRunTraceResponse(
                UUID.randomUUID(),
                flowId,
                runStatus,
                "single-pass",
                1,
                "flow-compiler-v1",
                "provider-input-fingerprint",
                "compiled-flow",
                null,
                new FlowExecutionPlanResponse("flow-plan-v4", "linear", steps),
                nodes
        );
    }

    private Task completedTask(UUID taskId) {
        return Task.builder()
                .id(taskId)
                .provider("deepseek")
                .model("deepseek-chat")
                .inputTokens(120)
                .outputTokens(80)
                .totalTokens(200)
                .durationMs(840L)
                .status(Task.STATUS_COMPLETED)
                .build();
    }

    private Task failedTask(UUID taskId) {
        return Task.builder()
                .id(taskId)
                .provider("deepseek")
                .model("deepseek-chat")
                .durationMs(420L)
                .status(Task.STATUS_FAILED)
                .errorMessage("AI API error: provider unavailable")
                .build();
    }

    private FlowRunSnapshotResponse snapshot(UUID flowId) {
        return new FlowRunSnapshotResponse(
                flowId,
                "Lineage Flow",
                "Flow objective",
                List.of(),
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 8, 18, 10, 0),
                "",
                Map.of()
        );
    }

    private String operation(String nodeType) {
        return switch (nodeType) {
            case "input" -> "supply-context";
            case "prompt" -> "supply-instructions";
            case "ai-task" -> "invoke-provider";
            case "output" -> "define-delivery";
            default -> throw new IllegalArgumentException("Unsupported node type");
        };
    }

    private FlowNodeRunTraceResponse node(
            String nodeId,
            String nodeType,
            String title,
            String artifactType,
            String compiledContent,
            String fingerprint
    ) {
        return node(
                nodeId,
                nodeType,
                title,
                artifactType,
                compiledContent,
                "materialized",
                fingerprint
        );
    }

    private FlowNodeRunTraceResponse node(
            String nodeId,
            String nodeType,
            String title,
            String artifactType,
            String compiledContent,
            String artifactState,
            String fingerprint
    ) {
        return new FlowNodeRunTraceResponse(
                nodeId,
                nodeType,
                title,
                "materialized",
                compiledContent,
                null,
                null,
                new FlowNodeArtifactResponse(
                        "node:" + nodeId + ":" + artifactType,
                        artifactType,
                        "node-artifact",
                        artifactState,
                        fingerprint
                )
        );
    }
}
