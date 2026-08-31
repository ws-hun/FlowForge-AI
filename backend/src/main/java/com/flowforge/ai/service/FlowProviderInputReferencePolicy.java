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
import java.util.regex.Pattern;

@Component
public class FlowProviderInputReferencePolicy {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    void validate(
            FlowNodeArtifact providerArtifact,
            List<FlowProviderInputReference> references,
            List<FlowNodeArtifact> sourceArtifacts
    ) {
        if (providerArtifact == null || references == null || sourceArtifacts == null) {
            throw invalidReferences();
        }
        if (references.isEmpty()) {
            return;
        }
        if (!"provider-result".equals(providerArtifact.getArtifactType())
                || !List.of("materialized", "failed").contains(providerArtifact.getState())
                || providerArtifact.getId() == null
                || providerArtifact.getTaskId() == null
                || providerArtifact.getFlowId() == null) {
            throw invalidReferences();
        }

        Map<UUID, FlowNodeArtifact> sourcesById = sourceArtifacts.stream()
                .collect(Collectors.toMap(FlowNodeArtifact::getId, Function.identity()));
        Set<String> artifactKeys = new LinkedHashSet<>();
        Set<UUID> sourceArtifactIds = new LinkedHashSet<>();

        for (int index = 0; index < references.size(); index++) {
            FlowProviderInputReference reference = references.get(index);
            if (reference == null
                    || reference.getInputOrder() == null
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
                || !isFingerprint(reference.getContentFingerprint())
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
                || !isFingerprint(sourceArtifact.getContentFingerprint())
                || !sourceArtifact.getContentFingerprint().equals(reference.getContentFingerprint())) {
            throw invalidReferences();
        }
    }

    private boolean isFingerprint(String fingerprint) {
        return StringUtils.hasText(fingerprint) && SHA_256_PATTERN.matcher(fingerprint).matches();
    }

    private IllegalStateException invalidReferences() {
        return new IllegalStateException("Persisted Flow Provider input references are inconsistent");
    }
}
