package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
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
                .createdAt(LocalDateTime.of(2026, 8, 17, 10, sequence))
                .build();
    }
}
