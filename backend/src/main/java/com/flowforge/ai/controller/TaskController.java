package com.flowforge.ai.controller;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
import com.flowforge.ai.dto.FlowNodeArtifactSummaryResponse;
import com.flowforge.ai.dto.RunTaskRequest;
import com.flowforge.ai.dto.TaskHistoryResponse;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.service.FlowNodeArtifactQueryService;
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
    private final FlowNodeArtifactQueryService flowNodeArtifactQueryService;

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

    @GetMapping("/{id}/artifacts")
    public List<FlowNodeArtifactSummaryResponse> listArtifacts(@PathVariable UUID id) {
        return flowNodeArtifactQueryService.listForTask(id);
    }

    @GetMapping("/{id}/artifacts/{artifactKey}")
    public FlowNodeArtifactDetailResponse getArtifact(
            @PathVariable UUID id,
            @PathVariable String artifactKey
    ) {
        return flowNodeArtifactQueryService.getForTask(id, artifactKey);
    }
}
