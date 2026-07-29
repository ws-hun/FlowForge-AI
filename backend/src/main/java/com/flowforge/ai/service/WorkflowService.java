package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowRequest;
import com.flowforge.ai.dto.FlowResponse;
import com.flowforge.ai.dto.FlowVersionResponse;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.entity.WorkflowVersion;
import com.flowforge.ai.exception.ResourceConflictException;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.WorkflowRepository;
import com.flowforge.ai.repository.WorkflowVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final int VERSION_RETENTION_LIMIT = 8;

    private static final TypeReference<List<FlowNodeDto>> NODE_LIST_TYPE = new TypeReference<>() {
    };

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final ObjectMapper objectMapper;
    private final FlowDefinitionValidator flowDefinitionValidator;

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
        flowDefinitionValidator.validate(request.nodes());
        applyRequest(workflow, request);
        applySource(workflow, request);
        return toResponse(workflowRepository.save(workflow));
    }

    @Transactional
    public FlowResponse updateFlow(UUID id, FlowRequest request) {
        Workflow workflow = findFlowForMutation(id);
        assertCurrentRevision(workflow, request.revision());
        flowDefinitionValidator.validate(request.nodes());
        saveVersionSnapshot(workflow);
        applyRequest(workflow, request);
        return toResponse(workflowRepository.saveAndFlush(workflow));
    }

    @Transactional
    public void deleteFlow(UUID id, Long revision) {
        Workflow workflow = findFlowForMutation(id);
        assertCurrentRevision(workflow, revision);
        workflowVersionRepository.deleteByFlowId(id);
        workflowRepository.delete(workflow);
    }

    @Transactional(readOnly = true)
    public List<FlowVersionResponse> listVersions(UUID flowId) {
        return workflowVersionRepository.findTop8ByFlowIdOrderByVersionNumberDesc(flowId)
                .stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional
    public FlowResponse restoreVersion(UUID flowId, UUID versionId, Long revision) {
        Workflow workflow = findFlowForMutation(flowId);
        assertCurrentRevision(workflow, revision);
        WorkflowVersion version = workflowVersionRepository.findById(versionId)
                .filter(item -> item.getFlowId().equals(flowId))
                .orElseThrow(() -> new ResourceNotFoundException("Flow version not found"));

        List<FlowNodeDto> versionNodes = deserializeNodes(version.getNodesJson());
        flowDefinitionValidator.validate(versionNodes);

        saveVersionSnapshot(workflow);
        workflow.setTitle(version.getTitle());
        workflow.setDescription(version.getDescription());
        workflow.setNodesJson(version.getNodesJson());

        return toResponse(workflowRepository.saveAndFlush(workflow));
    }

    private Workflow findFlowForMutation(UUID id) {
        return workflowRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flow not found"));
    }

    private void assertCurrentRevision(Workflow workflow, Long expectedRevision) {
        if (expectedRevision == null) {
            throw new IllegalArgumentException("revision is required");
        }
        if (!Objects.equals(workflow.getRevision(), expectedRevision)) {
            throw new ResourceConflictException("Flow 已在其他窗口更新，请基于最新版本重新确认修改");
        }
    }

    private void saveVersionSnapshot(Workflow workflow) {
        int versionNumber = workflowVersionRepository.findTopByFlowIdOrderByVersionNumberDesc(workflow.getId())
                .map(WorkflowVersion::getVersionNumber)
                .orElse(0) + 1;

        WorkflowVersion version = WorkflowVersion.builder()
                .flowId(workflow.getId())
                .versionNumber(versionNumber)
                .title(workflow.getTitle())
                .description(workflow.getDescription())
                .nodesJson(workflow.getNodesJson())
                .build();

        workflowVersionRepository.saveAndFlush(version);

        List<WorkflowVersion> versions = workflowVersionRepository.findByFlowIdOrderByVersionNumberDesc(workflow.getId());
        if (versions.size() > VERSION_RETENTION_LIMIT) {
            workflowVersionRepository.deleteAll(versions.subList(VERSION_RETENTION_LIMIT, versions.size()));
        }
    }

    private void applyRequest(Workflow workflow, FlowRequest request) {
        workflow.setTitle(cleanRequired(request.title(), "title"));
        workflow.setDescription(cleanRequired(request.description(), "description"));
        workflow.setNodesJson(serializeNodes(request.nodes()));
    }

    private void applySource(Workflow workflow, FlowRequest request) {
        if (request.sourceFlowVersionId() != null && request.sourceFlowId() == null) {
            throw new IllegalArgumentException("sourceFlowId is required when sourceFlowVersionId is provided");
        }
        if (request.sourceFlowId() == null) {
            return;
        }

        Workflow sourceFlow = workflowRepository.findById(request.sourceFlowId())
                .orElseThrow(() -> new ResourceNotFoundException("Source Flow not found"));
        workflow.setSourceFlowId(sourceFlow.getId());
        workflow.setSourceFlowTitle(sourceFlow.getTitle());

        if (request.sourceFlowVersionId() == null) {
            return;
        }
        WorkflowVersion sourceVersion = workflowVersionRepository.findById(request.sourceFlowVersionId())
                .filter(version -> version.getFlowId().equals(sourceFlow.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Source Flow revision not found"));
        workflow.setSourceFlowVersionId(sourceVersion.getId());
        workflow.setSourceFlowVersionNumber(sourceVersion.getVersionNumber());
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
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private FlowResponse toResponse(Workflow workflow) {
        return new FlowResponse(
                workflow.getId(),
                workflow.getTitle(),
                workflow.getDescription(),
                deserializeNodes(workflow.getNodesJson()),
                workflow.getSourceFlowId(),
                workflow.getSourceFlowTitle(),
                workflow.getSourceFlowVersionId(),
                workflow.getSourceFlowVersionNumber(),
                workflow.getRevision(),
                workflow.getCreatedAt(),
                workflow.getUpdatedAt()
        );
    }

    private FlowVersionResponse toVersionResponse(WorkflowVersion version) {
        return new FlowVersionResponse(
                version.getId(),
                version.getFlowId(),
                version.getVersionNumber(),
                version.getTitle(),
                version.getDescription(),
                deserializeNodes(version.getNodesJson()),
                version.getCreatedAt()
        );
    }
}
