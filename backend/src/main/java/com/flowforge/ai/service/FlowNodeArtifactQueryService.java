package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageEntryResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageResponse;
import com.flowforge.ai.dto.FlowNodeArtifactSummaryResponse;
import com.flowforge.ai.dto.FlowProviderCallResponse;
import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.FlowNodeArtifactRepository;
import com.flowforge.ai.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlowNodeArtifactQueryService {

    private static final String TERMINATION_FLOW_SNAPSHOT = "flow-snapshot";
    private static final String TERMINATION_LEGACY_RECORD = "legacy-record";
    private static final String TERMINATION_MISSING_UPSTREAM = "missing-upstream-artifact";
    private static final String TERMINATION_CYCLE = "cycle-detected";
    private static final String TERMINATION_UNSUPPORTED_STORAGE = "unsupported-input-storage";

    private final TaskRepository taskRepository;
    private final FlowNodeArtifactRepository artifactRepository;

    @Transactional(readOnly = true)
    public List<FlowNodeArtifactSummaryResponse> listForTask(UUID taskId) {
        requireTask(taskId);
        return artifactRepository.findByTaskIdOrderBySequenceNumberAsc(taskId)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowNodeArtifactDetailResponse getForTask(UUID taskId, String artifactKey) {
        requireTask(taskId);
        FlowNodeArtifact artifact = artifactRepository.findByTaskIdAndArtifactKey(taskId, artifactKey)
                .orElseThrow(() -> new ResourceNotFoundException("Flow node artifact not found"));
        return toDetailResponse(artifact);
    }

    @Transactional(readOnly = true)
    public FlowNodeArtifactLineageResponse getLineageForTask(UUID taskId, String artifactKey) {
        requireTask(taskId);
        Map<String, FlowNodeArtifact> artifacts = artifactRepository
                .findByTaskIdOrderBySequenceNumberAsc(taskId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        FlowNodeArtifact::getArtifactKey,
                        artifact -> artifact,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        FlowNodeArtifact requestedArtifact = artifacts.get(artifactKey);
        if (requestedArtifact == null) {
            throw new ResourceNotFoundException("Flow node artifact not found");
        }

        List<FlowNodeArtifactLineageEntryResponse> path = new java.util.ArrayList<>();
        Set<String> visitedKeys = new LinkedHashSet<>();
        FlowNodeArtifact current = requestedArtifact;
        String termination = TERMINATION_LEGACY_RECORD;
        boolean complete = false;

        while (current != null) {
            if (!visitedKeys.add(current.getArtifactKey())) {
                termination = TERMINATION_CYCLE;
                break;
            }
            path.add(toLineageEntry(current));

            if (current.getInputArtifactKey() == null) {
                termination = TERMINATION_LEGACY_RECORD;
                break;
            }
            if (TERMINATION_FLOW_SNAPSHOT.equals(current.getInputArtifactStorage())) {
                path.add(new FlowNodeArtifactLineageEntryResponse(
                        null,
                        null,
                        null,
                        current.getInputArtifactKey(),
                        current.getInputArtifactType(),
                        current.getInputArtifactStorage(),
                        current.getInputArtifactState(),
                        null,
                        current.getInputContentFingerprint(),
                        null,
                        null,
                        false
                ));
                termination = TERMINATION_FLOW_SNAPSHOT;
                complete = true;
                break;
            }
            if (!"node-artifact".equals(current.getInputArtifactStorage())) {
                termination = TERMINATION_UNSUPPORTED_STORAGE;
                break;
            }
            FlowNodeArtifact upstream = artifacts.get(current.getInputArtifactKey());
            if (upstream == null) {
                termination = TERMINATION_MISSING_UPSTREAM;
                break;
            }
            current = upstream;
        }

        return new FlowNodeArtifactLineageResponse(
                taskId,
                artifactKey,
                complete,
                termination,
                List.copyOf(path)
        );
    }

    private void requireTask(UUID taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task run not found");
        }
    }

    private FlowNodeArtifactSummaryResponse toSummaryResponse(FlowNodeArtifact artifact) {
        return new FlowNodeArtifactSummaryResponse(
                artifact.getId(),
                artifact.getTaskId(),
                artifact.getFlowId(),
                artifact.getNodeId(),
                artifact.getSequenceNumber(),
                artifact.getArtifactKey(),
                artifact.getArtifactType(),
                artifact.getState(),
                artifact.getMediaType(),
                artifact.getContentFingerprint(),
                artifact.getInputArtifactKey(),
                artifact.getInputArtifactType(),
                artifact.getInputArtifactStorage(),
                artifact.getInputArtifactState(),
                artifact.getInputResolution(),
                artifact.getInputContentFingerprint(),
                toProviderCallResponse(artifact),
                artifact.getCreatedAt()
        );
    }

    private FlowNodeArtifactDetailResponse toDetailResponse(FlowNodeArtifact artifact) {
        return new FlowNodeArtifactDetailResponse(
                artifact.getId(),
                artifact.getTaskId(),
                artifact.getFlowId(),
                artifact.getNodeId(),
                artifact.getSequenceNumber(),
                artifact.getArtifactKey(),
                artifact.getArtifactType(),
                artifact.getState(),
                artifact.getMediaType(),
                artifact.getPayload(),
                artifact.getContentFingerprint(),
                artifact.getInputArtifactKey(),
                artifact.getInputArtifactType(),
                artifact.getInputArtifactStorage(),
                artifact.getInputArtifactState(),
                artifact.getInputResolution(),
                artifact.getInputContentFingerprint(),
                toProviderCallResponse(artifact),
                artifact.getCreatedAt()
        );
    }

    private FlowNodeArtifactLineageEntryResponse toLineageEntry(FlowNodeArtifact artifact) {
        return new FlowNodeArtifactLineageEntryResponse(
                artifact.getId(),
                artifact.getNodeId(),
                artifact.getSequenceNumber(),
                artifact.getArtifactKey(),
                artifact.getArtifactType(),
                "node-artifact",
                artifact.getState(),
                artifact.getMediaType(),
                artifact.getContentFingerprint(),
                artifact.getInputResolution(),
                toProviderCallResponse(artifact),
                true
        );
    }

    private FlowProviderCallResponse toProviderCallResponse(FlowNodeArtifact artifact) {
        if (artifact.getProviderCallStatus() == null) {
            return null;
        }
        return new FlowProviderCallResponse(
                artifact.getProviderCallStatus(),
                artifact.getProviderName(),
                artifact.getProviderModel(),
                artifact.getProviderInputTokens(),
                artifact.getProviderOutputTokens(),
                artifact.getProviderTotalTokens(),
                artifact.getProviderDurationMs(),
                artifact.getProviderErrorMessage()
        );
    }
}
