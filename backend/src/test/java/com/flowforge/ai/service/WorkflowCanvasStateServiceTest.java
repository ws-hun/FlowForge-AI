package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowCanvasEdgeDto;
import com.flowforge.ai.dto.FlowCanvasPositionDto;
import com.flowforge.ai.dto.FlowCanvasStateRequest;
import com.flowforge.ai.dto.FlowCanvasStateResponse;
import com.flowforge.ai.dto.FlowCanvasViewportDto;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.entity.WorkflowCanvasState;
import com.flowforge.ai.exception.ResourceConflictException;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.WorkflowCanvasStateRepository;
import com.flowforge.ai.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowCanvasStateServiceTest {

    @Mock
    private WorkflowCanvasStateRepository canvasStateRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowCanvasStateService service;
    private UUID flowId;
    private Workflow flow;

    @BeforeEach
    void setUp() throws Exception {
        service = new WorkflowCanvasStateService(canvasStateRepository, workflowRepository, objectMapper);
        flowId = UUID.randomUUID();
        flow = Workflow.builder()
                .id(flowId)
                .nodesJson(objectMapper.writeValueAsString(List.of(
                        Map.of("id", "input", "type", "input", "title", "Input", "description", "Goal"),
                        Map.of("id", "output", "type", "output", "title", "Output", "description", "Result")
                )))
                .build();
    }

    @Test
    void returnsAnUnsavedEmptyCanvasForAnExistingFlow() {
        when(workflowRepository.existsById(flowId)).thenReturn(true);
        when(canvasStateRepository.findById(flowId)).thenReturn(Optional.empty());

        FlowCanvasStateResponse response = service.getCanvasState(flowId);

        assertThat(response.flowId()).isEqualTo(flowId);
        assertThat(response.revision()).isZero();
        assertThat(response.saved()).isFalse();
        assertThat(response.positions()).isEmpty();
        assertThat(response.edges()).isEmpty();
    }

    @Test
    void readsPersistedCanvasPayloadIntoItsTypedResponse() throws Exception {
        WorkflowCanvasState saved = WorkflowCanvasState.builder()
                .flowId(flowId)
                .revision(4L)
                .stateJson(objectMapper.writeValueAsString(Map.of(
                        "positions", Map.of("input", new FlowCanvasPositionDto(80.0, 120.0)),
                        "edges", List.of(new FlowCanvasEdgeDto("edge-1", "input", "output", "source", "target")),
                        "viewport", new FlowCanvasViewportDto(-20.0, 10.0, 0.8)
                )))
                .updatedAt(LocalDateTime.of(2026, 10, 21, 12, 0))
                .build();
        when(workflowRepository.existsById(flowId)).thenReturn(true);
        when(canvasStateRepository.findById(flowId)).thenReturn(Optional.of(saved));

        FlowCanvasStateResponse response = service.getCanvasState(flowId);

        assertThat(response.revision()).isEqualTo(4L);
        assertThat(response.saved()).isTrue();
        assertThat(response.positions()).containsEntry("input", new FlowCanvasPositionDto(80.0, 120.0));
        assertThat(response.edges()).containsExactly(new FlowCanvasEdgeDto("edge-1", "input", "output", "source", "target"));
        assertThat(response.viewport()).isEqualTo(new FlowCanvasViewportDto(-20.0, 10.0, 0.8));
        assertThat(response.updatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void savesCanvasStateWithoutCreatingAFlowContentRevision() {
        FlowCanvasStateRequest request = validRequest(0L);
        when(workflowRepository.findByIdForUpdate(flowId)).thenReturn(Optional.of(flow));
        when(canvasStateRepository.findById(flowId)).thenReturn(Optional.empty());
        when(canvasStateRepository.saveAndFlush(any(WorkflowCanvasState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlowCanvasStateResponse response = service.saveCanvasState(flowId, request);

        assertThat(response.saved()).isTrue();
        assertThat(response.revision()).isEqualTo(1L);
        assertThat(response.positions()).containsEntry("input", new FlowCanvasPositionDto(80.0, 120.0));
        assertThat(response.edges()).containsExactlyElementsOf(request.edges());
        verify(workflowRepository, never()).save(any(Workflow.class));
    }

    @Test
    void rejectsCanvasUpdatesFromAStaleCanvasRevision() {
        WorkflowCanvasState saved = WorkflowCanvasState.builder()
                .flowId(flowId)
                .revision(3L)
                .stateJson("{}")
                .build();
        when(workflowRepository.findByIdForUpdate(flowId)).thenReturn(Optional.of(flow));
        when(canvasStateRepository.findById(flowId)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.saveCanvasState(flowId, validRequest(2L)))
                .isInstanceOf(ResourceConflictException.class);
        verify(canvasStateRepository, never()).saveAndFlush(any());
    }

    @Test
    void incrementsCanvasRevisionOnEveryAcceptedSave() {
        WorkflowCanvasState saved = WorkflowCanvasState.builder()
                .flowId(flowId)
                .revision(3L)
                .stateJson("{}")
                .build();
        when(workflowRepository.findByIdForUpdate(flowId)).thenReturn(Optional.of(flow));
        when(canvasStateRepository.findById(flowId)).thenReturn(Optional.of(saved));
        when(canvasStateRepository.saveAndFlush(saved)).thenAnswer(invocation -> invocation.getArgument(0));

        FlowCanvasStateResponse response = service.saveCanvasState(flowId, validRequest(3L));

        assertThat(response.revision()).isEqualTo(4L);
        verify(canvasStateRepository).saveAndFlush(saved);
    }

    @Test
    void rejectsASecondFirstSaveUsingTheEmptyCanvasRevision() {
        WorkflowCanvasState created = WorkflowCanvasState.builder()
                .flowId(flowId)
                .revision(1L)
                .stateJson("{}")
                .build();
        when(workflowRepository.findByIdForUpdate(flowId)).thenReturn(Optional.of(flow));
        when(canvasStateRepository.findById(flowId)).thenReturn(Optional.empty(), Optional.of(created));
        when(canvasStateRepository.saveAndFlush(any(WorkflowCanvasState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveCanvasState(flowId, validRequest(0L));

        assertThatThrownBy(() -> service.saveCanvasState(flowId, validRequest(0L)))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void rejectsPositionsAndEdgesThatReferenceUnknownNodes() {
        when(workflowRepository.findByIdForUpdate(flowId)).thenReturn(Optional.of(flow));

        FlowCanvasStateRequest unknownPosition = new FlowCanvasStateRequest(
                0L,
                Map.of("missing", new FlowCanvasPositionDto(80.0, 120.0)),
                List.of(),
                null
        );
        assertThatThrownBy(() -> service.saveCanvasState(flowId, unknownPosition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position");

        FlowCanvasStateRequest unknownEdge = new FlowCanvasStateRequest(
                0L,
                Map.of(),
                List.of(new FlowCanvasEdgeDto("edge-1", "input", "missing", "source", "target")),
                null
        );
        assertThatThrownBy(() -> service.saveCanvasState(flowId, unknownEdge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("edge");
        verify(canvasStateRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateCanvasConnections() {
        when(workflowRepository.findByIdForUpdate(flowId)).thenReturn(Optional.of(flow));
        FlowCanvasStateRequest request = new FlowCanvasStateRequest(
                0L,
                Map.of(),
                List.of(
                        new FlowCanvasEdgeDto("edge-1", "input", "output", null, null),
                        new FlowCanvasEdgeDto("edge-2", "input", "output", "source", "target")
                ),
                null
        );

        assertThatThrownBy(() -> service.saveCanvasState(flowId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        verify(canvasStateRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsNotFoundForAFlowThatDoesNotExist() {
        when(workflowRepository.existsById(flowId)).thenReturn(false);
        assertThatThrownBy(() -> service.getCanvasState(flowId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private FlowCanvasStateRequest validRequest(long revision) {
        return new FlowCanvasStateRequest(
                revision,
                Map.of("input", new FlowCanvasPositionDto(80.0, 120.0)),
                List.of(new FlowCanvasEdgeDto("edge-1", "input", "output", "source", "target")),
                new FlowCanvasViewportDto(-20.0, 10.0, 0.8)
        );
    }
}
