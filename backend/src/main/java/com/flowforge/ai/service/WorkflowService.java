package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowRequest;
import com.flowforge.ai.dto.FlowResponse;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final TypeReference<List<FlowNodeDto>> NODE_LIST_TYPE = new TypeReference<>() {
    };

    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<FlowResponse> listFlows() {
        return workflowRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FlowResponse createFlow(FlowRequest request) {
        Workflow workflow = Workflow.builder().build();
        applyRequest(workflow, request);
        return toResponse(workflowRepository.save(workflow));
    }

    @Transactional
    public FlowResponse updateFlow(UUID id, FlowRequest request) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        applyRequest(workflow, request);
        return toResponse(workflow);
    }

    @Transactional
    public void deleteFlow(UUID id) {
        workflowRepository.deleteById(id);
    }

    private void applyRequest(Workflow workflow, FlowRequest request) {
        workflow.setTitle(cleanRequired(request.title(), "title"));
        workflow.setDescription(cleanRequired(request.description(), "description"));
        workflow.setNodesJson(serializeNodes(request.nodes()));
    }

    private String serializeNodes(List<FlowNodeDto> nodes) {
        try {
            return objectMapper.writeValueAsString(nodes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize flow nodes", e);
        }
    }

    private List<FlowNodeDto> deserializeNodes(String nodesJson) {
        try {
            return objectMapper.readValue(nodesJson, NODE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse flow nodes", e);
        }
    }

    private String cleanRequired(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(field + " is required");
        }
        return value.trim();
    }

    private FlowResponse toResponse(Workflow workflow) {
        return new FlowResponse(
                workflow.getId(),
                workflow.getTitle(),
                workflow.getDescription(),
                deserializeNodes(workflow.getNodesJson()),
                workflow.getCreatedAt(),
                workflow.getUpdatedAt()
        );
    }
}
