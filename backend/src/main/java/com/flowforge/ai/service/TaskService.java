package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.FlowNodeDto;
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

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final OpenAiService openAiService;
    private final TaskRepository taskRepository;
    private final PromptRepository promptRepository;
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public TaskRunResponse runTask(RunTaskRequest request) {
        Prompt sourcePrompt = resolveSourcePrompt(request);
        Workflow sourceFlow = resolveSourceFlow(request);
        FlowRunSnapshotResponse flowRunSnapshot = sourceFlow == null ? null : createFlowRunSnapshot(sourceFlow, request);
        OpenAiTaskResult aiResult = openAiService.processTask(request.input());

        Task task = Task.builder()
                .input(request.input())
                .summary(aiResult.summary())
                .result(aiResult.result())
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
                savedTask.getId(),
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

    private FlowRunSnapshotResponse createFlowRunSnapshot(Workflow flow, RunTaskRequest request) {
        return new FlowRunSnapshotResponse(
                flow.getId(),
                flow.getTitle(),
                flow.getDescription(),
                deserializeFlowNodes(flow.getNodesJson()),
                flow.getUpdatedAt(),
                cleanOptional(request.flowRunContext()),
                cleanVariableValues(request.flowVariableValues())
        );
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
                task.getSourcePromptId(),
                task.getSourcePromptTitle(),
                task.getSourceFlowId(),
                task.getSourceFlowTitle(),
                deserializeFlowRunSnapshot(task.getSourceFlowSnapshotJson()),
                task.getCreatedAt()
        );
    }
}
