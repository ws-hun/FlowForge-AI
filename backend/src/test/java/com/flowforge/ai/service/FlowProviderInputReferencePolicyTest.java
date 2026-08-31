package com.flowforge.ai.service;

import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.entity.FlowProviderInputReference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowProviderInputReferencePolicyTest {

    private final FlowProviderInputReferencePolicy policy = new FlowProviderInputReferencePolicy();

    @Test
    void acceptsAnOrderedObjectiveAndMaterializedNodeInputs() {
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FlowNodeArtifact input = artifact(taskId, flowId, "input-1", 1,
                "node:input-1:context-contribution", "context-contribution", "a".repeat(64));
        FlowNodeArtifact prompt = artifact(taskId, flowId, "prompt-1", 2,
                "node:prompt-1:instruction-contribution", "instruction-contribution", "b".repeat(64));
        FlowNodeArtifact provider = artifact(taskId, flowId, "ai-task-1", 3,
                "node:ai-task-1:provider-result", "provider-result", "c".repeat(64));

        assertThatCode(() -> policy.validate(provider, List.of(
                objective(provider, 1),
                reference(provider, input, 2),
                reference(provider, prompt, 3)
        ), List.of(input, prompt))).doesNotThrowAnyException();
    }

    @Test
    void acceptsAnEmptyListForRunsCreatedBeforeReferencePersistence() {
        FlowNodeArtifact provider = artifact(UUID.randomUUID(), UUID.randomUUID(), "ai-task-1", 1,
                "node:ai-task-1:provider-result", "provider-result", "provider-sha");

        assertThatCode(() -> policy.validate(provider, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonContiguousOrderAndAnObjectiveOutsideTheFirstPosition() {
        FlowNodeArtifact provider = artifact(UUID.randomUUID(), UUID.randomUUID(), "ai-task-1", 2,
                "node:ai-task-1:provider-result", "provider-result", "a".repeat(64));
        FlowProviderInputReference objective = objective(provider, 2);

        assertInvalid(provider, List.of(objective), List.of());
    }

    @Test
    void rejectsAReferenceOwnedByAnotherProviderArtifact() {
        FlowNodeArtifact provider = artifact(UUID.randomUUID(), UUID.randomUUID(), "ai-task-1", 2,
                "node:ai-task-1:provider-result", "provider-result", "a".repeat(64));
        FlowProviderInputReference objective = objective(UUID.randomUUID(), 1);

        assertInvalid(provider, List.of(objective), List.of());
    }

    @Test
    void rejectsAMissingOrLaterSourceArtifact() {
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FlowNodeArtifact provider = artifact(taskId, flowId, "ai-task-1", 2,
                "node:ai-task-1:provider-result", "provider-result", "c".repeat(64));
        FlowNodeArtifact source = artifact(taskId, flowId, "input-1", 3,
                "node:input-1:context-contribution", "context-contribution", "a".repeat(64));

        assertInvalid(provider, List.of(objective(provider, 1), reference(provider, source, 2)), List.of());
        assertInvalid(provider, List.of(objective(provider, 1), reference(provider, source, 2)), List.of(source));
    }

    @Test
    void rejectsSourceNodeOrFingerprintDrift() {
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FlowNodeArtifact provider = artifact(taskId, flowId, "ai-task-1", 2,
                "node:ai-task-1:provider-result", "provider-result", "c".repeat(64));
        FlowNodeArtifact source = artifact(taskId, flowId, "input-1", 1,
                "node:input-1:context-contribution", "context-contribution", "a".repeat(64));
        FlowProviderInputReference wrongNode = reference(provider, source, 2, "input-other", "a".repeat(64));
        FlowProviderInputReference wrongFingerprint = reference(
                provider,
                source,
                2,
                "input-1",
                "different-sha"
        );

        assertInvalid(provider, List.of(objective(provider, 1), wrongNode), List.of(source));
        assertInvalid(provider, List.of(objective(provider, 1), wrongFingerprint), List.of(source));
    }

    @Test
    void rejectsMalformedFingerprintsAndNullReferenceRows() {
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FlowNodeArtifact provider = artifact(taskId, flowId, "ai-task-1", 2,
                "node:ai-task-1:provider-result", "provider-result", "c".repeat(64));
        FlowNodeArtifact source = artifact(taskId, flowId, "input-1", 1,
                "node:input-1:context-contribution", "context-contribution", "not-a-sha");

        assertInvalid(provider, List.of(objective(provider, 1), reference(provider, source, 2)), List.of(source));
        List<FlowProviderInputReference> nullRow = new ArrayList<>();
        nullRow.add(objective(provider, 1));
        nullRow.add(null);
        assertInvalid(provider, nullRow, List.of(source));
    }

    private void assertInvalid(
            FlowNodeArtifact provider,
            List<FlowProviderInputReference> references,
            List<FlowNodeArtifact> sources
    ) {
        assertThatThrownBy(() -> policy.validate(provider, references, sources))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Persisted Flow Provider input references are inconsistent");
    }

    private FlowNodeArtifact artifact(
            UUID taskId,
            UUID flowId,
            String nodeId,
            int sequence,
            String artifactKey,
            String artifactType,
            String fingerprint
    ) {
        return FlowNodeArtifact.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .flowId(flowId)
                .nodeId(nodeId)
                .sequenceNumber(sequence)
                .artifactKey(artifactKey)
                .artifactType(artifactType)
                .state("materialized")
                .contentFingerprint(fingerprint)
                .build();
    }

    private FlowProviderInputReference objective(FlowNodeArtifact provider, int order) {
        return objective(provider.getId(), order);
    }

    private FlowProviderInputReference objective(UUID providerArtifactId, int order) {
        return FlowProviderInputReference.builder()
                .id(UUID.randomUUID())
                .providerArtifactId(providerArtifactId)
                .inputOrder(order)
                .artifactKey("flow:objective")
                .artifactType("flow-objective")
                .artifactStorage("flow-snapshot")
                .artifactState("materialized")
                .inputResolution("compiled-reference")
                .contentFingerprint("e".repeat(64))
                .build();
    }

    private FlowProviderInputReference reference(
            FlowNodeArtifact provider,
            FlowNodeArtifact source,
            int order
    ) {
        return reference(provider, source, order, source.getNodeId(), source.getContentFingerprint());
    }

    private FlowProviderInputReference reference(
            FlowNodeArtifact provider,
            FlowNodeArtifact source,
            int order,
            String sourceNodeId,
            String contentFingerprint
    ) {
        return FlowProviderInputReference.builder()
                .id(UUID.randomUUID())
                .providerArtifactId(provider.getId())
                .inputOrder(order)
                .artifactKey(source.getArtifactKey())
                .artifactType(source.getArtifactType())
                .artifactStorage("node-artifact")
                .artifactState(source.getState())
                .inputResolution("compiled-reference")
                .contentFingerprint(contentFingerprint)
                .sourceArtifactId(source.getId())
                .sourceNodeId(sourceNodeId)
                .build();
    }
}
