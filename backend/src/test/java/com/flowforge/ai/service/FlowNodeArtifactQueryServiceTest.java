package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageEntryResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageResponse;
import com.flowforge.ai.dto.FlowNodeArtifactSummaryResponse;
import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.FlowNodeArtifactRepository;
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

    private FlowNodeArtifactQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new FlowNodeArtifactQueryService(taskRepository, artifactRepository);
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
    void returnsOneAddressableArtifactPayload() {
        UUID taskId = UUID.randomUUID();
        FlowNodeArtifact artifact = artifact(taskId, "ai-task-1", 2, "provider-result", "Summary\nResult");
        when(taskRepository.existsById(taskId)).thenReturn(true);
        when(artifactRepository.findByTaskIdAndArtifactKey(taskId, artifact.getArtifactKey()))
                .thenReturn(Optional.of(artifact));

        FlowNodeArtifactDetailResponse response = queryService.getForTask(taskId, artifact.getArtifactKey());

        assertThat(response.artifactKey()).isEqualTo(artifact.getArtifactKey());
        assertThat(response.payload()).isEqualTo("Summary\nResult");
        assertThat(response.mediaType()).isEqualTo("text/markdown");
        assertThat(response.inputArtifactKey()).isEqualTo("node:input-1:context-contribution");
        assertThat(response.inputArtifactStorage()).isEqualTo("node-artifact");
        assertThat(response.inputArtifactState()).isEqualTo("materialized");
        assertThat(response.inputResolution()).isEqualTo("compiled-reference");
        assertThat(response.inputContentFingerprint()).hasSize(64);
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

        FlowNodeArtifactSummaryResponse summary = queryService.listForTask(taskId).get(0);
        FlowNodeArtifactDetailResponse detail = queryService.getForTask(taskId, legacyArtifact.getArtifactKey());

        assertThat(summary.inputArtifactKey()).isNull();
        assertThat(summary.inputArtifactType()).isNull();
        assertThat(summary.inputArtifactStorage()).isNull();
        assertThat(summary.inputArtifactState()).isNull();
        assertThat(summary.inputResolution()).isNull();
        assertThat(summary.inputContentFingerprint()).isNull();
        assertThat(detail.inputArtifactKey()).isNull();
        assertThat(detail.inputArtifactType()).isNull();
        assertThat(detail.inputArtifactStorage()).isNull();
        assertThat(detail.inputArtifactState()).isNull();
        assertThat(detail.inputResolution()).isNull();
        assertThat(detail.inputContentFingerprint()).isNull();
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
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, sequence))
                .build();
    }
}
