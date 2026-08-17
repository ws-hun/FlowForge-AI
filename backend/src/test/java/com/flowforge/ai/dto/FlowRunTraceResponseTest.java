package com.flowforge.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRunTraceResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsLegacySinglePassTracesWithoutAnExecutionModeField() throws Exception {
        UUID flowId = UUID.randomUUID();
        String legacyTrace = """
                {
                  "flowId": "%s",
                  "status": "completed",
                  "providerCallCount": 1,
                  "nodes": []
                }
                """.formatted(flowId);

        FlowRunTraceResponse trace = objectMapper.readValue(legacyTrace, FlowRunTraceResponse.class);

        assertThat(trace.flowId()).isEqualTo(flowId);
        assertThat(trace.runId()).isNull();
        assertThat(trace.executionMode()).isNull();
        assertThat(trace.providerCallCount()).isEqualTo(1);
        assertThat(trace.compilerVersion()).isNull();
        assertThat(trace.executionInputFingerprint()).isNull();
        assertThat(trace.inputSource()).isNull();
        assertThat(trace.replayedFromTaskId()).isNull();
        assertThat(trace.executionPlan()).isNull();
        assertThat(trace.nodes()).isEmpty();
    }

    @Test
    void preservesModernRunIdentityAndReplayProvenanceAcrossJsonRoundTrips() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID sourceRunId = UUID.randomUUID();
        FlowRunTraceResponse trace = new FlowRunTraceResponse(
                runId,
                flowId,
                "completed",
                "single-pass",
                1,
                "flow-compiler-v1",
                "1f4a8b6c",
                "stored-input-replay",
                sourceRunId,
                new FlowExecutionPlanResponse(
                        "flow-plan-v1",
                        "linear",
                        List.of(new FlowExecutionStepResponse(
                                1,
                                "ai-task-1",
                                "ai-task",
                                "Decision analysis",
                                "invoke-provider",
                                List.of(),
                                true
                        ))
                ),
                List.of(new FlowNodeRunTraceResponse(
                        "ai-task-1",
                        "ai-task",
                        "Decision analysis",
                        "completed",
                        "Compare the options.",
                        "Option A is preferred.",
                        null
                ))
        );

        FlowRunTraceResponse restored = objectMapper.readValue(
                objectMapper.writeValueAsString(trace),
                FlowRunTraceResponse.class
        );

        assertThat(restored).isEqualTo(trace);
        assertThat(restored.runId()).isEqualTo(runId);
        assertThat(restored.replayedFromTaskId()).isEqualTo(sourceRunId);
        assertThat(restored.executionPlan().version()).isEqualTo("flow-plan-v1");
        assertThat(restored.executionPlan().steps().get(0).inputArtifact()).isNull();
        assertThat(restored.executionPlan().steps().get(0).outputArtifact()).isNull();
        assertThat(restored.nodes()).singleElement().satisfies(node -> {
            assertThat(node.nodeId()).isEqualTo("ai-task-1");
            assertThat(node.outputSummary()).isEqualTo("Option A is preferred.");
            assertThat(node.outputArtifact()).isNull();
        });
    }

    @Test
    void preservesV2ArtifactContractsAndMaterializedOutputsAcrossJsonRoundTrips() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FlowArtifactContractResponse inputArtifact = new FlowArtifactContractResponse(
                "flow:objective",
                "flow-objective",
                "flow-snapshot"
        );
        FlowArtifactContractResponse outputArtifact = new FlowArtifactContractResponse(
                "node:input-1:context-contribution",
                "context-contribution",
                "trace-content"
        );
        FlowRunTraceResponse trace = new FlowRunTraceResponse(
                runId,
                flowId,
                "completed",
                "single-pass",
                1,
                "flow-compiler-v1",
                "provider-input-fingerprint",
                "compiled-flow",
                null,
                new FlowExecutionPlanResponse(
                        "flow-plan-v2",
                        "linear",
                        List.of(new FlowExecutionStepResponse(
                                1,
                                "input-1",
                                "input",
                                "Launch context",
                                "supply-context",
                                List.of(),
                                false,
                                inputArtifact,
                                outputArtifact
                        ))
                ),
                List.of(new FlowNodeRunTraceResponse(
                        "input-1",
                        "input",
                        "Launch context",
                        "prepared",
                        "Prepare the launch.",
                        null,
                        null,
                        new FlowNodeArtifactResponse(
                                outputArtifact.key(),
                                outputArtifact.type(),
                                outputArtifact.storage(),
                                "materialized",
                                "42f34ab4"
                        )
                ))
        );

        FlowRunTraceResponse restored = objectMapper.readValue(
                objectMapper.writeValueAsString(trace),
                FlowRunTraceResponse.class
        );

        assertThat(restored).isEqualTo(trace);
        assertThat(restored.executionPlan().version()).isEqualTo("flow-plan-v2");
        assertThat(restored.executionPlan().steps().get(0).inputArtifact()).isEqualTo(inputArtifact);
        assertThat(restored.executionPlan().steps().get(0).outputArtifact()).isEqualTo(outputArtifact);
        assertThat(restored.nodes().get(0).outputArtifact().state()).isEqualTo("materialized");
        assertThat(restored.nodes().get(0).outputArtifact().contentFingerprint()).isEqualTo("42f34ab4");
    }
}
