package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.dto.RunTaskRequest;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.repository.PromptRepository;
import com.flowforge.ai.repository.TaskRepository;
import com.flowforge.ai.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private OpenAiService openAiService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private PromptRepository promptRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Captor
    private ArgumentCaptor<Task> taskCaptor;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        taskService = new TaskService(
                openAiService,
                taskRepository,
                promptRepository,
                workflowRepository,
                objectMapper
        );
    }

    @Test
    void capturesTheFlowStateAndRuntimeInputsWhenRunningAFlow() throws Exception {
        UUID flowId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 14, 10, 30);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Workflow flow = Workflow.builder()
                .id(flowId)
                .title("Idea to MVP")
                .description("Turn an idea into a focused MVP")
                .nodesJson(objectMapper.writeValueAsString(List.of(new FlowNodeDto(
                        "prompt-1",
                        "prompt",
                        "Define the boundary",
                        "Make the MVP scope explicit",
                        "Use {audience} as the decision lens.",
                        null,
                        null
                ))))
                .createdAt(updatedAt.minusDays(1))
                .updatedAt(updatedAt)
                .build();

        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(flow));
        when(openAiService.processTask("compiled flow input"))
                .thenReturn(new OpenAiTaskResult("Focused MVP", "Detailed result", "{\"summary\":\"Focused MVP\"}"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        TaskRunResponse response = taskService.runTask(new RunTaskRequest(
                "compiled flow input",
                null,
                flowId,
                "Target early-stage product teams.",
                Map.of("audience", "product leads")
        ));

        verify(taskRepository).save(taskCaptor.capture());
        Task savedTask = taskCaptor.getValue();
        assertThat(savedTask.getSourceFlowId()).isEqualTo(flowId);
        assertThat(savedTask.getSourceFlowSnapshotJson()).contains("Idea to MVP");
        assertThat(response.taskId()).isNotNull();
        assertThat(response.flowRunSnapshot()).isNotNull();
        assertThat(response.flowRunSnapshot().title()).isEqualTo("Idea to MVP");
        assertThat(response.flowRunSnapshot().flowUpdatedAt()).isEqualTo(updatedAt);
        assertThat(response.flowRunSnapshot().runtimeContext()).isEqualTo("Target early-stage product teams.");
        assertThat(response.flowRunSnapshot().variableValues()).containsEntry("audience", "product leads");
        assertThat(response.flowRunSnapshot().nodes()).singleElement().extracting(FlowNodeDto::title)
                .isEqualTo("Define the boundary");
    }
}
