package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageEntryResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageResponse;
import com.flowforge.ai.dto.FlowNodeArtifactSummaryResponse;
import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.entity.FlowProviderInputReference;
import com.flowforge.ai.entity.FlowProviderAttempt;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.FlowNodeArtifactRepository;
import com.flowforge.ai.repository.FlowProviderInputReferenceRepository;
import com.flowforge.ai.repository.FlowProviderAttemptRepository;
import com.flowforge.ai.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowNodeArtifactQueryServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private FlowNodeArtifactRepository artifactRepository;

    @Mock
    private FlowProviderInputReferenceRepository providerInputReferenceRepository;

    @Mock
    private FlowProviderAttemptRepository providerAttemptRepository;

    private FlowNodeArtifactQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new FlowNodeArtifactQueryService(
                taskRepository,
                artifactRepository,
                providerInputReferenceRepository,
                providerAttemptRepository,
                new FlowProviderAttemptPolicy()
        );
    }

    @Test
    void listsArtifactMetadataInPersistedNodeOrder() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact input = artifact(taskId, "input-1", 1, "context-contribution", "Context");
        FlowNodeArtifact output = artifact(taskId, "output-1", 2, "result-document", "Result");
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdOrderBySequenceNumberAsc(taskId))
                .thenReturn(List.of(input, output));

        List<FlowNodeArtifactSummaryResponse> artifacts = queryService.listForTask(taskId);

        assertThat(artifacts).extracting(FlowNodeArtifactSummaryResponse::sequence).containsExactly(1, 2);
        assertThat(artifacts).extracting(FlowNodeArtifactSummaryResponse::artifactKey)
                .containsExactly(input.getArtifactKey(), output.getArtifactKey());
        assertThat(artifacts).allSatisfy(artifact -> {
            assertThat(artifact.taskId()).isEqualTo(taskId);
            assertThat(artifact.state()).isEqualTo("materialized");
            assertThat(artifact.contentFingerprint()).hasSize(64);
        });
    }

    @Test
    void usesTheLatestAttemptForCompactArtifactSummaries() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact artifact = artifact(taskId, "ai-task-1", 1, "provider-result", "Result");
        FlowProviderAttempt initial = failedAttempt(artifact.getId());
        FlowProviderAttempt retry = FlowProviderAttempt.builder()
                .id(UUID.randomUUID())
                .artifactId(artifact.getId())
                .attemptNumber(2)
                .triggerType("automatic-retry")
                .previousAttemptId(initial.getId())
                .status("completed")
                .provider("openai")
                .model("gpt-4.1")
                .totalTokens(260)
                .durationMs(1020L)
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, 3))
                .build();
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdOrderBySequenceNumberAsc(taskId))
                .thenReturn(List.of(artifact));
        when(providerAttemptRepository.findByArtifactIdInOrderByArtifactIdAscAttemptNumberAsc(
                List.of(artifact.getId())
        )).thenReturn(List.of(initial, retry));

        FlowNodeArtifactSummaryResponse response = queryService.listForTask(taskId).get(0);

        assertThat(response.providerCall().provider()).isEqualTo("openai");
        assertThat(response.providerCall().model()).isEqualTo("gpt-4.1");
        assertThat(response.providerCall().totalTokens()).isEqualTo(260);
        assertThat(response.providerCall().durationMs()).isEqualTo(1020L);
    }

    @Test
    void returnsOneAddressableArtifactPayload() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact artifact = artifact(taskId, "ai-task-1", 2, "provider-result", "Summary\nResult");
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdAndArtifactKey(taskId, artifact.getArtifactKey()))
                .thenReturn(Optional.of(artifact));
        FlowProviderAttempt attempt = attempt(artifact.getId());
        when(providerAttemptRepository.findByArtifactIdOrderByAttemptNumberAsc(artifact.getId()))
                .thenReturn(List.of(attempt));
        when(providerInputReferenceRepository.findByProviderArtifactIdOrderByInputOrderAsc(artifact.getId()))
                .thenReturn(List.of(
                        providerInputReference(artifact, 1, "flow:objective", null, null),
                        providerInputReference(
                                artifact,
                                2,
                                "node:input-1:context-contribution",
                                UUID.randomUUID(),
                                "input-1"
                        )
                ));

        FlowNodeArtifactDetailResponse response = queryService.getForTask(taskId, artifact.getArtifactKey());

        assertThat(response.artifactKey()).isEqualTo(artifact.getArtifactKey());
        assertThat(response.payload()).isEqualTo("Summary\nResult");
        assertThat(response.mediaType()).isEqualTo("text/markdown");
        assertThat(response.inputArtifactKey()).isEqualTo("node:input-1:context-contribution");
        assertThat(response.inputArtifactStorage()).isEqualTo("node-artifact");
        assertThat(response.inputArtifactState()).isEqualTo("materialized");
        assertThat(response.inputResolution()).isEqualTo("compiled-reference");
        assertThat(response.inputContentFingerprint()).hasSize(64);
        assertThat(response.providerCall()).isNotNull();
        assertThat(response.providerCall().status()).isEqualTo("completed");
        assertThat(response.providerCall().provider()).isEqualTo("deepseek");
        assertThat(response.providerCall().model()).isEqualTo("deepseek-chat");
        assertThat(response.providerCall().inputTokens()).isEqualTo(120);
        assertThat(response.providerCall().outputTokens()).isEqualTo(80);
        assertThat(response.providerCall().totalTokens()).isEqualTo(200);
        assertThat(response.providerCall().durationMs()).isEqualTo(840L);
        assertThat(response.providerCall().errorMessage()).isNull();
        assertThat(response.providerInputReferences())
                .extracting(reference -> reference.artifactKey())
                .containsExactly("flow:objective", "node:input-1:context-contribution");
        assertThat(response.providerInputReferences().get(0).sourceArtifactId()).isNull();
        assertThat(response.providerInputReferences().get(1).sourceNodeId()).isEqualTo("input-1");
        assertThat(response.providerAttemptPolicy().version())
                .isEqualTo("flow-provider-attempt-policy-v1");
        assertThat(response.providerAttemptPolicy().currentState()).isEqualTo("completed");
        assertThat(response.providerAttemptPolicy().recordedAttempts()).isEqualTo(1);
        assertThat(response.providerAttemptPolicy().automaticRetryEnabled()).isFalse();
        assertThat(response.providerAttemptPolicy().sameArtifactRecoveryEnabled()).isFalse();
        assertThat(response.providerAttemptPolicy().failedRunRecoveryAction()).isEqualTo("none");
        assertThat(response.providerAttempts()).singleElement().satisfies(providerAttempt -> {
            assertThat(providerAttempt.id()).isEqualTo(attempt.getId());
            assertThat(providerAttempt.attemptNumber()).isEqualTo(1);
            assertThat(providerAttempt.triggerType()).isEqualTo("initial");
            assertThat(providerAttempt.previousAttemptId()).isNull();
            assertThat(providerAttempt.status()).isEqualTo("completed");
            assertThat(providerAttempt.provider()).isEqualTo("deepseek");
            assertThat(providerAttempt.model()).isEqualTo("deepseek-chat");
            assertThat(providerAttempt.totalTokens()).isEqualTo(200);
            assertThat(providerAttempt.durationMs()).isEqualTo(840L);
            assertThat(providerAttempt.errorMessage()).isNull();
        });
    }

    @Test
    void usesTheLatestPersistedAttemptAsTheDetailCallSummary() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact artifact = artifact(taskId, "ai-task-1", 2, "provider-result", "Summary\nResult");
        FlowProviderAttempt initial = failedAttempt(artifact.getId());
        FlowProviderAttempt recovery = FlowProviderAttempt.builder()
                .id(UUID.randomUUID())
                .artifactId(artifact.getId())
                .attemptNumber(2)
                .triggerType("manual-recovery")
                .previousAttemptId(initial.getId())
                .status("completed")
                .provider("openai")
                .model("gpt-4.1")
                .inputTokens(160)
                .outputTokens(90)
                .totalTokens(250)
                .durationMs(960L)
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, 3))
                .build();
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdAndArtifactKey(taskId, artifact.getArtifactKey()))
                .thenReturn(Optional.of(artifact));
        when(providerAttemptRepository.findByArtifactIdOrderByAttemptNumberAsc(artifact.getId()))
                .thenReturn(List.of(initial, recovery));

        FlowNodeArtifactDetailResponse response = queryService.getForTask(taskId, artifact.getArtifactKey());

        assertThat(response.providerAttempts())
                .extracting(providerAttempt -> providerAttempt.attemptNumber())
                .containsExactly(1, 2);
        assertThat(response.providerCall().provider()).isEqualTo("openai");
        assertThat(response.providerCall().model()).isEqualTo("gpt-4.1");
        assertThat(response.providerCall().totalTokens()).isEqualTo(250);
        assertThat(response.providerCall().durationMs()).isEqualTo(960L);
        assertThat(response.providerAttemptPolicy().currentState()).isEqualTo("completed");
        assertThat(response.providerAttemptPolicy().recordedAttempts()).isEqualTo(2);
    }

    @Test
    void rejectsArtifactQueriesForMissingTasks() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.existsById(taskId)).thenReturn(false);

        assertThatThrownBy(() -> queryService.listForTask(taskId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Task run not found");

        verifyNoInteractions(artifactRepository);
    }

    @Test
    void rejectsUnknownArtifactKeysWithoutFallingBackToTraceContent() {
        UUID taskId = UUID.randomUUID();
        String artifactKey = "node:legacy:provider-result";
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdAndArtifactKey(taskId, artifactKey)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getForTask(taskId, artifactKey))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Flow node artifact not found");
    }

    @Test
    void keepsLegacyArtifactsReadableWithoutInventingLineage() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact legacyArtifact = FlowNodeArtifact.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .flowId(UUID.randomUUID())
                .nodeId("input-legacy")
                .sequenceNumber(1)
                .artifactKey("node:input-legacy:context-contribution")
                .artifactType("context-contribution")
                .state("materialized")
                .mediaType("text/plain")
                .payload("Legacy context")
                .contentFingerprint("a".repeat(64))
                .createdAt(LocalDateTime.of(2026, 8, 17, 9, 0))
                .build();
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdOrderBySequenceNumberAsc(taskId))
                .thenReturn(List.of(legacyArtifact));
        when(artifactRepository.findByTaskIdAndArtifactKey(taskId, legacyArtifact.getArtifactKey()))
                .thenReturn(Optional.of(legacyArtifact));
        when(providerAttemptRepository.findByArtifactIdOrderByAttemptNumberAsc(legacyArtifact.getId()))
                .thenReturn(List.of());

        FlowNodeArtifactSummaryResponse summary = queryService.listForTask(taskId).get(0);
        FlowNodeArtifactDetailResponse detail = queryService.getForTask(taskId, legacyArtifact.getArtifactKey());

        assertThat(summary.inputArtifactKey()).isNull();
        assertThat(summary.inputArtifactType()).isNull();
        assertThat(summary.inputArtifactStorage()).isNull();
        assertThat(summary.inputArtifactState()).isNull();
        assertThat(summary.inputResolution()).isNull();
        assertThat(summary.inputContentFingerprint()).isNull();
        assertThat(summary.providerCall()).isNull();
        assertThat(detail.inputArtifactKey()).isNull();
        assertThat(detail.inputArtifactType()).isNull();
        assertThat(detail.inputArtifactStorage()).isNull();
        assertThat(detail.inputArtifactState()).isNull();
        assertThat(detail.inputResolution()).isNull();
        assertThat(detail.inputContentFingerprint()).isNull();
        assertThat(detail.providerCall()).isNull();
        assertThat(detail.providerInputReferences()).isEmpty();
        assertThat(detail.providerAttempts()).isEmpty();
        assertThat(detail.providerAttemptPolicy().currentState()).isEqualTo("not-recorded");
        assertThat(detail.providerAttemptPolicy().recordedAttempts()).isZero();
    }

    @Test
    void returnsTheCompletePathFromArtifactToFlowSnapshot() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact input = artifact(taskId, "input-1", 1, "context-contribution", "Context");
        FlowNodeArtifact output = artifact(taskId, "output-1", 2, "result-document", "Result");
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdOrderBySequenceNumberAsc(taskId))
                .thenReturn(List.of(input, output));

        FlowNodeArtifactLineageResponse response = queryService.getLineageForTask(
                taskId,
                output.getArtifactKey()
        );

        assertThat(response.complete()).isTrue();
        assertThat(response.termination()).isEqualTo("flow-snapshot");
        assertThat(response.path()).extracting(FlowNodeArtifactLineageEntryResponse::artifactKey)
                .containsExactly(
                        output.getArtifactKey(),
                        input.getArtifactKey(),
                        "flow:objective"
                );
        assertThat(response.path()).extracting(FlowNodeArtifactLineageEntryResponse::persisted)
                .containsExactly(true, true, false);
        assertThat(response.path().get(2).storage()).isEqualTo("flow-snapshot");
        assertThat(response.path().get(2).contentFingerprint()).hasSize(64);
    }

    @Test
    void reportsABrokenChainWithoutPretendingTheLineageIsComplete() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact broken = FlowNodeArtifact.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .flowId(UUID.randomUUID())
                .nodeId("output-1")
                .sequenceNumber(2)
                .artifactKey("node:output-1:result-document")
                .artifactType("result-document")
                .state("materialized")
                .mediaType("text/markdown")
                .payload("Result")
                .contentFingerprint("a".repeat(64))
                .inputArtifactKey("node:missing:provider-result")
                .inputArtifactType("provider-result")
                .inputArtifactStorage("node-artifact")
                .inputArtifactState("materialized")
                .inputResolution("compiled-reference")
                .inputContentFingerprint("b".repeat(64))
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, 2))
                .build();
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdOrderBySequenceNumberAsc(taskId))
                .thenReturn(List.of(broken));

        FlowNodeArtifactLineageResponse response = queryService.getLineageForTask(
                taskId,
                broken.getArtifactKey()
        );

        assertThat(response.complete()).isFalse();
        assertThat(response.termination()).isEqualTo("missing-upstream-artifact");
        assertThat(response.path()).extracting(FlowNodeArtifactLineageEntryResponse::artifactKey)
                .containsExactly(broken.getArtifactKey());
    }

    @Test
    void stopsWhenPersistedLineageContainsACycle() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact first = lineageArtifact(
                taskId,
                "input-1",
                1,
                "node:output-1:result-document",
                "context-contribution"
        );
        FlowNodeArtifact second = lineageArtifact(
                taskId,
                "output-1",
                2,
                first.getArtifactKey(),
                "result-document"
        );
        FlowNodeArtifact cyclicFirst = lineageArtifact(
                taskId,
                "input-1",
                1,
                second.getArtifactKey(),
                "context-contribution"
        );
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdOrderBySequenceNumberAsc(taskId))
                .thenReturn(List.of(cyclicFirst, second));

        FlowNodeArtifactLineageResponse response = queryService.getLineageForTask(
                taskId,
                second.getArtifactKey()
        );

        assertThat(response.complete()).isFalse();
        assertThat(response.termination()).isEqualTo("cycle-detected");
        assertThat(response.path()).extracting(FlowNodeArtifactLineageEntryResponse::artifactKey)
                .containsExactly(second.getArtifactKey(), cyclicFirst.getArtifactKey());
    }

    private FlowNodeArtifact lineageArtifact(
            UUID taskId,
            String nodeId,
            int sequence,
            String inputArtifactKey,
            String artifactType
    ) {
        return FlowNodeArtifact.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .flowId(UUID.randomUUID())
                .nodeId(nodeId)
                .sequenceNumber(sequence)
                .artifactKey("node:" + nodeId + ":" + artifactType)
                .artifactType(artifactType)
                .state("materialized")
                .mediaType("text/plain")
                .payload("Payload")
                .contentFingerprint("a".repeat(64))
                .inputArtifactKey(inputArtifactKey)
                .inputArtifactType("provider-result")
                .inputArtifactStorage("node-artifact")
                .inputArtifactState("materialized")
                .inputResolution("compiled-reference")
                .inputContentFingerprint("b".repeat(64))
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, sequence))
                .build();
    }

    private FlowNodeArtifact artifact(
            UUID taskId,
            String nodeId,
            int sequence,
            String artifactType,
            String payload
    ) {
        return FlowNodeArtifact.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .flowId(UUID.randomUUID())
                .nodeId(nodeId)
                .sequenceNumber(sequence)
                .artifactKey("node:" + nodeId + ":" + artifactType)
                .artifactType(artifactType)
                .state("materialized")
                .mediaType(nodeId.startsWith("input") ? "text/plain" : "text/markdown")
                .payload(payload)
                .contentFingerprint("a".repeat(64))
                .inputArtifactKey(sequence == 1
                        ? "flow:objective"
                        : "node:input-1:context-contribution")
                .inputArtifactType(sequence == 1
                        ? "flow-objective"
                        : "context-contribution")
                .inputArtifactStorage(sequence == 1 ? "flow-snapshot" : "node-artifact")
                .inputArtifactState("materialized")
                .inputResolution("compiled-reference")
                .inputContentFingerprint("b".repeat(64))
                .providerCallStatus(nodeId.startsWith("ai-task") ? "completed" : null)
                .providerName(nodeId.startsWith("ai-task") ? "deepseek" : null)
                .providerModel(nodeId.startsWith("ai-task") ? "deepseek-chat" : null)
                .providerInputTokens(nodeId.startsWith("ai-task") ? 120 : null)
                .providerOutputTokens(nodeId.startsWith("ai-task") ? 80 : null)
                .providerTotalTokens(nodeId.startsWith("ai-task") ? 200 : null)
                .providerDurationMs(nodeId.startsWith("ai-task") ? 840L : null)
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, sequence))
                .build();
    }

    private FlowProviderAttempt attempt(UUID artifactId) {
        return FlowProviderAttempt.builder()
                .id(UUID.randomUUID())
                .artifactId(artifactId)
                .attemptNumber(1)
                .triggerType("initial")
                .status("completed")
                .provider("deepseek")
                .model("deepseek-chat")
                .inputTokens(120)
                .outputTokens(80)
                .totalTokens(200)
                .durationMs(840L)
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, 2))
                .build();
    }

    private FlowProviderInputReference providerInputReference(
            FlowNodeArtifact providerArtifact,
            int inputOrder,
            String artifactKey,
            UUID sourceArtifactId,
            String sourceNodeId
    ) {
        boolean objective = sourceArtifactId == null;
        return FlowProviderInputReference.builder()
                .id(UUID.randomUUID())
                .providerArtifactId(providerArtifact.getId())
                .inputOrder(inputOrder)
                .artifactKey(artifactKey)
                .artifactType(objective ? "flow-objective" : "context-contribution")
                .artifactStorage(objective ? "flow-snapshot" : "node-artifact")
                .artifactState("materialized")
                .inputResolution("compiled-reference")
                .contentFingerprint("c".repeat(64))
                .sourceArtifactId(sourceArtifactId)
                .sourceNodeId(sourceNodeId)
                .createdAt(LocalDateTime.of(2026, 8, 27, 10, inputOrder))
                .build();
    }

    private FlowProviderAttempt failedAttempt(UUID artifactId) {
        return FlowProviderAttempt.builder()
                .id(UUID.randomUUID())
                .artifactId(artifactId)
                .attemptNumber(1)
                .triggerType("initial")
                .status("failed")
                .provider("deepseek")
                .model("deepseek-chat")
                .durationMs(840L)
                .errorMessage("Provider unavailable")
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, 2))
                .build();
    }
}
