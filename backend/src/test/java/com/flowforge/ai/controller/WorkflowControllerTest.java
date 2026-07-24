package com.flowforge.ai.controller;

import com.flowforge.ai.dto.FlowExecutionPreviewRequest;
import com.flowforge.ai.dto.FlowExecutionPreviewResponse;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowResponse;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.service.TaskService;
import com.flowforge.ai.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowService workflowService;

    @MockBean
    private TaskService taskService;

    @Test
    void restoresFlowVersionThroughTheRevisionEndpoint() throws Exception {
        UUID flowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        FlowResponse response = new FlowResponse(
                flowId,
                "Recovered flow",
                "A recovered creative state",
                List.of(),
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(workflowService.restoreVersion(flowId, versionId)).thenReturn(response);

        mockMvc.perform(post("/api/flows/{id}/versions/{versionId}/restore", flowId, versionId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(workflowService).restoreVersion(flowId, versionId);
    }

    @Test
    void previewsTheServerCompiledExecutionInputForASavedFlow() throws Exception {
        UUID flowId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 15, 10, 20);
        FlowExecutionPreviewResponse response = new FlowExecutionPreviewResponse(
                "Flow: Launch brief\n本次运行上下文:\nFocus on the first release.",
                new FlowRunSnapshotResponse(
                        flowId,
                        "Launch brief",
                        "Prepare a focused launch",
                        List.of(new FlowNodeDto("input-1", "input", "Intent", "", "Prepare a launch.", null, null)),
                        null,
                        null,
                        null,
                        null,
                        updatedAt,
                        "Focus on the first release.",
                        Map.of("audience", "product teams")
                )
        );
        when(taskService.previewFlowExecution(eq(flowId), any(FlowExecutionPreviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/flows/{id}/execution-preview", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runtimeContext": "Focus on the first release.",
                                  "variableValues": { "audience": "product teams" }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.executionInput").value("Flow: Launch brief\n本次运行上下文:\nFocus on the first release."))
                .andExpect(jsonPath("$.flowRunSnapshot.flowId").value(flowId.toString()))
                .andExpect(jsonPath("$.flowRunSnapshot.variableValues.audience").value("product teams"));

        verify(taskService).previewFlowExecution(
                eq(flowId),
                argThat(request -> request.runtimeContext().equals("Focus on the first release.")
                        && request.variableValues().equals(Map.of("audience", "product teams")))
        );
    }
}
