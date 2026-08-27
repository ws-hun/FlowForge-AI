package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowExecutionPreviewRequest;
import com.flowforge.ai.dto.FlowExecutionPreviewResponse;
import com.flowforge.ai.dto.FlowExecutionPlanResponse;
import com.flowforge.ai.dto.FlowArtifactContractResponse;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowNodeRunTraceResponse;
import com.flowforge.ai.dto.FlowNodeArtifactResponse;
import com.flowforge.ai.dto.FlowRunTraceResponse;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.dto.RunTaskRequest;
import com.flowforge.ai.dto.TaskHistoryResponse;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.entity.Prompt;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.entity.Workflow;
import com.flowforge.ai.exception.AiExecutionException;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.PromptRepository;
import com.flowforge.ai.repository.TaskRepository;
import com.flowforge.ai.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final String FLOW_INPUT_SOURCE_COMPILED = "compiled-flow";
    private static final String FLOW_INPUT_SOURCE_REPLAY = "stored-input-replay";
    private static final String FLOW_INPUT_SOURCE_RECOVERY = "stored-input-recovery";

    private final OpenAiService openAiService;
    private final TaskRepository taskRepository;
    private final PromptRepository promptRepository;
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;
    private final TaskFailureRecorder taskFailureRecorder;
    private final FlowExecutionCompiler flowExecutionCompiler;
    private final FlowNodeArtifactService flowNodeArtifactService;

    @Transactional
    public TaskRunResponse runTask(RunTaskRequest request) {
        Prompt sourcePrompt = resolveSourcePrompt(request);
        Workflow sourceFlow = resolveSourceFlow(request);
        Task continuedFromTask = resolveContinuedFromTask(request);
        Task inputVariantSourceTask = resolveInputVariantSourceTask(request);
        int sourceCount = (sourcePrompt == null ? 0 : 1)
                + (sourceFlow == null ? 0 : 1)
                + (continuedFromTask == null ? 0 : 1)
                + (inputVariantSourceTask == null ? 0 : 1);
        if (sourceCount > 1) {
            throw new IllegalArgumentException("A task run can only use one source");
        }
        String standaloneInput = cleanOptional(request.input());
        if (sourceFlow == null && continuedFromTask == null && !StringUtils.hasText(standaloneInput)) {
            throw new IllegalArgumentException("input is required");
        }
        if (continuedFromTask != null && !StringUtils.hasText(standaloneInput)) {
            throw new IllegalArgumentException("continuation input is required");
        }
        FlowRunSnapshotResponse flowRunSnapshot = null;
        if (sourceFlow != null) {
            flowRunSnapshot = createFlowRunSnapshot(sourceFlow, request.flowRunContext(), request.flowVariableValues());
        } else if (continuedFromTask != null) {
            flowRunSnapshot = deserializeFlowRunSnapshot(continuedFromTask.getSourceFlowSnapshotJson());
        }
        if (flowRunSnapshot != null && continuedFromTask == null) {
            requireFlowNodeContents(flowRunSnapshot);
            requireFlowVariableValues(flowRunSnapshot);
        }

        if (continuedFromTask != null) {
            return executeAndSave(
                    compileContinuationInput(continuedFromTask, standaloneInput),
                    new TaskExecutionSource(
                            UUID.randomUUID(),
                            continuedFromTask.getSourcePromptId(),
                            continuedFromTask.getSourcePromptTitle(),
                            continuedFromTask.getSourceFlowId(),
                            continuedFromTask.getSourceFlowTitle(),
                            flowRunSnapshot,
                            null,
                            null,
                            continuedFromTask.getId(),
                            null,
                            false,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    )
            );
        }

        FlowExecutionCompiler.Compilation compiledExecution = flowRunSnapshot == null
                ? null
                : flowExecutionCompiler.compile(flowRunSnapshot);
        String executionInput = compiledExecution == null
                ? standaloneInput
                : compiledExecution.executionInput();
        return executeAndSave(
                executionInput,
                new TaskExecutionSource(
                        UUID.randomUUID(),
                        sourcePrompt == null ? null : sourcePrompt.getId(),
                        sourcePrompt == null ? null : sourcePrompt.getTitle(),
                        sourceFlow == null ? null : sourceFlow.getId(),
                        sourceFlow == null ? null : sourceFlow.getTitle(),
                        flowRunSnapshot,
                        null,
                        null,
                        null,
                        inputVariantSourceTask == null ? null : inputVariantSourceTask.getId(),
                        sourceFlow != null,
                        compiledExecution == null ? null : compiledExecution.executionMode(),
                        compiledExecution == null ? null : compiledExecution.providerCallCount(),
                        compiledExecution == null ? null : compiledExecution.compilerVersion(),
                        compiledExecution == null ? null : compiledExecution.executionInputFingerprint(),
                        compiledExecution == null ? null : FLOW_INPUT_SOURCE_COMPILED,
                        null,
                        compiledExecution == null ? null : compiledExecution.plan()
                )
        );
    }

    @Transactional
    public TaskRunResponse rerunTask(UUID taskId) {
        Task sourceTask = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task run not found"));
        return replayTask(sourceTask, Task.STATUS_FAILED.equals(sourceTask.getStatus()));
    }

    @Transactional
    public TaskRunResponse recoverTask(UUID taskId) {
        Task sourceTask = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task run not found"));
        if (!Task.STATUS_FAILED.equals(sourceTask.getStatus())) {
            throw new IllegalArgumentException("Only failed task runs can be recovered");
        }
        return replayTask(sourceTask, true);
    }

    private TaskRunResponse replayTask(Task sourceTask, boolean recovery) {
        FlowRunSnapshotResponse flowRunSnapshot = deserializeFlowRunSnapshot(sourceTask.getSourceFlowSnapshotJson());
        FlowRunTraceResponse sourceTrace = deserializeFlowRunTrace(sourceTask.getFlowRunTraceJson());

        return executeAndSave(
                sourceTask.getInput(),
                new TaskExecutionSource(
                        UUID.randomUUID(),
                        sourceTask.getSourcePromptId(),
                        sourceTask.getSourcePromptTitle(),
                        sourceTask.getSourceFlowId(),
                        sourceTask.getSourceFlowTitle(),
                        flowRunSnapshot,
                        recovery ? null : sourceTask.getId(),
                        recovery ? sourceTask.getId() : null,
                        sourceTask.getContinuedFromTaskId(),
                        sourceTask.getInputVariantOfTaskId(),
                        flowRunSnapshot != null
                                && sourceTask.getContinuedFromTaskId() == null
                                && sourceTask.getInputVariantOfTaskId() == null,
                        sourceTrace == null ? FlowExecutionCompiler.EXECUTION_MODE : sourceTrace.executionMode(),
                        sourceTrace == null ? FlowExecutionCompiler.PROVIDER_CALL_COUNT : sourceTrace.providerCallCount(),
                        sourceTrace == null ? null : sourceTrace.compilerVersion(),
                        flowRunSnapshot == null ? null : flowExecutionCompiler.fingerprint(sourceTask.getInput()),
                        flowRunSnapshot == null
                                ? null
                                : recovery ? FLOW_INPUT_SOURCE_RECOVERY : FLOW_INPUT_SOURCE_REPLAY,
                        flowRunSnapshot == null ? null : sourceTask.getId(),
                        sourceTrace == null ? null : sourceTrace.executionPlan()
                )
        );
    }

    private TaskRunResponse executeAndSave(String executionInput, TaskExecutionSource source) {
        long startedAt = System.nanoTime();
        OpenAiTaskResult aiResult;
        try {
            aiResult = openAiService.processTask(executionInput);
        } catch (RuntimeException ex) {
            UUID failedRunId = recordFailedExecution(executionInput, source, ex, elapsedMillis(startedAt));
            if (failedRunId != null && ex instanceof AiExecutionException aiException) {
                aiException.attachRunId(failedRunId);
            }
            throw ex;
        }
        long durationMs = elapsedMillis(startedAt);
        FlowRunTraceResponse flowRunTrace = buildFlowRunTrace(source, aiResult, null);

        Task task = createTaskBuilder(executionInput, source, flowRunTrace)
                .summary(aiResult.summary())
                .result(aiResult.result())
                .provider(aiResult.provider())
                .model(aiResult.model())
                .inputTokens(aiResult.inputTokens())
                .outputTokens(aiResult.outputTokens())
                .totalTokens(aiResult.totalTokens())
                .durationMs(durationMs)
                .status(Task.STATUS_COMPLETED)
                .build();

        Task savedTask = taskRepository.save(task);
        if (flowRunTrace != null) {
            flowNodeArtifactService.persist(savedTask, source.flowRunSnapshot(), flowRunTrace, aiResult);
        }

        return new TaskRunResponse(
                aiResult.summary(),
                aiResult.result(),
                aiResult.raw(),
                aiResult.provider(),
                aiResult.model(),
                aiResult.inputTokens(),
                aiResult.outputTokens(),
                aiResult.totalTokens(),
                durationMs,
                source.rerunOfTaskId(),
                source.recoveryOfTaskId(),
                source.continuedFromTaskId(),
                source.inputVariantOfTaskId(),
                executionInput,
                savedTask.getId(),
                source.flowRunSnapshot(),
                flowRunTrace
        );
    }

    private UUID recordFailedExecution(
            String executionInput,
            TaskExecutionSource source,
            RuntimeException exception,
            long durationMs
    ) {
        String errorMessage = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : "AI Provider execution failed";
        String provider = exception instanceof AiExecutionException aiException
                ? aiException.getProvider()
                : null;
        String model = exception instanceof AiExecutionException aiException
                ? aiException.getModel()
                : null;
        FlowRunTraceResponse flowRunTrace = buildFlowRunTrace(source, null, errorMessage);
        Task failedTask = createTaskBuilder(executionInput, source, flowRunTrace)
                .summary("AI 执行失败")
                .result(errorMessage)
                .provider(provider)
                .model(model)
                .durationMs(durationMs)
                .status(Task.STATUS_FAILED)
                .errorMessage(errorMessage)
                .build();
        try {
            taskFailureRecorder.record(failedTask, source.flowRunSnapshot(), flowRunTrace);
            return failedTask.getId();
        } catch (RuntimeException persistenceFailure) {
            exception.addSuppressed(persistenceFailure);
            return null;
        }
    }

    private Task.TaskBuilder createTaskBuilder(
            String executionInput,
            TaskExecutionSource source,
            FlowRunTraceResponse flowRunTrace
    ) {
        return Task.builder()
                .id(source.taskId())
                .input(executionInput)
                .rerunOfTaskId(source.rerunOfTaskId())
                .recoveryOfTaskId(source.recoveryOfTaskId())
                .continuedFromTaskId(source.continuedFromTaskId())
                .inputVariantOfTaskId(source.inputVariantOfTaskId())
                .sourcePromptId(source.promptId())
                .sourcePromptTitle(source.promptTitle())
                .sourceFlowId(source.flowId())
                .sourceFlowTitle(source.flowTitle())
                .sourceFlowSnapshotJson(serializeFlowRunSnapshot(source.flowRunSnapshot()))
                .flowRunTraceJson(serializeFlowRunTrace(flowRunTrace));
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    @Transactional(readOnly = true)
    public FlowExecutionPreviewResponse previewFlowExecution(UUID flowId, FlowExecutionPreviewRequest request) {
        Workflow flow = workflowRepository.findById(flowId)
                .orElseThrow(() -> new ResourceNotFoundException("Flow not found"));
        FlowRunSnapshotResponse flowRunSnapshot = createFlowRunSnapshot(
                flow,
                request.runtimeContext(),
                request.variableValues()
        );

        FlowExecutionCompiler.Compilation compiledExecution = flowExecutionCompiler.compile(flowRunSnapshot);
        List<String> missingVariables = flowExecutionCompiler.findMissingVariables(flowRunSnapshot);
        List<String> incompleteNodes = findIncompleteFlowNodes(flowRunSnapshot);

        return new FlowExecutionPreviewResponse(
                compiledExecution.executionMode(),
                compiledExecution.providerCallCount(),
                compiledExecution.compilerVersion(),
                compiledExecution.executionInputFingerprint(),
                compiledExecution.executionInput(),
                flowRunSnapshot,
                compiledExecution.sections(),
                compiledExecution.plan(),
                missingVariables.isEmpty() && incompleteNodes.isEmpty(),
                missingVariables,
                incompleteNodes
        );
    }

    @Transactional(readOnly = true)
    public List<TaskHistoryResponse> listTasks() {
        return taskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskHistoryResponse> listPromptRuns(UUID promptId) {
        return taskRepository.findTop6BySourcePromptIdOrderByCreatedAtDesc(promptId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskHistoryResponse> listFlowRuns(UUID flowId) {
        return taskRepository.findTop6BySourceFlowIdOrderByCreatedAtDesc(flowId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private Prompt resolveSourcePrompt(RunTaskRequest request) {
        if (request.promptId() == null) {
            return null;
        }
        return promptRepository.findById(request.promptId())
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));
    }

    private Workflow resolveSourceFlow(RunTaskRequest request) {
        if (request.flowId() == null) {
            return null;
        }
        return workflowRepository.findById(request.flowId())
                .orElseThrow(() -> new ResourceNotFoundException("Flow not found"));
    }

    private Task resolveContinuedFromTask(RunTaskRequest request) {
        if (request.continuedFromTaskId() == null) {
            return null;
        }
        return taskRepository.findById(request.continuedFromTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Continuation source task not found"));
    }

    private Task resolveInputVariantSourceTask(RunTaskRequest request) {
        if (request.inputVariantOfTaskId() == null) {
            return null;
        }
        return taskRepository.findById(request.inputVariantOfTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Input variant source task not found"));
    }

    private FlowRunSnapshotResponse createFlowRunSnapshot(
            Workflow flow,
            String runtimeContext,
            Map<String, String> variableValues
    ) {
        return new FlowRunSnapshotResponse(
                flow.getId(),
                flow.getTitle(),
                flow.getDescription(),
                deserializeFlowNodes(flow.getNodesJson()),
                flow.getSourceFlowId(),
                flow.getSourceFlowTitle(),
                flow.getSourceFlowVersionId(),
                flow.getSourceFlowVersionNumber(),
                flow.getUpdatedAt(),
                cleanOptional(runtimeContext),
                cleanVariableValues(variableValues)
        );
    }

    private String compileContinuationInput(Task sourceTask, String direction) {
        return """
                请基于一次已完成的 AI 运行结果继续推进。

                来源摘要:
                %s

                来源结果:
                %s

                本次继续方向:
                %s

                请保留仍然有效的内容，并针对本次方向输出清晰、可执行的新版本。
                """.formatted(sourceTask.getSummary(), sourceTask.getResult(), direction).trim();
    }

    private void requireFlowVariableValues(FlowRunSnapshotResponse snapshot) {
        List<String> missingVariables = flowExecutionCompiler.findMissingVariables(snapshot);
        if (!missingVariables.isEmpty()) {
            throw new IllegalArgumentException("请填写 Flow 变量: " + String.join(", ", missingVariables));
        }
    }

    private void requireFlowNodeContents(FlowRunSnapshotResponse snapshot) {
        List<String> incompleteNodes = findIncompleteFlowNodes(snapshot);
        if (!incompleteNodes.isEmpty()) {
            throw new IllegalArgumentException("请完善 Flow 节点: " + String.join(", ", incompleteNodes));
        }
    }

    private List<String> findIncompleteFlowNodes(FlowRunSnapshotResponse snapshot) {
        return snapshot.nodes().stream()
                .filter(node -> !StringUtils.hasText(node.content()))
                .map(FlowNodeDto::title)
                .toList();
    }

    private String serializeFlowRunSnapshot(FlowRunSnapshotResponse snapshot) {
        if (snapshot == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to save flow run snapshot", ex);
        }
    }

    private FlowRunSnapshotResponse deserializeFlowRunSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(snapshotJson, FlowRunSnapshotResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read flow run snapshot", ex);
        }
    }

    private FlowRunTraceResponse buildFlowRunTrace(
            TaskExecutionSource source,
            OpenAiTaskResult result,
            String errorMessage
    ) {
        FlowRunSnapshotResponse snapshot = source.flowRunSnapshot();
        if (!source.flowExecution() || snapshot == null) {
            return null;
        }

        boolean completed = result != null;
        List<FlowNodeRunTraceResponse> nodes = snapshot.nodes().stream()
                .map(node -> buildFlowNodeRunTrace(
                        node,
                        outputArtifactContract(source.executionPlan(), node.id()),
                        snapshot.variableValues(),
                        result,
                        errorMessage
                ))
                .toList();
        FlowRunTraceResponse trace = new FlowRunTraceResponse(
                source.taskId(),
                snapshot.flowId(),
                completed ? Task.STATUS_COMPLETED : Task.STATUS_FAILED,
                source.executionMode(),
                source.providerCallCount(),
                source.compilerVersion(),
                source.executionInputFingerprint(),
                source.inputSource(),
                source.replayedFromTaskId(),
                source.executionPlan(),
                nodes
        );
        flowExecutionCompiler.validateFailurePolicy(trace);
        return trace;
    }

    private FlowNodeRunTraceResponse buildFlowNodeRunTrace(
            FlowNodeDto node,
            FlowArtifactContractResponse outputArtifactContract,
            Map<String, String> variableValues,
            OpenAiTaskResult result,
            String errorMessage
    ) {
        boolean completed = result != null;
        String status = switch (node.type()) {
            case "ai-task" -> completed ? "completed" : "failed";
            case "output" -> completed ? "completed" : "skipped";
            default -> "prepared";
        };
        String outputSummary = completed && ("ai-task".equals(node.type()) || "output".equals(node.type()))
                ? result.summary()
                : null;
        String nodeError = "ai-task".equals(node.type()) && !completed ? errorMessage : null;
        String compiledContent = flowExecutionCompiler.applyVariables(node.content(), variableValues);
        return new FlowNodeRunTraceResponse(
                node.id(),
                node.type(),
                node.title(),
                status,
                compiledContent,
                outputSummary,
                nodeError,
                buildNodeOutputArtifact(node, outputArtifactContract, compiledContent, result)
        );
    }

    private FlowArtifactContractResponse outputArtifactContract(FlowExecutionPlanResponse plan, String nodeId) {
        if (plan == null || plan.steps() == null) {
            return null;
        }
        return plan.steps().stream()
                .filter(step -> nodeId.equals(step.nodeId()))
                .filter(step -> step.outputArtifact() != null)
                .map(step -> step.outputArtifact())
                .findFirst()
                .orElse(null);
    }

    private FlowNodeArtifactResponse buildNodeOutputArtifact(
            FlowNodeDto node,
            FlowArtifactContractResponse contract,
            String compiledContent,
            OpenAiTaskResult result
    ) {
        if (contract == null) {
            return null;
        }

        String state = switch (node.type()) {
            case "input", "prompt" -> "materialized";
            case "ai-task" -> result == null ? "failed" : "materialized";
            case "output" -> result == null ? "skipped" : "materialized";
            default -> throw new IllegalArgumentException("Unsupported Flow node type: " + node.type());
        };
        String fingerprintSource = switch (node.type()) {
            case "input", "prompt" -> compiledContent;
            case "ai-task" -> result == null ? null : result.summary() + "\n" + result.result();
            case "output" -> result == null ? null : result.result();
            default -> throw new IllegalArgumentException("Unsupported Flow node type: " + node.type());
        };
        return new FlowNodeArtifactResponse(
                contract.key(),
                contract.type(),
                contract.storage(),
                state,
                fingerprintSource == null ? null : flowExecutionCompiler.fingerprint(fingerprintSource)
        );
    }

    private String serializeFlowRunTrace(FlowRunTraceResponse trace) {
        if (trace == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(trace);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to save Flow run trace", ex);
        }
    }

    private FlowRunTraceResponse deserializeFlowRunTrace(String traceJson) {
        if (traceJson == null || traceJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(traceJson, FlowRunTraceResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read Flow run trace", ex);
        }
    }

    private List<FlowNodeDto> deserializeFlowNodes(String nodesJson) {
        try {
            return objectMapper.readValue(
                    nodesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FlowNodeDto.class)
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read flow run nodes", ex);
        }
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    private Map<String, String> cleanVariableValues(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<String, String> cleanedValues = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                cleanedValues.put(key.trim(), value == null ? "" : value.trim());
            }
        });
        return Map.copyOf(cleanedValues);
    }

    private TaskHistoryResponse toHistoryResponse(Task task) {
        return new TaskHistoryResponse(
                task.getId(),
                task.getInput(),
                task.getSummary(),
                task.getResult(),
                task.getProvider(),
                task.getModel(),
                task.getInputTokens(),
                task.getOutputTokens(),
                task.getTotalTokens(),
                task.getDurationMs(),
                task.getRerunOfTaskId(),
                task.getRecoveryOfTaskId(),
                task.getContinuedFromTaskId(),
                task.getInputVariantOfTaskId(),
                StringUtils.hasText(task.getStatus()) ? task.getStatus() : Task.STATUS_COMPLETED,
                task.getErrorMessage(),
                task.getSourcePromptId(),
                task.getSourcePromptTitle(),
                task.getSourceFlowId(),
                task.getSourceFlowTitle(),
                deserializeFlowRunSnapshot(task.getSourceFlowSnapshotJson()),
                deserializeFlowRunTrace(task.getFlowRunTraceJson()),
                task.getCreatedAt()
        );
    }

    private record TaskExecutionSource(
            UUID taskId,
            UUID promptId,
            String promptTitle,
            UUID flowId,
            String flowTitle,
            FlowRunSnapshotResponse flowRunSnapshot,
            UUID rerunOfTaskId,
            UUID recoveryOfTaskId,
            UUID continuedFromTaskId,
            UUID inputVariantOfTaskId,
            boolean flowExecution,
            String executionMode,
            Integer providerCallCount,
            String compilerVersion,
            String executionInputFingerprint,
            String inputSource,
            UUID replayedFromTaskId,
            FlowExecutionPlanResponse executionPlan
    ) {
    }

}
