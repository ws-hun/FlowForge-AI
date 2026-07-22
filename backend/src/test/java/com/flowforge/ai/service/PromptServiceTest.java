package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.PromptRequest;
import com.flowforge.ai.dto.PromptResponse;
import com.flowforge.ai.entity.Prompt;
import com.flowforge.ai.entity.PromptVersion;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.repository.PromptRepository;
import com.flowforge.ai.repository.PromptVersionRepository;
import com.flowforge.ai.repository.TaskRepository;
import com.flowforge.ai.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptServiceTest {

    @Mock
    private PromptRepository promptRepository;

    @Mock
    private PromptVersionRepository promptVersionRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    private ObjectMapper objectMapper;
    private PromptService promptService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        promptService = new PromptService(
                promptRepository,
                promptVersionRepository,
                taskRepository,
                workflowRepository,
                objectMapper
        );
    }

    @Test
    void createsPromptWithImmutableTaskLineage() {
        UUID taskId = UUID.randomUUID();
        UUID sourcePromptId = UUID.randomUUID();
        UUID sourceFlowId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .input("Compiled input")
                .summary("A reusable execution result")
                .result("Result")
                .status(Task.STATUS_COMPLETED)
                .sourcePromptId(sourcePromptId)
                .sourcePromptTitle("Product brief")
                .sourceFlowId(sourceFlowId)
                .sourceFlowTitle("Idea to MVP")
                .createdAt(LocalDateTime.now())
                .build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        stubPromptSave();

        PromptResponse response = promptService.createPrompt(request(taskId, null, null));

        assertThat(response.sourceTaskId()).isEqualTo(taskId);
        assertThat(response.sourceTaskSummary()).isEqualTo("A reusable execution result");
        assertThat(response.sourcePromptId()).isEqualTo(sourcePromptId);
        assertThat(response.sourcePromptTitle()).isEqualTo("Product brief");
        assertThat(response.sourceFlowId()).isEqualTo(sourceFlowId);
        assertThat(response.sourceFlowTitle()).isEqualTo("Idea to MVP");
        verify(workflowRepository, never()).findById(any());
    }

    @Test
    void resolvesFlowNodeLineageFromTheSavedFlow() throws Exception {
        UUID flowId = UUID.randomUUID();
        UUID sourcePromptId = UUID.randomUUID();
        FlowNodeDto sourceNode = new FlowNodeDto(
                "prompt-node",
                "prompt",
                "Refine the brief",
                "Turn the brief into a delivery plan",
                "Prompt content",
                sourcePromptId,
                "Product brief"
        );
        Workflow workflow = Workflow.builder()
                .id(flowId)
                .title("Idea to MVP")
                .description("Build an MVP plan")
                .nodesJson(objectMapper.writeValueAsString(List.of(sourceNode)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(workflow));
        stubPromptSave();

        PromptResponse response = promptService.createPrompt(request(null, flowId, sourceNode.id()));

        assertThat(response.sourceFlowId()).isEqualTo(flowId);
        assertThat(response.sourceFlowTitle()).isEqualTo("Idea to MVP");
        assertThat(response.sourceNodeId()).isEqualTo(sourceNode.id());
        assertThat(response.sourceNodeTitle()).isEqualTo("Refine the brief");
        assertThat(response.sourcePromptId()).isEqualTo(sourcePromptId);
        assertThat(response.sourcePromptTitle()).isEqualTo("Product brief");
    }

    @Test
    void rejectsAmbiguousPromptSources() {
        PromptRequest request = request(UUID.randomUUID(), UUID.randomUUID(), "prompt-node");

        assertThatThrownBy(() -> promptService.createPrompt(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt source must be either a Task or a Flow node");
        verify(promptRepository, never()).save(any(Prompt.class));
    }

    @Test
    void requiresAFlowNodeForFlowProvenance() {
        PromptRequest request = request(null, UUID.randomUUID(), null);

        assertThatThrownBy(() -> promptService.createPrompt(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceNodeId is required when sourceFlowId is provided");
        verify(promptRepository, never()).save(any(Prompt.class));
    }

    @Test
    void preservesPromptLineageWhenContentIsEdited() {
        UUID promptId = UUID.randomUUID();
        UUID sourceTaskId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Original title")
                .category("Flow Output")
                .description("Original description")
                .content("Original content")
                .tags("Flow,Result")
                .sourceTaskId(sourceTaskId)
                .sourceTaskSummary("Original run")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .updatedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(promptVersionRepository.findTopByPromptIdOrderByVersionNumberDesc(promptId)).thenReturn(Optional.empty());
        when(promptVersionRepository.save(any(PromptVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromptResponse response = promptService.updatePrompt(
                promptId,
                new PromptRequest(
                        "Refined title",
                        "Flow Output",
                        "Refined description",
                        "Refined content",
                        List.of("Flow", "Reusable"),
                        true,
                        UUID.randomUUID(),
                        null,
                        null
                )
        );

        assertThat(response.title()).isEqualTo("Refined title");
        assertThat(response.sourceTaskId()).isEqualTo(sourceTaskId);
        assertThat(response.sourceTaskSummary()).isEqualTo("Original run");
    }

    private PromptRequest request(UUID sourceTaskId, UUID sourceFlowId, String sourceNodeId) {
        return new PromptRequest(
                "Reusable work pattern",
                "Flow Output",
                "A reusable way to continue the work",
                "Prompt content",
                List.of("Flow", "Reusable"),
                false,
                sourceTaskId,
                sourceFlowId,
                sourceNodeId
        );
    }

    private void stubPromptSave() {
        when(promptRepository.save(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            LocalDateTime now = LocalDateTime.now();
            prompt.setId(UUID.randomUUID());
            prompt.setCreatedAt(now);
            prompt.setUpdatedAt(now);
            return prompt;
        });
    }
}
