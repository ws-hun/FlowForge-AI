package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowExecutionPreviewRequest;
import com.flowforge.ai.dto.FlowExecutionPreviewResponse;
import com.flowforge.ai.dto.FlowExecutionSectionResponse;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowNodeRunTraceResponse;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final Pattern FLOW_VARIABLE_PATTERN = Pattern.compile("\\{[a-zA-Z0-9_\\u4e00-\\u9fa5-]+}");

    private final OpenAiService openAiService;
    private final TaskRepository taskRepository;
    private final PromptRepository promptRepository;
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;
    private final TaskFailureRecorder taskFailureRecorder;

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
                            continuedFromTask.getSourcePromptId(),
                            continuedFromTask.getSourcePromptTitle(),
                            continuedFromTask.getSourceFlowId(),
                            continuedFromTask.getSourceFlowTitle(),
                            flowRunSnapshot,
                            null,
                            continuedFromTask.getId(),
                            null,
                            false
                    )
            );
        }

        String executionInput = flowRunSnapshot == null
                ? standaloneInput
                : compileFlowExecution(flowRunSnapshot).executionInput();
        return executeAndSave(
                executionInput,
                new TaskExecutionSource(
                        sourcePrompt == null ? null : sourcePrompt.getId(),
                        sourcePrompt == null ? null : sourcePrompt.getTitle(),
                        sourceFlow == null ? null : sourceFlow.getId(),
                        sourceFlow == null ? null : sourceFlow.getTitle(),
                        flowRunSnapshot,
                        null,
                        null,
                        inputVariantSourceTask == null ? null : inputVariantSourceTask.getId(),
                        sourceFlow != null
                )
        );
    }

    @Transactional
    public TaskRunResponse rerunTask(UUID taskId) {
        Task sourceTask = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task run not found"));
        FlowRunSnapshotResponse flowRunSnapshot = deserializeFlowRunSnapshot(sourceTask.getSourceFlowSnapshotJson());

        return executeAndSave(
                sourceTask.getInput(),
                new TaskExecutionSource(
                        sourceTask.getSourcePromptId(),
                        sourceTask.getSourcePromptTitle(),
                        sourceTask.getSourceFlowId(),
                        sourceTask.getSourceFlowTitle(),
                        flowRunSnapshot,
                        sourceTask.getId(),
                        sourceTask.getContinuedFromTaskId(),
                        sourceTask.getInputVariantOfTaskId(),
                        flowRunSnapshot != null
                                && sourceTask.getContinuedFromTaskId() == null
                                && sourceTask.getInputVariantOfTaskId() == null
                )
        );
    }

    private TaskRunResponse executeAndSave(String executionInput, TaskExecutionSource source) {
        long startedAt = System.nanoTime();
        OpenAiTaskResult aiResult;
        try {
            aiResult = openAiService.processTask(executionInput);
        } catch (RuntimeException ex) {
            recordFailedExecution(executionInput, source, ex, elapsedMillis(startedAt));
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
                source.continuedFromTaskId(),
                source.inputVariantOfTaskId(),
                executionInput,
                savedTask.getId(),
                source.flowRunSnapshot(),
                flowRunTrace
        );
    }

    private void recordFailedExecution(
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
            taskFailureRecorder.record(failedTask);
        } catch (RuntimeException persistenceFailure) {
            exception.addSuppressed(persistenceFailure);
        }
    }

    private Task.TaskBuilder createTaskBuilder(
            String executionInput,
            TaskExecutionSource source,
            FlowRunTraceResponse flowRunTrace
    ) {
        return Task.builder()
                .input(executionInput)
                .rerunOfTaskId(source.rerunOfTaskId())
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
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        FlowRunSnapshotResponse flowRunSnapshot = createFlowRunSnapshot(
                flow,
                request.runtimeContext(),
                request.variableValues()
        );

        CompiledFlowExecution compiledExecution = compileFlowExecution(flowRunSnapshot);
        List<String> missingVariables = findMissingFlowVariables(flowRunSnapshot);
        List<String> incompleteNodes = findIncompleteFlowNodes(flowRunSnapshot);

        return new FlowExecutionPreviewResponse(
                compiledExecution.executionInput(),
                flowRunSnapshot,
                compiledExecution.sections(),
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
                .orElseThrow(() -> new IllegalStateException("Prompt not found"));
    }

    private Workflow resolveSourceFlow(RunTaskRequest request) {
        if (request.flowId() == null) {
            return null;
        }
        return workflowRepository.findById(request.flowId())
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
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

    /**
     * A Flow run must execute the persisted Flow state, not an equivalent-looking client payload.
     */
    private CompiledFlowExecution compileFlowExecution(FlowRunSnapshotResponse snapshot) {
        List<FlowExecutionSectionResponse> inputSections = snapshot.nodes().stream()
                .filter(node -> "input".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .filter(node -> !node.content().trim().equals(snapshot.description()))
                .map(node -> compileNodeSection(node, "input-context", snapshot.variableValues()))
                .toList();
        List<FlowExecutionSectionResponse> promptSections = snapshot.nodes().stream()
                .filter(node -> "prompt".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> compileNodeSection(node, "prompt", snapshot.variableValues()))
                .toList();
        List<FlowExecutionSectionResponse> executionGuidanceSections = snapshot.nodes().stream()
                .filter(node -> "ai-task".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> compileNodeSection(node, "execution-guidance", snapshot.variableValues()))
                .toList();
        List<FlowExecutionSectionResponse> deliveryFocusSections = snapshot.nodes().stream()
                .filter(node -> "output".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> compileNodeSection(node, "delivery-focus", snapshot.variableValues()))
                .toList();

        List<FlowExecutionSectionResponse> sections = new ArrayList<>();
        sections.add(new FlowExecutionSectionResponse(
                "objective",
                null,
                snapshot.title(),
                snapshot.description()
        ));
        sections.addAll(inputSections);
        if (StringUtils.hasText(snapshot.runtimeContext())) {
            sections.add(new FlowExecutionSectionResponse(
                    "runtime-context",
                    null,
                    "本次运行说明",
                    snapshot.runtimeContext()
            ));
        }
        sections.addAll(promptSections);
        sections.addAll(executionGuidanceSections);
        sections.addAll(deliveryFocusSections);
        sections.add(new FlowExecutionSectionResponse(
                "response-contract",
                null,
                "结构化输出",
                "Summary · Key Points · Result · Next Actions"
        ));

        List<String> executionInput = new ArrayList<>();
        executionInput.add("请按下面的 Flow 目标执行 AI 工作流。");
        executionInput.add("");
        executionInput.add("Flow: " + snapshot.title());
        executionInput.add("目标: " + snapshot.description());
        if (!inputSections.isEmpty()) {
            executionInput.add("\n输入节点上下文:\n" + renderNodeSections(inputSections));
        }
        if (StringUtils.hasText(snapshot.runtimeContext())) {
            executionInput.add("\n本次运行上下文:\n" + snapshot.runtimeContext());
        }
        if (!promptSections.isEmpty()) {
            executionInput.add("\n可复用 Prompt 节点:\n" + renderNodeSections(promptSections));
        }
        if (!executionGuidanceSections.isEmpty()) {
            executionInput.add("\n执行指令:\n" + renderNodeSections(executionGuidanceSections));
        }
        if (!deliveryFocusSections.isEmpty()) {
            executionInput.add("\n交付重点:\n" + renderNodeSections(deliveryFocusSections));
        }
        executionInput.add("");
        executionInput.add("请输出：1. Summary 2. Key Points 3. Result 4. Next Actions");
        return new CompiledFlowExecution(String.join("\n", executionInput), List.copyOf(sections));
    }

    private FlowExecutionSectionResponse compileNodeSection(
            FlowNodeDto node,
            String kind,
            Map<String, String> variableValues
    ) {
        return new FlowExecutionSectionResponse(
                kind,
                node.id(),
                node.title(),
                applyFlowVariables(node.content(), variableValues)
        );
    }

    private String renderNodeSections(List<FlowExecutionSectionResponse> sections) {
        return sections.stream()
                .map(section -> formatNodeBlock(section.title(), section.content()))
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
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

    private String formatNodeBlock(String title, String content) {
        return "## " + title + "\n" + content.trim();
    }

    private String applyFlowVariables(String content, Map<String, String> values) {
        if (!StringUtils.hasText(content) || values.isEmpty()) {
            return content;
        }

        Matcher matcher = FLOW_VARIABLE_PATTERN.matcher(content);
        StringBuffer compiled = new StringBuffer();
        while (matcher.find()) {
            String variable = matcher.group().substring(1, matcher.group().length() - 1);
            String value = values.get(variable);
            String replacement = StringUtils.hasText(value) ? value : matcher.group();
            matcher.appendReplacement(compiled, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(compiled);
        return compiled.toString();
    }

    private void requireFlowVariableValues(FlowRunSnapshotResponse snapshot) {
        List<String> missingVariables = findMissingFlowVariables(snapshot);
        if (!missingVariables.isEmpty()) {
            throw new IllegalArgumentException("请填写 Flow 变量: " + String.join(", ", missingVariables));
        }
    }

    private List<String> findMissingFlowVariables(FlowRunSnapshotResponse snapshot) {
        Set<String> requiredVariables = new LinkedHashSet<>();
        snapshot.nodes().stream()
                .map(FlowNodeDto::content)
                .filter(StringUtils::hasText)
                .forEach(content -> {
                    Matcher matcher = FLOW_VARIABLE_PATTERN.matcher(content);
                    while (matcher.find()) {
                        requiredVariables.add(matcher.group().substring(1, matcher.group().length() - 1));
                    }
                });

        return requiredVariables.stream()
                .filter(variable -> !StringUtils.hasText(snapshot.variableValues().get(variable)))
                .toList();
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
                .map(node -> buildFlowNodeRunTrace(node, snapshot.variableValues(), result, errorMessage))
                .toList();
        return new FlowRunTraceResponse(
                snapshot.flowId(),
                completed ? Task.STATUS_COMPLETED : Task.STATUS_FAILED,
                1,
                nodes
        );
    }

    private FlowNodeRunTraceResponse buildFlowNodeRunTrace(
            FlowNodeDto node,
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
        return new FlowNodeRunTraceResponse(
                node.id(),
                node.type(),
                node.title(),
                status,
                applyFlowVariables(node.content(), variableValues),
                outputSummary,
                nodeError
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
            UUID promptId,
            String promptTitle,
            UUID flowId,
            String flowTitle,
            FlowRunSnapshotResponse flowRunSnapshot,
            UUID rerunOfTaskId,
            UUID continuedFromTaskId,
            UUID inputVariantOfTaskId,
            boolean flowExecution
    ) {
    }

    private record CompiledFlowExecution(
            String executionInput,
            List<FlowExecutionSectionResponse> sections
    ) {
    }
}
