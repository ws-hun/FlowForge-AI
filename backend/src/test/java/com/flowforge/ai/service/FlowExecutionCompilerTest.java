package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlowExecutionCompilerTest {

    private final FlowExecutionCompiler compiler = new FlowExecutionCompiler();

    @Test
    void compilesThePersistedFlowIntoOneDeterministicProviderInput() {
        FlowRunSnapshotResponse snapshot = snapshot(Map.of("audience", "product teams"));

        FlowExecutionCompiler.Compilation compilation = compiler.compile(snapshot);

        assertThat(compilation.executionMode()).isEqualTo("single-pass");
        assertThat(compilation.providerCallCount()).isEqualTo(1);
        assertThat(compilation.executionInput())
                .contains("Flow: Launch workspace")
                .contains("本次运行上下文:\nShip a focused first release.")
                .contains("## Audience context\nPrioritize product teams.")
                .contains("## Launch prompt\nCreate a launch plan for product teams.")
                .contains("## AI Execution\nMake tradeoffs explicit for product teams.")
                .contains("## Structured Result\nEnd with next actions for product teams.")
                .endsWith("请输出：1. Summary 2. Key Points 3. Result 4. Next Actions");
        assertThat(compilation.sections())
                .extracting(section -> section.kind())
                .containsExactly(
                        "objective",
                        "input-context",
                        "runtime-context",
                        "prompt",
                        "execution-guidance",
                        "delivery-focus",
                        "response-contract"
                );
    }

    @Test
    void reportsMissingVariablesInFirstUseOrderAndPreservesUnresolvedTokens() {
        FlowRunSnapshotResponse snapshot = snapshot(Map.of("audience", ""));

        assertThat(compiler.findMissingVariables(snapshot)).containsExactly("audience");
        assertThat(compiler.applyVariables("For {audience} and {unknown}", Map.of("audience", "teams")))
                .isEqualTo("For teams and {unknown}");
    }

    private FlowRunSnapshotResponse snapshot(Map<String, String> variableValues) {
        return new FlowRunSnapshotResponse(
                UUID.randomUUID(),
                "Launch workspace",
                "Prepare a calm product launch",
                List.of(
                        node("input-1", "input", "Intent", "Prepare a calm product launch"),
                        node("input-2", "input", "Audience context", "Prioritize {audience}."),
                        node("prompt-1", "prompt", "Launch prompt", "Create a launch plan for {audience}."),
                        node("ai-task-1", "ai-task", "AI Execution", "Make tradeoffs explicit for {audience}."),
                        node("output-1", "output", "Structured Result", "End with next actions for {audience}.")
                ),
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 7, 29, 10, 0),
                "Ship a focused first release.",
                variableValues
        );
    }

    private FlowNodeDto node(String id, String type, String title, String content) {
        return new FlowNodeDto(id, type, title, title + " description", content, null, null);
    }
}
