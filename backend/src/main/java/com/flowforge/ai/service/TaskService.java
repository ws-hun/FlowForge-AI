package com.flowforge.ai.service;

import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.dto.TaskHistoryResponse;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.repository.TaskRepository;
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

    @Transactional
    public TaskRunResponse runTask(String input) {
        OpenAiTaskResult aiResult = openAiService.processTask(input);

        Task task = Task.builder()
                .input(input)
                .summary(aiResult.summary())
                .result(aiResult.result())
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

    private TaskHistoryResponse toHistoryResponse(Task task) {
        return new TaskHistoryResponse(
                task.getId(),
                task.getInput(),
                task.getSummary(),
                task.getResult(),
                task.getCreatedAt()
        );
    }
}
