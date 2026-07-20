package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowExecutionPreviewRequest;
import com.flowforge.ai.dto.FlowExecutionPreviewResponse;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.dto.RunTaskRequest;
import com.flowforge.ai.dto.TaskHistoryResponse;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.entity.Prompt;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.entity.Workflow;
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

    @Transactional
    public TaskRunResponse runTask(RunTaskRequest request) {
        Prompt sourcePrompt = resolveSourcePrompt(request);
        Workflow sourceFlow = resolveSourceFlow(request);
        String standaloneInput = cleanOptional(request.input());
        if (sourceFlow == null && !StringUtils.hasText(standaloneInput)) {
            throw new IllegalArgumentException("input is required");
        }
        FlowRunSnapshotResponse flowRunSnapshot = sourceFlow == null
                ? null
                : createFlowRunSnapshot(sourceFlow, request.flowRunContext(), request.flowVariableValues());
        if (flowRunSnapshot != null) {
            requireFlowNodeContents(flowRunSnapshot);
            requireFlowVariableValues(flowRunSnapshot);
        }
        String executionInput = flowRunSnapshot == null
                ? standaloneInput
                : compileFlowExecutionInput(flowRunSnapshot);
        OpenAiTaskResult aiResult = openAiService.processTask(executionInput);

        Task task = Task.builder()
                .input(executionInput)
                .summary(aiResult.summary())
                .result(aiResult.result())
                .provider(aiResult.provider())
                .model(aiResult.model())
                .sourcePromptId(sourcePrompt == null ? null : sourcePrompt.getId())
                .sourcePromptTitle(sourcePrompt == null ? null : sourcePrompt.getTitle())
                .sourceFlowId(sourceFlow == null ? null : sourceFlow.getId())
                .sourceFlowTitle(sourceFlow == null ? null : sourceFlow.getTitle())
                .sourceFlowSnapshotJson(serializeFlowRunSnapshot(flowRunSnapshot))
                .build();

        Task savedTask = taskRepository.save(task);

        return new TaskRunResponse(
                aiResult.summary(),
                aiResult.result(),
                aiResult.raw(),
                aiResult.provider(),
                aiResult.model(),
                executionInput,
                savedTask.getId(),
                flowRunSnapshot
        );
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

        return new FlowExecutionPreviewResponse(
                compileFlowExecutionInput(flowRunSnapshot),
                flowRunSnapshot
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
                flow.getUpdatedAt(),
                cleanOptional(runtimeContext),
                cleanVariableValues(variableValues)
        );
    }

    /**
     * A Flow run must execute the persisted Flow state, not an equivalent-looking client payload.
     */
    private String compileFlowExecutionInput(FlowRunSnapshotResponse snapshot) {
        String inputBlocks = snapshot.nodes().stream()
                .filter(node -> "input".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .filter(node -> !node.content().trim().equals(snapshot.description()))
                .map(node -> formatNodeBlock(node.title(), applyFlowVariables(node.content(), snapshot.variableValues())))
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
        String promptBlocks = snapshot.nodes().stream()
                .filter(node -> "prompt".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> formatNodeBlock(node.title(), applyFlowVariables(node.content(), snapshot.variableValues())))
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
        String executionGuidanceBlocks = snapshot.nodes().stream()
                .filter(node -> "ai-task".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> formatNodeBlock(node.title(), applyFlowVariables(node.content(), snapshot.variableValues())))
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
        String deliveryFocusBlocks = snapshot.nodes().stream()
                .filter(node -> "output".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> formatNodeBlock(node.title(), applyFlowVariables(node.content(), snapshot.variableValues())))
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");

        List<String> sections = new ArrayList<>();
        sections.add("请按下面的 Flow 目标执行 AI 工作流。");
        sections.add("");
        sections.add("Flow: " + snapshot.title());
        sections.add("目标: " + snapshot.description());
        if (StringUtils.hasText(inputBlocks)) {
            sections.add("\n输入节点上下文:\n" + inputBlocks);
        }
        if (StringUtils.hasText(snapshot.runtimeContext())) {
            sections.add("\n本次运行上下文:\n" + snapshot.runtimeContext());
        }
        if (StringUtils.hasText(promptBlocks)) {
            sections.add("\n可复用 Prompt 节点:\n" + promptBlocks);
        }
        if (StringUtils.hasText(executionGuidanceBlocks)) {
            sections.add("\n执行指令:\n" + executionGuidanceBlocks);
        }
        if (StringUtils.hasText(deliveryFocusBlocks)) {
            sections.add("\n交付重点:\n" + deliveryFocusBlocks);
        }
        sections.add("");
        sections.add("请输出：1. Summary 2. Key Points 3. Result 4. Next Actions");
        return String.join("\n", sections);
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

        List<String> missingVariables = requiredVariables.stream()
                .filter(variable -> !StringUtils.hasText(snapshot.variableValues().get(variable)))
                .toList();
        if (!missingVariables.isEmpty()) {
            throw new IllegalArgumentException("请填写 Flow 变量: " + String.join(", ", missingVariables));
        }
    }

    private void requireFlowNodeContents(FlowRunSnapshotResponse snapshot) {
        List<String> incompleteNodes = snapshot.nodes().stream()
                .filter(node -> !StringUtils.hasText(node.content()))
                .map(FlowNodeDto::title)
                .toList();
        if (!incompleteNodes.isEmpty()) {
            throw new IllegalArgumentException("请完善 Flow 节点: " + String.join(", ", incompleteNodes));
        }
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
                task.getSourcePromptId(),
                task.getSourcePromptTitle(),
                task.getSourceFlowId(),
                task.getSourceFlowTitle(),
                deserializeFlowRunSnapshot(task.getSourceFlowSnapshotJson()),
                task.getCreatedAt()
        );
    }
}
