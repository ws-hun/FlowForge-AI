package com.flowforge.ai.controller;

import com.flowforge.ai.dto.RunTaskRequest;
import com.flowforge.ai.dto.TaskHistoryResponse;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/run")
    public TaskRunResponse runTask(@Valid @RequestBody RunTaskRequest request) {
        return taskService.runTask(request);
    }

    @PostMapping("/{id}/rerun")
    public TaskRunResponse rerunTask(@PathVariable UUID id) {
        return taskService.rerunTask(id);
    }

    @GetMapping
    public List<TaskHistoryResponse> listTasks() {
        return taskService.listTasks();
    }
}
