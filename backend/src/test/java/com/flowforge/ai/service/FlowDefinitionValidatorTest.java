package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowDefinitionValidatorTest {

    private final FlowDefinitionValidator validator = new FlowDefinitionValidator();

    @Test
    void acceptsACanonicalSingleProviderFlow() {
        assertThatCode(() -> validator.validate(List.of(
                node("input-1", "input"),
                node("input-2", "input"),
                node("prompt-1", "prompt"),
                node("prompt-2", "prompt"),
                node("ai-task-1", "ai-task"),
                node("output-1", "output")
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateIdsUnknownTypesAndInvalidExecutionShape() {
        assertThatThrownBy(() -> validator.validate(List.of(
                node("input-1", "input"),
                node("input-1", "ai-task"),
                node("output-1", "output")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flow 节点 ID 重复: input-1");

        assertThatThrownBy(() -> validator.validate(List.of(
                node("input-1", "input"),
                node("agent-1", "agent"),
                node("ai-task-1", "ai-task"),
                node("output-1", "output")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flow 节点类型不支持: agent");

        assertThatThrownBy(() -> validator.validate(List.of(
                node("input-1", "input"),
                node("output-1", "output"),
                node("ai-task-1", "ai-task")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flow 节点顺序应为 Input、Prompt、AI Task、Output");

        assertThatThrownBy(() -> validator.validate(List.of(
                node("input-1", "input"),
                node("output-1", "output")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前 Flow 运行模型需要且只支持一个 AI Task 节点");
    }

    @Test
    void rejectsNullOrIncompleteNodeMetadataButAllowsEmptyContentForPreflight() {
        assertThatThrownBy(() -> validator.validate(Arrays.asList(
                null,
                node("input-1", "input"),
                node("ai-task-1", "ai-task"),
                node("output-1", "output")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flow 节点不能为空");

        FlowNodeDto missingTitle = new FlowNodeDto(
                "input-1", "input", " ", "Saved description", "", null, null
        );
        assertThatThrownBy(() -> validator.validate(List.of(
                missingTitle,
                node("ai-task-1", "ai-task"),
                node("output-1", "output")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flow 节点标题不能为空: input-1");

        FlowNodeDto missingDescription = new FlowNodeDto(
                "input-1", "input", "Input", " ", "", null, null
        );
        assertThatThrownBy(() -> validator.validate(List.of(
                missingDescription,
                node("ai-task-1", "ai-task"),
                node("output-1", "output")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flow 节点说明不能为空: input-1");

        assertThatCode(() -> validator.validate(List.of(
                new FlowNodeDto("input-1", "input", "Input", "Description", "", null, null),
                node("ai-task-1", "ai-task"),
                node("output-1", "output")
        ))).doesNotThrowAnyException();
    }

    private FlowNodeDto node(String id, String type) {
        return new FlowNodeDto(id, type, id, "Flow node", "Content", null, null);
    }
}
