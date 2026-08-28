package com.flowforge.ai.service;

import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.entity.FlowProviderInputReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FlowProviderInputReferencePolicy {

    void validate(
            FlowNodeArtifact providerArtifact,
            List<FlowProviderInputReference> references,
            List<FlowNodeArtifact> sourceArtifacts
    ) {
        if (references.isEmpty()) {
            return;
        }
        if (!"provider-result".equals(providerArtifact.getArtifactType())) {
            throw invalidReferences();
        }

        Map<UUID, FlowNodeArtifact> sourcesById = sourceArtifacts.stream()
                .collect(Collectors.toMap(FlowNodeArtifact::getId, Function.identity()));
        Set<String> artifactKeys = new LinkedHashSet<>();
        Set<UUID> sourceArtifactIds = new LinkedHashSet<>();

        for (int index = 0; index < references.size(); index++) {
            FlowProviderInputReference reference = references.get(index);
            if (reference.getInputOrder() == null
                    || reference.getInputOrder() != index + 1
                    || !providerArtifact.getId().equals(reference.getProviderArtifactId())
                    || !artifactKeys.add(reference.getArtifactKey())
                    || !FlowExecutionCompiler.INPUT_RESOLUTION.equals(reference.getInputResolution())) {
                throw invalidReferences();
            }
            if (index == 0) {
                validateObjective(reference);
                continue;
            }
            validateNodeReference(
                    providerArtifact,
                    reference,
                    sourcesById.get(reference.getSourceArtifactId()),
                    sourceArtifactIds
            );
        }
    }

    private void validateObjective(FlowProviderInputReference reference) {
        if (!"flow:objective".equals(reference.getArtifactKey())
                || !"flow-objective".equals(reference.getArtifactType())
                || !"flow-snapshot".equals(reference.getArtifactStorage())
                || !"materialized".equals(reference.getArtifactState())
                || !StringUtils.hasText(reference.getContentFingerprint())
                || reference.getSourceArtifactId() != null
                || reference.getSourceNodeId() != null) {
            throw invalidReferences();
        }
    }

    private void validateNodeReference(
            FlowNodeArtifact providerArtifact,
            FlowProviderInputReference reference,
            FlowNodeArtifact sourceArtifact,
            Set<UUID> sourceArtifactIds
    ) {
        if (!"node-artifact".equals(reference.getArtifactStorage())
                || !"materialized".equals(reference.getArtifactState())
                || reference.getSourceArtifactId() == null
                || !sourceArtifactIds.add(reference.getSourceArtifactId())
                || sourceArtifact == null
                || !providerArtifact.getTaskId().equals(sourceArtifact.getTaskId())
                || !providerArtifact.getFlowId().equals(sourceArtifact.getFlowId())
                || sourceArtifact.getSequenceNumber() >= providerArtifact.getSequenceNumber()
                || !sourceArtifact.getNodeId().equals(reference.getSourceNodeId())
                || !sourceArtifact.getArtifactKey().equals(reference.getArtifactKey())
                || !sourceArtifact.getArtifactType().equals(reference.getArtifactType())
                || !sourceArtifact.getState().equals(reference.getArtifactState())
                || !StringUtils.hasText(sourceArtifact.getContentFingerprint())
                || !sourceArtifact.getContentFingerprint().equals(reference.getContentFingerprint())) {
            throw invalidReferences();
        }
    }

    private IllegalStateException invalidReferences() {
        return new IllegalStateException("Persisted Flow Provider input references are inconsistent");
    }
}
