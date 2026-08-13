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
        assertThat(restored.nodes()).singleElement().satisfies(node -> {
            assertThat(node.nodeId()).isEqualTo("ai-task-1");
            assertThat(node.outputSummary()).isEqualTo("Option A is preferred.");
        });
    }
}
