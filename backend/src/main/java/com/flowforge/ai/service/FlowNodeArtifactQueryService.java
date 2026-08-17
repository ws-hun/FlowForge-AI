package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
import com.flowforge.ai.dto.FlowNodeArtifactSummaryResponse;
import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.FlowNodeArtifactRepository;
import com.flowforge.ai.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlowNodeArtifactQueryService {

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
                artifact.getCreatedAt()
        );
    }
}
