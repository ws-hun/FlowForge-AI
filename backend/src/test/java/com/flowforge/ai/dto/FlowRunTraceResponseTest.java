package com.flowforge.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
        assertThat(trace.executionMode()).isNull();
        assertThat(trace.providerCallCount()).isEqualTo(1);
    }
}
