package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactResponse;
import com.flowforge.ai.dto.FlowNodeRunTraceResponse;
import com.flowforge.ai.dto.FlowArtifactContractResponse;
import com.flowforge.ai.dto.FlowExecutionPlanResponse;
import com.flowforge.ai.dto.FlowExecutionStepResponse;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.dto.FlowRunTraceResponse;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.repository.FlowNodeArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlowNodeArtifactService {

    private final FlowNodeArtifactRepository artifactRepository;
    private final FlowExecutionCompiler flowExecutionCompiler;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<FlowNodeArtifact> persist(
            Task task,
            FlowRunSnapshotResponse snapshot,
            FlowRunTraceResponse trace,
            OpenAiTaskResult result
    ) {
        if (trace == null || trace.nodes() == null) {
            return List.of();
        }
        flowExecutionCompiler.validateFailurePolicy(trace);

        List<FlowNodeArtifact> artifacts = new ArrayList<>();
        Map<String, FlowNodeArtifactResponse> priorArtifacts = new LinkedHashMap<>();
        for (int index = 0; index < trace.nodes().size(); index++) {
            FlowNodeRunTraceResponse node = trace.nodes().get(index);
            FlowNodeArtifactResponse artifact = node.outputArtifact();
            if (artifact == null) {
                continue;
            }
            String payload = payloadFor(node, result);
            verifyArtifactPayload(artifact, payload);
            ArtifactInputLineage lineage = resolveInputLineage(
                    snapshot,
                    trace,
                    index,
                    node,
                    artifact,
                    priorArtifacts
            );
            ProviderCallProvenance providerCall = resolveProviderCall(task, trace, index, node, artifact);
            artifacts.add(FlowNodeArtifact.builder()
                    .taskId(task.getId())
                    .flowId(trace.flowId())
                    .nodeId(node.nodeId())
                    .sequenceNumber(index + 1)
                    .artifactKey(artifact.key())
                    .artifactType(artifact.type())
                    .state(artifact.state())
                    .mediaType(mediaTypeFor(node.nodeType()))
                    .payload(payload)
                    .contentFingerprint(artifact.contentFingerprint())
                    .inputArtifactKey(lineage == null ? null : lineage.artifact().key())
                    .inputArtifactType(lineage == null ? null : lineage.artifact().type())
                    .inputArtifactStorage(lineage == null ? null : lineage.artifact().storage())
                    .inputArtifactState(lineage == null ? null : lineage.state())
                    .inputResolution(lineage == null ? null : lineage.resolution())
                    .inputContentFingerprint(lineage == null ? null : lineage.contentFingerprint())
                    .providerCallStatus(providerCall == null ? null : providerCall.status())
                    .providerName(providerCall == null ? null : providerCall.provider())
                    .providerModel(providerCall == null ? null : providerCall.model())
                    .providerInputTokens(providerCall == null ? null : providerCall.inputTokens())
                    .providerOutputTokens(providerCall == null ? null : providerCall.outputTokens())
                    .providerTotalTokens(providerCall == null ? null : providerCall.totalTokens())
                    .providerDurationMs(providerCall == null ? null : providerCall.durationMs())
                    .providerErrorMessage(providerCall == null ? null : providerCall.errorMessage())
                    .build());
            priorArtifacts.put(artifact.key(), artifact);
        }
        if (artifacts.isEmpty()) {
            return List.of();
        }
        return artifactRepository.saveAll(artifacts);
    }

    private ArtifactInputLineage resolveInputLineage(
            FlowRunSnapshotResponse snapshot,
            FlowRunTraceResponse trace,
            int index,
            FlowNodeRunTraceResponse node,
            FlowNodeArtifactResponse outputArtifact,
            Map<String, FlowNodeArtifactResponse> priorArtifacts
    ) {
        FlowExecutionPlanResponse plan = trace.executionPlan();
        if (plan == null || plan.steps() == null || index >= plan.steps().size()) {
            return null;
        }
        FlowExecutionStepResponse step = plan.steps().get(index);
        if (step.inputResolution() == null) {
            return null;
        }
        if (!FlowExecutionCompiler.INPUT_RESOLUTION.equals(step.inputResolution())) {
            throw new IllegalStateException("Unsupported Flow artifact input resolution");
        }
        if (!node.nodeId().equals(step.nodeId())) {
            throw new IllegalStateException("Flow artifact plan order does not match node trace");
        }
        verifyOutputContract(step.outputArtifact(), outputArtifact);

        FlowArtifactContractResponse inputArtifact = step.inputArtifact();
        if (inputArtifact == null) {
            throw new IllegalStateException("Flow artifact input contract is required");
        }
        if ("flow-snapshot".equals(inputArtifact.storage())) {
            if (snapshot == null || !trace.flowId().equals(snapshot.flowId())) {
                throw new IllegalStateException("Flow artifact input snapshot does not match run trace");
            }
            if (!"flow:objective".equals(inputArtifact.key())
                    || !"flow-objective".equals(inputArtifact.type())) {
                throw new IllegalStateException("Flow artifact objective contract is invalid");
            }
            return new ArtifactInputLineage(
                    inputArtifact,
                    "materialized",
                    step.inputResolution(),
                    flowExecutionCompiler.fingerprint(snapshot.description())
            );
        }
        if (!"node-artifact".equals(inputArtifact.storage())) {
            throw new IllegalStateException("Flow artifact input storage is not supported");
        }
        FlowNodeArtifactResponse priorArtifact = priorArtifacts.get(inputArtifact.key());
        if (priorArtifact == null
                || !inputArtifact.type().equals(priorArtifact.type())
                || !inputArtifact.storage().equals(priorArtifact.storage())) {
            throw new IllegalStateException("Flow artifact input does not resolve to a prior node output");
        }
        return new ArtifactInputLineage(
                inputArtifact,
                priorArtifact.state(),
                step.inputResolution(),
                priorArtifact.contentFingerprint()
        );
    }

    private void verifyOutputContract(
            FlowArtifactContractResponse plannedArtifact,
            FlowNodeArtifactResponse traceArtifact
    ) {
        if (plannedArtifact == null
                || !plannedArtifact.key().equals(traceArtifact.key())
                || !plannedArtifact.type().equals(traceArtifact.type())
                || !plannedArtifact.storage().equals(traceArtifact.storage())) {
            throw new IllegalStateException("Flow artifact output contract does not match node trace");
        }
    }

    private ProviderCallProvenance resolveProviderCall(
            Task task,
            FlowRunTraceResponse trace,
            int index,
            FlowNodeRunTraceResponse node,
            FlowNodeArtifactResponse artifact
    ) {
        FlowExecutionPlanResponse plan = trace.executionPlan();
        if (plan == null || plan.steps() == null || index >= plan.steps().size()) {
            return null;
        }
        FlowExecutionStepResponse step = plan.steps().get(index);
        if (step.inputResolution() == null) {
            return null;
        }
        if (!step.providerBoundary()) {
            if ("ai-task".equals(node.nodeType())) {
                throw new IllegalStateException("Flow AI Task artifact requires a Provider boundary");
            }
            return null;
        }
        if (!"ai-task".equals(node.nodeType()) || !"provider-result".equals(artifact.type())) {
            throw new IllegalStateException("Flow Provider boundary must produce an AI Task result artifact");
        }
        if (trace.providerCallCount() == null || trace.providerCallCount() != 1) {
            throw new IllegalStateException("Flow Provider provenance does not match the run call count");
        }
        if (task.getDurationMs() == null || task.getDurationMs() < 0) {
            throw new IllegalStateException("Flow Provider provenance requires a non-negative duration");
        }
        verifyNonNegativeTokenCount(task.getInputTokens());
        verifyNonNegativeTokenCount(task.getOutputTokens());
        verifyNonNegativeTokenCount(task.getTotalTokens());

        String status = task.getStatus();
        if (!status.equals(trace.status())) {
            throw new IllegalStateException("Flow Provider provenance does not match the run status");
        }
        if (Task.STATUS_COMPLETED.equals(status)) {
            if (!"materialized".equals(artifact.state()) || task.getErrorMessage() != null) {
                throw new IllegalStateException("Completed Flow Provider provenance does not match artifact state");
            }
        } else if (Task.STATUS_FAILED.equals(status)) {
            if (!"failed".equals(artifact.state())
                    || task.getErrorMessage() == null
                    || task.getErrorMessage().isBlank()) {
                throw new IllegalStateException("Failed Flow Provider provenance requires an error");
            }
        } else {
            throw new IllegalStateException("Flow Provider provenance requires a terminal Task status");
        }
        return new ProviderCallProvenance(
                status,
                task.getProvider(),
                task.getModel(),
                task.getInputTokens(),
                task.getOutputTokens(),
                task.getTotalTokens(),
                task.getDurationMs(),
                task.getErrorMessage()
        );
    }

    private void verifyNonNegativeTokenCount(Integer tokenCount) {
        if (tokenCount != null && tokenCount < 0) {
            throw new IllegalStateException("Flow Provider provenance token counts cannot be negative");
        }
    }

    private String payloadFor(FlowNodeRunTraceResponse node, OpenAiTaskResult result) {
        if (!"materialized".equals(node.outputArtifact().state())) {
            return null;
        }
        return switch (node.nodeType()) {
            case "input", "prompt" -> node.compiledContent();
            case "ai-task" -> result == null ? null : result.summary() + "\n" + result.result();
            case "output" -> result == null ? null : result.result();
            default -> throw new IllegalArgumentException("Unsupported Flow node type: " + node.nodeType());
        };
    }

    private void verifyArtifactPayload(FlowNodeArtifactResponse artifact, String payload) {
        if ("materialized".equals(artifact.state())) {
            if (payload == null || artifact.contentFingerprint() == null) {
                throw new IllegalStateException("Materialized Flow node artifact requires payload and fingerprint");
            }
            String actualFingerprint = flowExecutionCompiler.fingerprint(payload);
            if (!actualFingerprint.equals(artifact.contentFingerprint())) {
                throw new IllegalStateException("Flow node artifact fingerprint does not match its payload");
            }
            return;
        }
        if (payload != null || artifact.contentFingerprint() != null) {
            throw new IllegalStateException("Unmaterialized Flow node artifact cannot contain payload");
        }
    }

    private String mediaTypeFor(String nodeType) {
        return switch (nodeType) {
            case "input", "prompt" -> "text/plain";
            case "ai-task", "output" -> "text/markdown";
            default -> throw new IllegalArgumentException("Unsupported Flow node type: " + nodeType);
        };
    }

    private record ArtifactInputLineage(
            FlowArtifactContractResponse artifact,
            String state,
            String resolution,
            String contentFingerprint
    ) {
    }

    private record ProviderCallProvenance(
            String status,
            String provider,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            Long durationMs,
            String errorMessage
    ) {
    }
}
