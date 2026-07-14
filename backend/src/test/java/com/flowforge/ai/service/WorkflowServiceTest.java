package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowRequest;
import com.flowforge.ai.dto.FlowResponse;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.entity.WorkflowVersion;
import com.flowforge.ai.repository.WorkflowRepository;
import com.flowforge.ai.repository.WorkflowVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private WorkflowVersionRepository workflowVersionRepository;

    @Captor
    private ArgumentCaptor<WorkflowVersion> versionCaptor;

    private WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowService(workflowRepository, workflowVersionRepository, new ObjectMapper());
    }

    @Test
    void savesTheCurrentStateBeforeUpdatingTheFlow() {
        Workflow flow = flow("Original flow", "Original goal", "Original input");
        FlowRequest request = request("Refined flow", "Refined goal", "Refined input");

        when(workflowRepository.findById(flow.getId())).thenReturn(Optional.of(flow));
        when(workflowVersionRepository.findTopByFlowIdOrderByVersionNumberDesc(flow.getId()))
                .thenReturn(Optional.empty());
        when(workflowVersionRepository.saveAndFlush(any(WorkflowVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowVersionRepository.findByFlowIdOrderByVersionNumberDesc(flow.getId()))
                .thenReturn(List.of());
        when(workflowRepository.saveAndFlush(flow)).thenReturn(flow);

        FlowResponse response = workflowService.updateFlow(flow.getId(), request);

        verify(workflowVersionRepository).saveAndFlush(versionCaptor.capture());
        WorkflowVersion snapshot = versionCaptor.getValue();
        assertThat(snapshot.getFlowId()).isEqualTo(flow.getId());
        assertThat(snapshot.getVersionNumber()).isOne();
        assertThat(snapshot.getTitle()).isEqualTo("Original flow");
        assertThat(snapshot.getDescription()).isEqualTo("Original goal");
        assertThat(snapshot.getNodesJson()).contains("Original input");
        assertThat(response.title()).isEqualTo("Refined flow");
        assertThat(response.description()).isEqualTo("Refined goal");
        assertThat(response.nodes()).singleElement().extracting(FlowNodeDto::content).isEqualTo("Refined input");
    }

    @Test
    void preservesTheCurrentStateBeforeRestoringAnEarlierRevision() {
        Workflow flow = flow("Current flow", "Current goal", "Current input");
        WorkflowVersion revision = WorkflowVersion.builder()
                .id(UUID.randomUUID())
                .flowId(flow.getId())
                .versionNumber(3)
                .title("Earlier flow")
                .description("Earlier goal")
                .nodesJson(nodesJson("Earlier input"))
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(workflowRepository.findById(flow.getId())).thenReturn(Optional.of(flow));
        when(workflowVersionRepository.findById(revision.getId())).thenReturn(Optional.of(revision));
        when(workflowVersionRepository.findTopByFlowIdOrderByVersionNumberDesc(flow.getId()))
                .thenReturn(Optional.of(revision));
        when(workflowVersionRepository.saveAndFlush(any(WorkflowVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowVersionRepository.findByFlowIdOrderByVersionNumberDesc(flow.getId()))
                .thenReturn(List.of());
        when(workflowRepository.saveAndFlush(flow)).thenReturn(flow);

        FlowResponse response = workflowService.restoreVersion(flow.getId(), revision.getId());

        verify(workflowVersionRepository).saveAndFlush(versionCaptor.capture());
        WorkflowVersion safetySnapshot = versionCaptor.getValue();
        assertThat(safetySnapshot.getVersionNumber()).isEqualTo(4);
        assertThat(safetySnapshot.getTitle()).isEqualTo("Current flow");
        assertThat(safetySnapshot.getNodesJson()).contains("Current input");
        assertThat(response.title()).isEqualTo("Earlier flow");
        assertThat(response.description()).isEqualTo("Earlier goal");
        assertThat(response.nodes()).singleElement().extracting(FlowNodeDto::content).isEqualTo("Earlier input");
    }

    @Test
    void removesSnapshotsOlderThanTheEightMostRecentVersions() {
        Workflow flow = flow("Original flow", "Original goal", "Original input");
        FlowRequest request = request("Refined flow", "Refined goal", "Refined input");
        List<WorkflowVersion> versions = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(number -> WorkflowVersion.builder()
                        .id(UUID.randomUUID())
                        .flowId(flow.getId())
                        .versionNumber(10 - number)
                        .title("Revision " + number)
                        .description("Revision goal " + number)
                        .nodesJson(nodesJson("Revision input " + number))
                        .build())
                .toList();

        when(workflowRepository.findById(flow.getId())).thenReturn(Optional.of(flow));
        when(workflowVersionRepository.findTopByFlowIdOrderByVersionNumberDesc(flow.getId()))
                .thenReturn(Optional.empty());
        when(workflowVersionRepository.saveAndFlush(any(WorkflowVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowVersionRepository.findByFlowIdOrderByVersionNumberDesc(flow.getId()))
                .thenReturn(versions);
        when(workflowRepository.saveAndFlush(flow)).thenReturn(flow);

        workflowService.updateFlow(flow.getId(), request);

        verify(workflowVersionRepository).deleteAll(eq(versions.subList(8, 9)));
    }

    private Workflow flow(String title, String description, String input) {
        LocalDateTime now = LocalDateTime.now();
        return Workflow.builder()
                .id(UUID.randomUUID())
                .title(title)
                .description(description)
                .nodesJson(nodesJson(input))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private FlowRequest request(String title, String description, String input) {
        return new FlowRequest(title, description, List.of(node(input)));
    }

    private String nodesJson(String input) {
        try {
            return new ObjectMapper().writeValueAsString(List.of(node(input)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private FlowNodeDto node(String input) {
        return new FlowNodeDto(
                "input",
                "input",
                "Intent",
                "The flow input",
                input,
                null,
                null
        );
    }
}
