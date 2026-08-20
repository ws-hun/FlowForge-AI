package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageEntryResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageResponse;
import com.flowforge.ai.dto.FlowNodeArtifactSummaryResponse;
import com.flowforge.ai.dto.FlowProviderCallResponse;
import com.flowforge.ai.dto.FlowProviderAttemptResponse;
import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.entity.FlowProviderAttempt;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.FlowNodeArtifactRepository;
import com.flowforge.ai.repository.FlowProviderAttemptRepository;
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
    private final FlowProviderAttemptRepository providerAttemptRepository;

    @Transactional(readOnly = true)
    public List<FlowNodeArtifactSummaryResponse> listForTask(UUID taskId) {
        requireTask(taskId);
        List<FlowNodeArtifact> artifacts = artifactRepository.findByTaskIdOrderBySequenceNumberAsc(taskId);
        Map<UUID, List<FlowProviderAttemptResponse>> attempts = providerAttemptsByArtifact(artifacts);
        return artifacts
                .stream()
                .map(artifact -> toSummaryResponse(
                        artifact,
                        attempts.getOrDefault(artifact.getId(), List.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowNodeArtifactDetailResponse getForTask(UUID taskId, String artifactKey) {
        requireTask(taskId);
        FlowNodeArtifact artifact = artifactRepository.findByTaskIdAndArtifactKey(taskId, artifactKey)
                .orElseThrow(() -> new ResourceNotFoundException("Flow node artifact not found"));
        List<FlowProviderAttemptResponse> attempts = providerAttemptRepository
                .findByArtifactIdOrderByAttemptNumberAsc(artifact.getId())
                .stream()
                .map(this::toProviderAttemptResponse)
                .toList();
        return toDetailResponse(artifact, attempts);
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
        Map<UUID, List<FlowProviderAttemptResponse>> attempts = providerAttemptsByArtifact(
                List.copyOf(artifacts.values())
        );
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
            path.add(toLineageEntry(
                    current,
                    attempts.getOrDefault(current.getId(), List.of())
            ));

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

    private FlowNodeArtifactSummaryResponse toSummaryResponse(
            FlowNodeArtifact artifact,
            List<FlowProviderAttemptResponse> attempts
    ) {
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
                toProviderCallResponse(artifact, attempts),
                artifact.getCreatedAt()
        );
    }

    private FlowNodeArtifactDetailResponse toDetailResponse(
            FlowNodeArtifact artifact,
            List<FlowProviderAttemptResponse> attempts
    ) {
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
                toProviderCallResponse(artifact, attempts),
                attempts,
                artifact.getCreatedAt()
        );
    }

    private FlowNodeArtifactLineageEntryResponse toLineageEntry(
            FlowNodeArtifact artifact,
            List<FlowProviderAttemptResponse> attempts
    ) {
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
                toProviderCallResponse(artifact, attempts),
                true
        );
    }

    private Map<UUID, List<FlowProviderAttemptResponse>> providerAttemptsByArtifact(
            List<FlowNodeArtifact> artifacts
    ) {
        if (artifacts.isEmpty()) {
            return Map.of();
        }
        return providerAttemptRepository.findByArtifactIdInOrderByArtifactIdAscAttemptNumberAsc(
                        artifacts.stream().map(FlowNodeArtifact::getId).toList()
                )
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        FlowProviderAttempt::getArtifactId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(
                                this::toProviderAttemptResponse,
                                java.util.stream.Collectors.toList()
                        )
                ));
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

    private FlowProviderCallResponse toProviderCallResponse(
            FlowNodeArtifact artifact,
            List<FlowProviderAttemptResponse> attempts
    ) {
        if (attempts.isEmpty()) {
            return toProviderCallResponse(artifact);
        }
        FlowProviderAttemptResponse latest = attempts.get(attempts.size() - 1);
        return new FlowProviderCallResponse(
                latest.status(),
                latest.provider(),
                latest.model(),
                latest.inputTokens(),
                latest.outputTokens(),
                latest.totalTokens(),
                latest.durationMs(),
                latest.errorMessage()
        );
    }

    private FlowProviderAttemptResponse toProviderAttemptResponse(FlowProviderAttempt attempt) {
        return new FlowProviderAttemptResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getTriggerType(),
                attempt.getPreviousAttemptId(),
                attempt.getStatus(),
                attempt.getProvider(),
                attempt.getModel(),
                attempt.getInputTokens(),
                attempt.getOutputTokens(),
                attempt.getTotalTokens(),
                attempt.getDurationMs(),
                attempt.getErrorMessage(),
                attempt.getCreatedAt()
        );
    }
}
