package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowExecutionPreviewRequest;
import com.flowforge.ai.dto.FlowExecutionPreviewResponse;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.dto.RunTaskRequest;
import com.flowforge.ai.dto.TaskHistoryResponse;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Captor
    private ArgumentCaptor<String> executionInputCaptor;

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
                .nodesJson(objectMapper.writeValueAsString(List.of(
                        new FlowNodeDto(
                                "input-1",
                                "input",
                                "Product context",
                                "The starting product idea",
                                "Build a calm workspace for {audience}.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "input-2",
                                "input",
                                "Delivery constraints",
                                "A persisted supporting context node.",
                                "Keep the first release aligned with {audience}.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "prompt-1",
                                "prompt",
                                "Define the boundary",
                                "Make the MVP scope explicit",
                                "Use {audience} as the decision lens.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "ai-task-1",
                                "ai-task",
                                "AI execution guidance",
                                "Define how the model should deliver this Flow.",
                                "Prioritize concrete tradeoffs for {audience}.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "output-1",
                                "output",
                                "Delivery focus",
                                "Make the result useful after this run.",
                                "End with decisions that {audience} can act on immediately.",
                                null,
                                null
                        )
                )))
                .createdAt(updatedAt.minusDays(1))
                .updatedAt(updatedAt)
                .build();

        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(flow));
        when(openAiService.processTask(any()))
                .thenReturn(new OpenAiTaskResult(
                        "Focused MVP",
                        "Detailed result",
                        "{\"summary\":\"Focused MVP\"}",
                        "deepseek",
                        "deepseek-chat",
                        820,
                        430,
                        1250
                ));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        TaskRunResponse response = taskService.runTask(new RunTaskRequest(
                "untrusted browser payload",
                null,
                flowId,
                "Target early-stage product teams.",
                Map.of("audience", "product leads")
        ));

        verify(taskRepository).save(taskCaptor.capture());
        verify(openAiService).processTask(executionInputCaptor.capture());
        Task savedTask = taskCaptor.getValue();
        String executionInput = executionInputCaptor.getValue();
        assertThat(savedTask.getSourceFlowId()).isEqualTo(flowId);
        assertThat(savedTask.getSourceFlowSnapshotJson()).contains("Idea to MVP");
        assertThat(savedTask.getInput()).isEqualTo(executionInput);
        assertThat(savedTask.getProvider()).isEqualTo("deepseek");
        assertThat(savedTask.getModel()).isEqualTo("deepseek-chat");
        assertThat(savedTask.getInputTokens()).isEqualTo(820);
        assertThat(savedTask.getOutputTokens()).isEqualTo(430);
        assertThat(savedTask.getTotalTokens()).isEqualTo(1250);
        assertThat(executionInput)
                .contains("Flow: Idea to MVP")
                .contains("Build a calm workspace for product leads.")
                .contains("Keep the first release aligned with product leads.")
                .contains("Target early-stage product teams.")
                .contains("Use product leads as the decision lens.")
                .contains("执行指令:")
                .contains("Prioritize concrete tradeoffs for product leads.")
                .contains("交付重点:")
                .contains("End with decisions that product leads can act on immediately.")
                .doesNotContain("{audience}")
                .doesNotContain("untrusted browser payload");
        assertThat(executionInput.indexOf("Build a calm workspace for product leads."))
                .isLessThan(executionInput.indexOf("Keep the first release aligned with product leads."));
        assertThat(response.taskId()).isNotNull();
        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.model()).isEqualTo("deepseek-chat");
        assertThat(response.inputTokens()).isEqualTo(820);
        assertThat(response.outputTokens()).isEqualTo(430);
        assertThat(response.totalTokens()).isEqualTo(1250);
        assertThat(response.flowRunSnapshot()).isNotNull();
        assertThat(response.flowRunSnapshot().title()).isEqualTo("Idea to MVP");
        assertThat(response.flowRunSnapshot().flowUpdatedAt()).isEqualTo(updatedAt);
        assertThat(response.flowRunSnapshot().runtimeContext()).isEqualTo("Target early-stage product teams.");
        assertThat(response.flowRunSnapshot().variableValues()).containsEntry("audience", "product leads");
        assertThat(response.flowRunSnapshot().nodes()).extracting(FlowNodeDto::title)
                .containsExactly(
                        "Product context",
                        "Delivery constraints",
                        "Define the boundary",
                        "AI execution guidance",
                        "Delivery focus"
                );
    }

    @Test
    void preservesTheProvidedInputForStandaloneTasks() {
        when(openAiService.processTask("Draft an onboarding checklist"))
                .thenReturn(new OpenAiTaskResult("Onboarding", "Checklist", "{\"summary\":\"Onboarding\"}"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        taskService.runTask(new RunTaskRequest(
                "Draft an onboarding checklist",
                null,
                null,
                null,
                null
        ));

        verify(taskRepository).save(taskCaptor.capture());
        Task savedTask = taskCaptor.getValue();
        assertThat(savedTask.getInput()).isEqualTo("Draft an onboarding checklist");
        assertThat(savedTask.getSourceFlowId()).isNull();
        assertThat(savedTask.getSourceFlowSnapshotJson()).isNull();
    }

    @Test
    void returnsStoredExecutionProvenanceInTaskHistory() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 10, 15);
        UUID rerunOfTaskId = UUID.randomUUID();
        Task task = Task.builder()
                .id(UUID.randomUUID())
                .input("Prepare a launch brief")
                .summary("Launch brief prepared")
                .result("Detailed launch brief")
                .provider("openai")
                .model("gpt-4.1")
                .inputTokens(640)
                .outputTokens(360)
                .totalTokens(1000)
                .rerunOfTaskId(rerunOfTaskId)
                .createdAt(createdAt)
                .build();
        when(taskRepository.findAll(any(Sort.class))).thenReturn(List.of(task));

        List<TaskHistoryResponse> history = taskService.listTasks();

        assertThat(history).singleElement().satisfies(item -> {
            assertThat(item.provider()).isEqualTo("openai");
            assertThat(item.model()).isEqualTo("gpt-4.1");
            assertThat(item.inputTokens()).isEqualTo(640);
            assertThat(item.outputTokens()).isEqualTo(360);
            assertThat(item.totalTokens()).isEqualTo(1000);
            assertThat(item.rerunOfTaskId()).isEqualTo(rerunOfTaskId);
            assertThat(item.createdAt()).isEqualTo(createdAt);
        });
    }

    @Test
    void rerunsTheExactStoredExecutionInputAndPreservesItsSourceSnapshot() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        LocalDateTime flowUpdatedAt = LocalDateTime.of(2026, 7, 18, 9, 30);
        FlowRunSnapshotResponse snapshot = new FlowRunSnapshotResponse(
                flowId,
                "Launch decision",
                "Decide the first launch scope",
                List.of(new FlowNodeDto(
                        "input-1",
                        "input",
                        "Launch context",
                        "The fixed context for this run",
                        "Prepare the launch for product teams.",
                        null,
                        null
                )),
                flowUpdatedAt,
                "Keep the first release focused.",
                Map.of("audience", "product teams")
        );
        String snapshotJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(snapshot);
        Task sourceTask = Task.builder()
                .id(sourceTaskId)
                .input("Exact server-compiled execution input")
                .summary("Original result")
                .result("Original content")
                .provider("deepseek")
                .model("deepseek-chat")
                .sourceFlowId(flowId)
                .sourceFlowTitle("Launch decision")
                .sourceFlowSnapshotJson(snapshotJson)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        when(taskRepository.findById(sourceTaskId)).thenReturn(Optional.of(sourceTask));
        when(openAiService.processTask("Exact server-compiled execution input"))
                .thenReturn(new OpenAiTaskResult(
                        "Current provider result",
                        "New content",
                        "{}",
                        "openai",
                        "gpt-4.1",
                        500,
                        250,
                        750
                ));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        TaskRunResponse response = taskService.rerunTask(sourceTaskId);

        verify(openAiService).processTask("Exact server-compiled execution input");
        verify(taskRepository).save(taskCaptor.capture());
        Task rerun = taskCaptor.getValue();
        assertThat(rerun).isNotSameAs(sourceTask);
        assertThat(rerun.getInput()).isEqualTo(sourceTask.getInput());
        assertThat(rerun.getSourceFlowId()).isEqualTo(flowId);
        assertThat(rerun.getSourceFlowTitle()).isEqualTo("Launch decision");
        assertThat(rerun.getSourceFlowSnapshotJson()).contains("Keep the first release focused.");
        assertThat(rerun.getRerunOfTaskId()).isEqualTo(sourceTaskId);
        assertThat(rerun.getProvider()).isEqualTo("openai");
        assertThat(rerun.getModel()).isEqualTo("gpt-4.1");
        assertThat(response.executionInput()).isEqualTo(sourceTask.getInput());
        assertThat(response.rerunOfTaskId()).isEqualTo(sourceTaskId);
        assertThat(response.flowRunSnapshot()).isEqualTo(snapshot);
        assertThat(response.totalTokens()).isEqualTo(750);
        verifyNoInteractions(promptRepository, workflowRepository);
    }

    @Test
    void rejectsRerunWhenTheSourceTaskDoesNotExist() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.rerunTask(taskId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Task run not found");

        verifyNoInteractions(openAiService, promptRepository, workflowRepository);
    }

    @Test
    void allowsAnEmptyRunBriefForSavedFlowRuns() throws Exception {
        UUID flowId = UUID.randomUUID();
        Workflow flow = Workflow.builder()
                .id(flowId)
                .title("Empty Brief Flow")
                .description("Run from saved Flow state")
                .nodesJson(new ObjectMapper().writeValueAsString(List.of()))
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();

        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(flow));
        when(openAiService.processTask(any()))
                .thenReturn(new OpenAiTaskResult("Completed", "Result", "{\"summary\":\"Completed\"}"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        taskService.runTask(new RunTaskRequest("", null, flowId, null, null));

        verify(openAiService).processTask(executionInputCaptor.capture());
        assertThat(executionInputCaptor.getValue())
                .contains("Flow: Empty Brief Flow")
                .doesNotContain("本次运行上下文:");
    }

    @Test
    void previewsTheServerCompiledInputWithoutRunningOrPersistingATask() throws Exception {
        UUID flowId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 15, 9, 45);
        Workflow flow = Workflow.builder()
                .id(flowId)
                .title("Release Brief")
                .description("Prepare a focused launch brief")
                .nodesJson(new ObjectMapper().writeValueAsString(List.of(
                        new FlowNodeDto(
                                "input-1",
                                "input",
                                "Product context",
                                "Saved Flow context",
                                "Create a calm release workspace for {audience}.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "prompt-1",
                                "prompt",
                                "Audience lens",
                                "Use the filled runtime variable",
                                "Write for {audience} and include a release checklist.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "ai-task-1",
                                "ai-task",
                                "Launch execution guidance",
                                "Saved creative direction for this Flow.",
                                "Keep the deliverable decisive for {audience}.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "output-1",
                                "output",
                                "Release delivery focus",
                                "Saved outcome standard for this Flow.",
                                "Leave {audience} with a sequence they can start today.",
                                null,
                                null
                        )
                )))
                .createdAt(updatedAt.minusDays(2))
                .updatedAt(updatedAt)
                .build();
        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(flow));

        FlowExecutionPreviewResponse response = taskService.previewFlowExecution(
                flowId,
                new FlowExecutionPreviewRequest(
                        "Keep the first release intentionally small.",
                        Map.of("audience", "product teams")
                )
        );

        assertThat(response.executionInput())
                .contains("Flow: Release Brief")
                .contains("Create a calm release workspace for product teams.")
                .contains("Keep the first release intentionally small.")
                .contains("Write for product teams and include a release checklist.")
                .contains("执行指令:")
                .contains("Keep the deliverable decisive for product teams.")
                .contains("交付重点:")
                .contains("Leave product teams with a sequence they can start today.")
                .doesNotContain("{audience}")
                .doesNotContain("untrusted browser node");
        assertThat(response.flowRunSnapshot().flowId()).isEqualTo(flowId);
        assertThat(response.flowRunSnapshot().flowUpdatedAt()).isEqualTo(updatedAt);
        assertThat(response.flowRunSnapshot().nodes()).extracting(FlowNodeDto::title)
                .containsExactly("Product context", "Audience lens", "Launch execution guidance", "Release delivery focus");
        verifyNoInteractions(openAiService, taskRepository);
    }

    @Test
    void rejectsAFlowRunWhenRequiredVariablesAreMissing() throws Exception {
        UUID flowId = UUID.randomUUID();
        Workflow flow = Workflow.builder()
                .id(flowId)
                .title("Audience brief")
                .description("Prepare a brief for a specific audience")
                .nodesJson(new ObjectMapper().writeValueAsString(List.of(
                        new FlowNodeDto(
                                "input-1",
                                "input",
                                "Audience context",
                                "The audience for this run",
                                "Prepare this for {audience} in {region}.",
                                null,
                                null
                        )
                )))
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();
        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(flow));

        assertThatThrownBy(() -> taskService.runTask(new RunTaskRequest(
                "",
                null,
                flowId,
                "Keep it concise.",
                Map.of("audience", "product teams", "region", "   ")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请填写 Flow 变量: region");

        verifyNoInteractions(openAiService, taskRepository);
    }

    @Test
    void rejectsAFlowRunWhenNodeContentIsIncomplete() throws Exception {
        UUID flowId = UUID.randomUUID();
        Workflow flow = Workflow.builder()
                .id(flowId)
                .title("Incomplete research flow")
                .description("Prepare a focused research brief")
                .nodesJson(new ObjectMapper().writeValueAsString(List.of(
                        new FlowNodeDto(
                                "input-1",
                                "input",
                                "Research intent",
                                "The goal for this Flow",
                                "Research the selected product category.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "input-2",
                                "input",
                                "Market context",
                                "Supporting context that still needs to be written",
                                "   ",
                                null,
                                null
                        )
                )))
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();
        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(flow));

        assertThatThrownBy(() -> taskService.runTask(new RunTaskRequest(
                "",
                null,
                flowId,
                "Keep the research practical.",
                Map.of()
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请完善 Flow 节点: Market context");

        verifyNoInteractions(openAiService, taskRepository);
    }

    @Test
    void rejectsPreviewForAMissingFlow() {
        UUID flowId = UUID.randomUUID();
        when(workflowRepository.findById(flowId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.previewFlowExecution(
                flowId,
                new FlowExecutionPreviewRequest(null, Map.of())
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flow not found");

        verifyNoInteractions(openAiService, taskRepository);
    }

    @Test
    void rejectsEmptyStandaloneTaskInput() {
        assertThatThrownBy(() -> taskService.runTask(new RunTaskRequest(
                "   ",
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("input is required");

        verifyNoInteractions(openAiService, taskRepository);
    }
}
