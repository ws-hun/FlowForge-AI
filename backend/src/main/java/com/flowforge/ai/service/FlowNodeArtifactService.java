package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactResponse;
import com.flowforge.ai.dto.FlowNodeRunTraceResponse;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowNodeArtifactService {

    private final FlowNodeArtifactRepository artifactRepository;
    private final FlowExecutionCompiler flowExecutionCompiler;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<FlowNodeArtifact> persist(
            Task task,
            FlowRunTraceResponse trace,
            OpenAiTaskResult result
    ) {
        if (trace == null || trace.nodes() == null) {
            return List.of();
        }

        List<FlowNodeArtifact> artifacts = new ArrayList<>();
        for (int index = 0; index < trace.nodes().size(); index++) {
            FlowNodeRunTraceResponse node = trace.nodes().get(index);
            FlowNodeArtifactResponse artifact = node.outputArtifact();
            if (artifact == null) {
                continue;
            }
            String payload = payloadFor(node, result);
            verifyArtifactPayload(artifact, payload);
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
                    .build());
        }
        if (artifacts.isEmpty()) {
            return List.of();
        }
        return artifactRepository.saveAll(artifacts);
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
}
