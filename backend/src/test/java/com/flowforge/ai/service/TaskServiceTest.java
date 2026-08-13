package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowforge.ai.dto.FlowExecutionPreviewRequest;
import com.flowforge.ai.dto.FlowExecutionPreviewResponse;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowNodeRunTraceResponse;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.dto.FlowRunTraceResponse;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.dto.RunTaskRequest;
import com.flowforge.ai.dto.TaskHistoryResponse;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.exception.AiExecutionException;
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
import static org.mockito.Mockito.never;
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

    @Mock
    private TaskFailureRecorder taskFailureRecorder;

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
                objectMapper,
                taskFailureRecorder,
                new FlowExecutionCompiler()
        );
    }

    @Test
    void capturesTheFlowStateAndRuntimeInputsWhenRunningAFlow() throws Exception {
        UUID flowId = UUID.randomUUID();
        UUID sourceFlowId = UUID.randomUUID();
        UUID sourceFlowVersionId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 14, 10, 30);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Workflow flow = Workflow.builder()
                .id(flowId)
                .title("Idea to MVP")
                .description("Turn an idea into a focused MVP")
                .sourceFlowId(sourceFlowId)
                .sourceFlowTitle("Product discovery")
                .sourceFlowVersionId(sourceFlowVersionId)
                .sourceFlowVersionNumber(3)
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
        assertThat(savedTask.getFlowRunTraceJson()).contains("\"executionMode\":\"single-pass\"");
        assertThat(savedTask.getFlowRunTraceJson()).contains("\"providerCallCount\":1");
        assertThat(savedTask.getInput()).isEqualTo(executionInput);
        assertThat(savedTask.getProvider()).isEqualTo("deepseek");
        assertThat(savedTask.getModel()).isEqualTo("deepseek-chat");
        assertThat(savedTask.getStatus()).isEqualTo(Task.STATUS_COMPLETED);
        assertThat(savedTask.getInputTokens()).isEqualTo(820);
        assertThat(savedTask.getOutputTokens()).isEqualTo(430);
        assertThat(savedTask.getTotalTokens()).isEqualTo(1250);
        assertThat(savedTask.getDurationMs()).isNotNegative();
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
        assertThat(response.durationMs()).isEqualTo(savedTask.getDurationMs());
        assertThat(response.flowRunSnapshot()).isNotNull();
        assertThat(response.flowRunSnapshot().title()).isEqualTo("Idea to MVP");
        assertThat(response.flowRunSnapshot().sourceFlowId()).isEqualTo(sourceFlowId);
        assertThat(response.flowRunSnapshot().sourceFlowTitle()).isEqualTo("Product discovery");
        assertThat(response.flowRunSnapshot().sourceFlowVersionId()).isEqualTo(sourceFlowVersionId);
        assertThat(response.flowRunSnapshot().sourceFlowVersionNumber()).isEqualTo(3);
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
        assertThat(response.flowRunTrace()).isNotNull();
        assertThat(response.flowRunTrace().flowId()).isEqualTo(flowId);
        assertThat(response.flowRunTrace().status()).isEqualTo(Task.STATUS_COMPLETED);
        assertThat(response.flowRunTrace().providerCallCount()).isEqualTo(1);
        assertThat(response.flowRunTrace().executionMode()).isEqualTo("single-pass");
        assertThat(response.flowRunTrace().compilerVersion()).isEqualTo("flow-compiler-v1");
        assertThat(response.flowRunTrace().executionInputFingerprint())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(new FlowExecutionCompiler().fingerprint(executionInput));
        assertThat(response.flowRunTrace().nodes())
                .extracting(node -> node.title() + ":" + node.status())
                .containsExactly(
                        "Product context:prepared",
                        "Delivery constraints:prepared",
                        "Define the boundary:prepared",
                        "AI execution guidance:completed",
                        "Delivery focus:completed"
                );
        assertThat(response.flowRunTrace().nodes().get(2).compiledContent())
                .isEqualTo("Use product leads as the decision lens.");
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
        assertThat(savedTask.getFlowRunTraceJson()).isNull();
    }

    @Test
    void returnsStoredExecutionProvenanceInTaskHistory() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 10, 15);
        UUID rerunOfTaskId = UUID.randomUUID();
        UUID continuedFromTaskId = UUID.randomUUID();
        UUID inputVariantOfTaskId = UUID.randomUUID();
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
                .durationMs(2450L)
                .rerunOfTaskId(rerunOfTaskId)
                .continuedFromTaskId(continuedFromTaskId)
                .inputVariantOfTaskId(inputVariantOfTaskId)
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
            assertThat(item.durationMs()).isEqualTo(2450L);
            assertThat(item.rerunOfTaskId()).isEqualTo(rerunOfTaskId);
            assertThat(item.continuedFromTaskId()).isEqualTo(continuedFromTaskId);
            assertThat(item.inputVariantOfTaskId()).isEqualTo(inputVariantOfTaskId);
            assertThat(item.status()).isEqualTo(Task.STATUS_COMPLETED);
            assertThat(item.errorMessage()).isNull();
            assertThat(item.createdAt()).isEqualTo(createdAt);
        });
    }

    @Test
    void runsAnEditedInputVariantWithoutClaimingTheOriginalFlowSnapshot() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID sourceFlowId = UUID.randomUUID();
        FlowRunSnapshotResponse snapshot = new FlowRunSnapshotResponse(
                sourceFlowId,
                "Launch plan",
                "Prepare a launch plan",
                List.of(),
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 7, 21, 9, 30),
                "Original Flow run brief",
                Map.of()
        );
        String snapshotJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(snapshot);
        Task sourceTask = Task.builder()
                .id(sourceTaskId)
                .input("Original server-compiled Flow input")
                .summary("Original launch plan")
                .result("Original result")
                .sourceFlowId(sourceFlowId)
                .sourceFlowTitle("Launch plan")
                .sourceFlowSnapshotJson(snapshotJson)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        when(taskRepository.findById(sourceTaskId)).thenReturn(Optional.of(sourceTask));
        when(openAiService.processTask("Edited standalone execution input"))
                .thenReturn(new OpenAiTaskResult("Variant result", "Variant content", "{}"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        TaskRunResponse response = taskService.runTask(new RunTaskRequest(
                "Edited standalone execution input",
                null,
                null,
                null,
                null,
                null,
                sourceTaskId
        ));

        verify(openAiService).processTask("Edited standalone execution input");
        verify(taskRepository).save(taskCaptor.capture());
        Task inputVariant = taskCaptor.getValue();
        assertThat(inputVariant.getInputVariantOfTaskId()).isEqualTo(sourceTaskId);
        assertThat(inputVariant.getInput()).isEqualTo("Edited standalone execution input");
        assertThat(inputVariant.getSourceFlowId()).isNull();
        assertThat(inputVariant.getSourceFlowSnapshotJson()).isNull();
        assertThat(response.inputVariantOfTaskId()).isEqualTo(sourceTaskId);
        assertThat(response.executionInput()).isEqualTo("Edited standalone execution input");
        assertThat(response.flowRunSnapshot()).isNull();
        assertThat(response.flowRunTrace()).isNull();
        verifyNoInteractions(promptRepository, workflowRepository);
    }

    @Test
    void recordsAProviderFailureWithoutLosingTheExecutionContext() {
        AiExecutionException failure = new AiExecutionException(
                "deepseek",
                "deepseek-chat",
                "AI API error: rate limit exceeded",
                new IllegalStateException("rate limit exceeded")
        );
        when(openAiService.processTask("Prepare a release plan")).thenThrow(failure);

        assertThatThrownBy(() -> taskService.runTask(new RunTaskRequest(
                "Prepare a release plan",
                null,
                null,
                null,
                null
        )))
                .isSameAs(failure);

        verify(taskFailureRecorder).record(taskCaptor.capture());
        Task failedTask = taskCaptor.getValue();
        assertThat(failedTask.getInput()).isEqualTo("Prepare a release plan");
        assertThat(failedTask.getSummary()).isEqualTo("AI 执行失败");
        assertThat(failedTask.getResult()).isEqualTo("AI API error: rate limit exceeded");
        assertThat(failedTask.getProvider()).isEqualTo("deepseek");
        assertThat(failedTask.getModel()).isEqualTo("deepseek-chat");
        assertThat(failedTask.getStatus()).isEqualTo(Task.STATUS_FAILED);
        assertThat(failedTask.getErrorMessage()).isEqualTo("AI API error: rate limit exceeded");
        assertThat(failedTask.getDurationMs()).isNotNegative();
        verify(taskRepository, never()).save(any(Task.class));
        verifyNoInteractions(promptRepository, workflowRepository);
    }

    @Test
    void continuesFromTheStoredResultAndPreservesItsCreativeSource() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID sourceFlowId = UUID.randomUUID();
        FlowRunSnapshotResponse snapshot = new FlowRunSnapshotResponse(
                sourceFlowId,
                "Product direction",
                "Shape a product direction",
                List.of(),
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 7, 19, 11, 20),
                "Prioritize a calm first release.",
                Map.of()
        );
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ObjectNode legacySnapshot = objectMapper.valueToTree(snapshot);
        legacySnapshot.remove(List.of(
                "sourceFlowId",
                "sourceFlowTitle",
                "sourceFlowVersionId",
                "sourceFlowVersionNumber"
        ));
        String snapshotJson = objectMapper.writeValueAsString(legacySnapshot);
        Task sourceTask = Task.builder()
                .id(sourceTaskId)
                .input("Original execution input")
                .summary("A focused product direction")
                .result("The first release should solve one clear workflow problem.")
                .sourceFlowId(sourceFlowId)
                .sourceFlowTitle("Product direction")
                .sourceFlowSnapshotJson(snapshotJson)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
        when(taskRepository.findById(sourceTaskId)).thenReturn(Optional.of(sourceTask));
        when(openAiService.processTask(any()))
                .thenReturn(new OpenAiTaskResult("Risk review", "Updated direction", "{}"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        TaskRunResponse response = taskService.runTask(new RunTaskRequest(
                "补充主要风险和一周验证计划",
                null,
                null,
                null,
                null,
                sourceTaskId
        ));

        verify(openAiService).processTask(executionInputCaptor.capture());
        verify(taskRepository).save(taskCaptor.capture());
        String executionInput = executionInputCaptor.getValue();
        Task continuedTask = taskCaptor.getValue();
        assertThat(executionInput)
                .contains("A focused product direction")
                .contains("The first release should solve one clear workflow problem.")
                .contains("补充主要风险和一周验证计划")
                .doesNotContain("Original execution input");
        assertThat(continuedTask.getContinuedFromTaskId()).isEqualTo(sourceTaskId);
        assertThat(continuedTask.getRerunOfTaskId()).isNull();
        assertThat(continuedTask.getSourceFlowId()).isEqualTo(sourceFlowId);
        assertThat(continuedTask.getSourceFlowSnapshotJson()).contains("Prioritize a calm first release.");
        assertThat(response.continuedFromTaskId()).isEqualTo(sourceTaskId);
        assertThat(response.flowRunSnapshot()).isEqualTo(snapshot);
        assertThat(response.flowRunTrace()).isNull();
        verifyNoInteractions(promptRepository, workflowRepository);
    }

    @Test
    void recordsAFailedFlowRunTraceAndSkipsTheOutputNode() throws Exception {
        UUID flowId = UUID.randomUUID();
        Workflow flow = Workflow.builder()
                .id(flowId)
                .title("Release review")
                .description("Review release readiness")
                .nodesJson(new ObjectMapper().writeValueAsString(List.of(
                        new FlowNodeDto(
                                "input-1",
                                "input",
                                "Release context",
                                "The release context",
                                "Review the release for {audience}.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "ai-task-1",
                                "ai-task",
                                "Readiness analysis",
                                "Analyze release readiness",
                                "Identify blocking risks.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "output-1",
                                "output",
                                "Release decision",
                                "Record the decision",
                                "Return a go or no-go decision.",
                                null,
                                null
                        )
                )))
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .updatedAt(LocalDateTime.now())
                .build();
        AiExecutionException failure = new AiExecutionException(
                "deepseek",
                "deepseek-chat",
                "AI API error: provider unavailable",
                new IllegalStateException("provider unavailable")
        );
        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(flow));
        when(openAiService.processTask(any())).thenThrow(failure);

        assertThatThrownBy(() -> taskService.runTask(new RunTaskRequest(
                "",
                null,
                flowId,
                "",
                Map.of("audience", "product teams")
        )))
                .isSameAs(failure);

        verify(taskFailureRecorder).record(taskCaptor.capture());
        Task failedTask = taskCaptor.getValue();
        assertThat(failedTask.getFlowRunTraceJson()).isNotBlank();
        FlowRunTraceResponse trace = new ObjectMapper()
                .findAndRegisterModules()
                .readValue(failedTask.getFlowRunTraceJson(), FlowRunTraceResponse.class);
        assertThat(trace.status()).isEqualTo(Task.STATUS_FAILED);
        assertThat(trace.executionMode()).isEqualTo("single-pass");
        assertThat(trace.providerCallCount()).isEqualTo(1);
        assertThat(trace.compilerVersion()).isEqualTo("flow-compiler-v1");
        assertThat(trace.executionInputFingerprint())
                .isEqualTo(new FlowExecutionCompiler().fingerprint(failedTask.getInput()));
        assertThat(trace.nodes())
                .extracting(node -> node.title() + ":" + node.status())
                .containsExactly(
                        "Release context:prepared",
                        "Readiness analysis:failed",
                        "Release decision:skipped"
                );
        assertThat(trace.nodes().get(1).errorMessage()).isEqualTo("AI API error: provider unavailable");
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void rebuildsAFlowRunTraceWhenRerunningAnOlderDirectFlowRecord() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FlowRunSnapshotResponse snapshot = new FlowRunSnapshotResponse(
                flowId,
                "Decision brief",
                "Prepare a decision brief",
                List.of(
                        new FlowNodeDto("input-1", "input", "Decision context", "Context", "Assess option A.", null, null),
                        new FlowNodeDto("ai-task-1", "ai-task", "Decision analysis", "Analysis", "Compare tradeoffs.", null, null),
                        new FlowNodeDto("output-1", "output", "Decision output", "Output", "Recommend one option.", null, null)
                ),
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                "",
                Map.of()
        );
        String snapshotJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(snapshot);
        Task sourceTask = Task.builder()
                .id(sourceTaskId)
                .input("Stored direct Flow input")
                .summary("Original decision")
                .result("Original result")
                .sourceFlowId(flowId)
                .sourceFlowTitle("Decision brief")
                .sourceFlowSnapshotJson(snapshotJson)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
        when(taskRepository.findById(sourceTaskId)).thenReturn(Optional.of(sourceTask));
        when(openAiService.processTask("Stored direct Flow input"))
                .thenReturn(new OpenAiTaskResult("Updated decision", "Updated result", "{}"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        TaskRunResponse response = taskService.rerunTask(sourceTaskId);

        assertThat(response.flowRunTrace()).isNotNull();
        assertThat(response.flowRunTrace().providerCallCount()).isEqualTo(1);
        assertThat(response.flowRunTrace().compilerVersion()).isNull();
        assertThat(response.flowRunTrace().executionInputFingerprint())
                .isEqualTo(new FlowExecutionCompiler().fingerprint("Stored direct Flow input"));
        assertThat(response.flowRunTrace().nodes())
                .extracting(FlowNodeRunTraceResponse::status)
                .containsExactly("prepared", "completed", "completed");
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getFlowRunTraceJson()).contains("Updated decision");
    }

    @Test
    void preservesCompilerVersionAndRecalculatesFingerprintWhenRerunningADirectFlowRecord() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FlowRunSnapshotResponse snapshot = new FlowRunSnapshotResponse(
                flowId,
                "Launch decision",
                "Decide the first launch scope",
                List.of(new FlowNodeDto(
                        "ai-task-1",
                        "ai-task",
                        "Launch analysis",
                        "Analysis",
                        "Recommend the first launch scope.",
                        null,
                        null
                )),
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 7, 18, 9, 30),
                "Keep the first release focused.",
                Map.of()
        );
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        String sourceTraceJson = objectMapper.writeValueAsString(new FlowRunTraceResponse(
                flowId,
                Task.STATUS_COMPLETED,
                "single-pass",
                1,
                "flow-compiler-v1",
                "source-fingerprint",
                List.of()
        ));
        Task sourceTask = Task.builder()
                .id(sourceTaskId)
                .input("Exact server-compiled execution input")
                .summary("Original result")
                .result("Original content")
                .sourceFlowId(flowId)
                .sourceFlowTitle("Launch decision")
                .sourceFlowSnapshotJson(snapshotJson)
                .flowRunTraceJson(sourceTraceJson)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        when(taskRepository.findById(sourceTaskId)).thenReturn(Optional.of(sourceTask));
        when(openAiService.processTask(sourceTask.getInput()))
                .thenReturn(new OpenAiTaskResult("Current decision", "Current result", "{}"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        TaskRunResponse response = taskService.rerunTask(sourceTaskId);

        assertThat(response.flowRunTrace()).isNotNull();
        assertThat(response.flowRunTrace().compilerVersion()).isEqualTo("flow-compiler-v1");
        assertThat(response.flowRunTrace().executionInputFingerprint())
                .isEqualTo(new FlowExecutionCompiler().fingerprint(sourceTask.getInput()))
                .isNotEqualTo("source-fingerprint");
    }

    @Test
    void rerunsTheExactStoredExecutionInputAndPreservesItsSourceSnapshot() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID continuationAncestorId = UUID.randomUUID();
        UUID inputVariantAncestorId = UUID.randomUUID();
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
                null,
                null,
                null,
                null,
                flowUpdatedAt,
                "Keep the first release focused.",
                Map.of("audience", "product teams")
        );
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        String sourceTraceJson = objectMapper.writeValueAsString(new FlowRunTraceResponse(
                flowId,
                Task.STATUS_COMPLETED,
                "single-pass",
                1,
                "flow-compiler-v1",
                "source-fingerprint",
                List.of()
        ));
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
                .flowRunTraceJson(sourceTraceJson)
                .continuedFromTaskId(continuationAncestorId)
                .inputVariantOfTaskId(inputVariantAncestorId)
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
        assertThat(rerun.getContinuedFromTaskId()).isEqualTo(continuationAncestorId);
        assertThat(rerun.getInputVariantOfTaskId()).isEqualTo(inputVariantAncestorId);
        assertThat(rerun.getProvider()).isEqualTo("openai");
        assertThat(rerun.getModel()).isEqualTo("gpt-4.1");
        assertThat(response.executionInput()).isEqualTo(sourceTask.getInput());
        assertThat(response.rerunOfTaskId()).isEqualTo(sourceTaskId);
        assertThat(response.continuedFromTaskId()).isEqualTo(continuationAncestorId);
        assertThat(response.inputVariantOfTaskId()).isEqualTo(inputVariantAncestorId);
        assertThat(response.flowRunSnapshot()).isEqualTo(snapshot);
        assertThat(response.totalTokens()).isEqualTo(750);
        assertThat(response.flowRunTrace()).isNull();
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
        assertThat(response.executionMode()).isEqualTo("single-pass");
        assertThat(response.providerCallCount()).isEqualTo(1);
        assertThat(response.compilerVersion()).isEqualTo("flow-compiler-v1");
        assertThat(response.executionInputFingerprint())
                .isEqualTo(new FlowExecutionCompiler().fingerprint(response.executionInput()));
        assertThat(response.flowRunSnapshot().nodes()).extracting(FlowNodeDto::title)
                .containsExactly("Product context", "Audience lens", "Launch execution guidance", "Release delivery focus");
        assertThat(response.executable()).isTrue();
        assertThat(response.missingVariables()).isEmpty();
        assertThat(response.incompleteNodes()).isEmpty();
        assertThat(response.sections())
                .extracting(section -> section.kind() + ":" + section.title())
                .containsExactly(
                        "objective:Release Brief",
                        "input-context:Product context",
                        "runtime-context:本次运行说明",
                        "prompt:Audience lens",
                        "execution-guidance:Launch execution guidance",
                        "delivery-focus:Release delivery focus",
                        "response-contract:结构化输出"
                );
        assertThat(response.sections().get(3).content())
                .isEqualTo("Write for product teams and include a release checklist.");
        verifyNoInteractions(openAiService, taskRepository);
    }

    @Test
    void reportsFlowPreviewReadinessWithoutCallingTheProvider() throws Exception {
        UUID flowId = UUID.randomUUID();
        Workflow flow = Workflow.builder()
                .id(flowId)
                .title("Regional launch")
                .description("Prepare a regional launch brief")
                .nodesJson(new ObjectMapper().writeValueAsString(List.of(
                        new FlowNodeDto(
                                "input-1",
                                "input",
                                "Audience context",
                                "The audience and region for this run",
                                "Prepare this for {audience} in {region}.",
                                null,
                                null
                        ),
                        new FlowNodeDto(
                                "prompt-1",
                                "prompt",
                                "Pending launch pattern",
                                "A reusable pattern that still needs content",
                                "   ",
                                null,
                                null
                        )
                )))
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();
        when(workflowRepository.findById(flowId)).thenReturn(Optional.of(flow));

        FlowExecutionPreviewResponse response = taskService.previewFlowExecution(
                flowId,
                new FlowExecutionPreviewRequest(null, Map.of("audience", "product teams"))
        );

        assertThat(response.executable()).isFalse();
        assertThat(response.missingVariables()).containsExactly("region");
        assertThat(response.incompleteNodes()).containsExactly("Pending launch pattern");
        assertThat(response.executionInput()).contains("{region}");
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
                .isInstanceOf(ResourceNotFoundException.class)
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
