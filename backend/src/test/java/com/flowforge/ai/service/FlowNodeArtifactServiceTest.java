package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeArtifactResponse;
import com.flowforge.ai.dto.FlowNodeRunTraceResponse;
import com.flowforge.ai.dto.FlowRunTraceResponse;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.entity.FlowNodeArtifact;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.repository.FlowNodeArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowNodeArtifactServiceTest {

    @Mock
    private FlowNodeArtifactRepository artifactRepository;

    private final FlowExecutionCompiler compiler = new FlowExecutionCompiler();
    private FlowNodeArtifactService artifactService;

    @BeforeEach
    void setUp() {
        artifactService = new FlowNodeArtifactService(artifactRepository, compiler);
    }

    @Test
    void persistsAddressablePayloadsInNodeOrder() {
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).build();
        OpenAiTaskResult result = new OpenAiTaskResult("Focused summary", "Detailed result", "{}");
        FlowRunTraceResponse trace = trace(flowId, List.of(
                node(
                        "input-1",
                        "input",
                        "Context",
                        "context-contribution",
                        "Product context",
                        compiler.fingerprint("Product context")
                ),
                node(
                        "ai-task-1",
                        "ai-task",
                        "Provider result",
                        "provider-result",
                        "Execution guidance",
                        compiler.fingerprint("Focused summary\nDetailed result")
                ),
                node(
                        "output-1",
                        "output",
                        "Result document",
                        "result-document",
                        "Delivery focus",
                        compiler.fingerprint("Detailed result")
                )
        ));
        when(artifactRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<FlowNodeArtifact> saved = artifactService.persist(task, trace, result);

        assertThat(saved).extracting(FlowNodeArtifact::getSequenceNumber).containsExactly(1, 2, 3);
        assertThat(saved).extracting(FlowNodeArtifact::getArtifactKey)
                .containsExactly(
                        "node:input-1:context-contribution",
                        "node:ai-task-1:provider-result",
                        "node:output-1:result-document"
                );
        assertThat(saved).extracting(FlowNodeArtifact::getPayload)
                .containsExactly("Product context", "Focused summary\nDetailed result", "Detailed result");
        assertThat(saved).extracting(FlowNodeArtifact::getMediaType)
                .containsExactly("text/plain", "text/markdown", "text/markdown");
        assertThat(saved).allSatisfy(artifact -> {
            assertThat(artifact.getTaskId()).isEqualTo(taskId);
            assertThat(artifact.getFlowId()).isEqualTo(flowId);
            assertThat(artifact.getState()).isEqualTo("materialized");
            assertThat(artifact.getContentFingerprint())
                    .isEqualTo(compiler.fingerprint(artifact.getPayload()));
        });
        verify(artifactRepository).saveAll(saved);
    }

    @Test
    void rejectsMaterializedPayloadWhenTheTraceFingerprintDoesNotMatch() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        FlowRunTraceResponse trace = trace(UUID.randomUUID(), List.of(node(
                "input-1",
                "input",
                "Context",
                "context-contribution",
                "Product context",
                "incorrect-fingerprint"
        )));

        assertThatThrownBy(() -> artifactService.persist(task, trace, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow node artifact fingerprint does not match its payload");

        verifyNoInteractions(artifactRepository);
    }

    private FlowRunTraceResponse trace(UUID flowId, List<FlowNodeRunTraceResponse> nodes) {
        return new FlowRunTraceResponse(
                UUID.randomUUID(),
                flowId,
                "completed",
                "single-pass",
                1,
                "flow-compiler-v1",
                "provider-input-fingerprint",
                "compiled-flow",
                null,
                null,
                nodes
        );
    }

    private FlowNodeRunTraceResponse node(
            String nodeId,
            String nodeType,
            String title,
            String artifactType,
            String compiledContent,
            String fingerprint
    ) {
        return new FlowNodeRunTraceResponse(
                nodeId,
                nodeType,
                title,
                "materialized",
                compiledContent,
                null,
                null,
                new FlowNodeArtifactResponse(
                        "node:" + nodeId + ":" + artifactType,
                        artifactType,
                        "node-artifact",
                        "materialized",
                        fingerprint
                )
        );
    }
}
