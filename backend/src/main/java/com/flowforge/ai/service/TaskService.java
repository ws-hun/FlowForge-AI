package com.flowforge.ai.service;

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

@Service
@RequiredArgsConstructor
public class TaskService {

    private final OpenAiService openAiService;
    private final TaskRepository taskRepository;
    private final PromptRepository promptRepository;
    private final WorkflowRepository workflowRepository;

    @Transactional
    public TaskRunResponse runTask(RunTaskRequest request) {
        Prompt sourcePrompt = resolveSourcePrompt(request);
        Workflow sourceFlow = resolveSourceFlow(request);
        OpenAiTaskResult aiResult = openAiService.processTask(request.input());

        Task task = Task.builder()
                .input(request.input())
                .summary(aiResult.summary())
                .result(aiResult.result())
                .sourcePromptId(sourcePrompt == null ? null : sourcePrompt.getId())
                .sourcePromptTitle(sourcePrompt == null ? null : sourcePrompt.getTitle())
                .sourceFlowId(sourceFlow == null ? null : sourceFlow.getId())
                .sourceFlowTitle(sourceFlow == null ? null : sourceFlow.getTitle())
                .build();

        taskRepository.save(task);

        return new TaskRunResponse(
                aiResult.summary(),
                aiResult.result(),
                aiResult.raw()
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
    public List<TaskHistoryResponse> listPromptRuns(java.util.UUID promptId) {
        return taskRepository.findTop6BySourcePromptIdOrderByCreatedAtDesc(promptId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskHistoryResponse> listFlowRuns(java.util.UUID flowId) {
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
                task.getCreatedAt()
        );
    }
}
