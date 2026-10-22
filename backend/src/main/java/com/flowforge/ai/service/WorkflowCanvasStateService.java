package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowCanvasEdgeDto;
import com.flowforge.ai.dto.FlowCanvasPositionDto;
import com.flowforge.ai.dto.FlowCanvasStateRequest;
import com.flowforge.ai.dto.FlowCanvasStateResponse;
import com.flowforge.ai.dto.FlowCanvasViewportDto;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.entity.WorkflowCanvasState;
import com.flowforge.ai.exception.ResourceConflictException;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.WorkflowCanvasStateRepository;
import com.flowforge.ai.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowCanvasStateService {

    private static final TypeReference<List<FlowNodeDto>> FLOW_NODE_LIST_TYPE = new TypeReference<>() {
    };

    private final WorkflowCanvasStateRepository canvasStateRepository;
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FlowCanvasStateResponse getCanvasState(UUID flowId) {
        if (!workflowRepository.existsById(flowId)) {
            throw new ResourceNotFoundException("Flow not found");
        }
        return canvasStateRepository.findById(flowId)
                .map(this::toResponse)
                .orElseGet(() -> emptyResponse(flowId));
    }

    @Transactional
    public FlowCanvasStateResponse saveCanvasState(UUID flowId, FlowCanvasStateRequest request) {
        Workflow flow = workflowRepository.findByIdForUpdate(flowId)
                .orElseThrow(() -> new ResourceNotFoundException("Flow not found"));
        validateState(flow, request);

        WorkflowCanvasState state = canvasStateRepository.findById(flowId).orElse(null);
        if (state == null) {
            if (request.revision() != 0) {
                throw new ResourceConflictException("Canvas changed in another window");
            }
            state = WorkflowCanvasState.builder()
                    .flowId(flowId)
                    .revision(1L)
                    .stateJson(serialize(request.positions(), request.edges(), request.viewport()))
                    .build();
        } else {
            if (!state.getRevision().equals(request.revision())) {
                throw new ResourceConflictException("Canvas changed in another window");
            }
            state.setRevision(state.getRevision() + 1);
            state.setStateJson(serialize(request.positions(), request.edges(), request.viewport()));
        }

        return toResponse(canvasStateRepository.saveAndFlush(state));
    }

    private void validateState(Workflow flow, FlowCanvasStateRequest request) {
        Set<String> nodeIds = new HashSet<>();
        try {
            objectMapper.readValue(flow.getNodesJson(), FLOW_NODE_LIST_TYPE)
                    .forEach(node -> nodeIds.add(node.id()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Saved Flow nodes are invalid", exception);
        }

        if (!nodeIds.containsAll(request.positions().keySet())) {
            throw new IllegalArgumentException("Canvas position references a missing Flow node");
        }

        Set<String> edgeIds = new HashSet<>();
        Set<List<String>> connections = new HashSet<>();
        for (FlowCanvasEdgeDto edge : request.edges()) {
            if (!nodeIds.contains(edge.source()) || !nodeIds.contains(edge.target()) || edge.source().equals(edge.target())) {
                throw new IllegalArgumentException("Canvas edge references an invalid Flow node");
            }
            if ((edge.sourceHandle() != null && !"source".equals(edge.sourceHandle())) ||
                    (edge.targetHandle() != null && !"target".equals(edge.targetHandle()))) {
                throw new IllegalArgumentException("Canvas edge handle is invalid");
            }
            List<String> connection = Arrays.asList(
                    edge.source(),
                    edge.sourceHandle() == null ? "source" : edge.sourceHandle(),
                    edge.target(),
                    edge.targetHandle() == null ? "target" : edge.targetHandle()
            );
            if (!edgeIds.add(edge.id()) || !connections.add(connection)) {
                throw new IllegalArgumentException("Canvas contains duplicate edges");
            }
        }
    }

    private String serialize(
            Map<String, FlowCanvasPositionDto> positions,
            List<FlowCanvasEdgeDto> edges,
            FlowCanvasViewportDto viewport
    ) {
        try {
            return objectMapper.writeValueAsString(new CanvasPayload(positions, edges, viewport));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Canvas state could not be serialized", exception);
        }
    }

    private FlowCanvasStateResponse toResponse(WorkflowCanvasState state) {
        try {
            CanvasPayload payload = objectMapper.readValue(state.getStateJson(), CanvasPayload.class);
            return new FlowCanvasStateResponse(
                    state.getFlowId(),
                    state.getRevision(),
                    true,
                    payload.positions(),
                    payload.edges(),
                    payload.viewport(),
                    state.getUpdatedAt()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Saved canvas state is invalid", exception);
        }
    }

    private FlowCanvasStateResponse emptyResponse(UUID flowId) {
        return new FlowCanvasStateResponse(flowId, 0, false, Map.of(), List.of(), null, null);
    }

    private record CanvasPayload(
            Map<String, FlowCanvasPositionDto> positions,
            List<FlowCanvasEdgeDto> edges,
            FlowCanvasViewportDto viewport
    ) {
    }
}
