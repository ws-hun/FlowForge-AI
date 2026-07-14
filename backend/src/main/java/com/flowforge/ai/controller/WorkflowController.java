package com.flowforge.ai.controller;

import com.flowforge.ai.dto.FlowRequest;
import com.flowforge.ai.dto.FlowResponse;
import com.flowforge.ai.dto.FlowVersionResponse;
import com.flowforge.ai.dto.TaskHistoryResponse;
import com.flowforge.ai.service.TaskService;
import com.flowforge.ai.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final TaskService taskService;

    @GetMapping
    public List<FlowResponse> listFlows() {
        return workflowService.listFlows();
    }

    @PostMapping
    public FlowResponse createFlow(@Valid @RequestBody FlowRequest request) {
        return workflowService.createFlow(request);
    }

    @PutMapping("/{id}")
    public FlowResponse updateFlow(@PathVariable("id") UUID id, @Valid @RequestBody FlowRequest request) {
        return workflowService.updateFlow(id, request);
    }

    @GetMapping("/{id}/runs")
    public List<TaskHistoryResponse> listFlowRuns(@PathVariable("id") UUID id) {
        return taskService.listFlowRuns(id);
    }

    @GetMapping("/{id}/versions")
    public List<FlowVersionResponse> listVersions(@PathVariable("id") UUID id) {
        return workflowService.listVersions(id);
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public FlowResponse restoreVersion(
            @PathVariable("id") UUID id,
            @PathVariable("versionId") UUID versionId
    ) {
        return workflowService.restoreVersion(id, versionId);
    }

    @DeleteMapping("/{id}")
    public void deleteFlow(@PathVariable("id") UUID id) {
        workflowService.deleteFlow(id);
    }
}
