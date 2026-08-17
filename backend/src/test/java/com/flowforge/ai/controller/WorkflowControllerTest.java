package com.flowforge.ai.controller;

import com.flowforge.ai.dto.FlowExecutionPreviewRequest;
import com.flowforge.ai.dto.FlowExecutionPreviewResponse;
import com.flowforge.ai.dto.FlowArtifactContractResponse;
import com.flowforge.ai.dto.FlowExecutionPlanResponse;
import com.flowforge.ai.dto.FlowExecutionSectionResponse;
import com.flowforge.ai.dto.FlowExecutionStepResponse;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowResponse;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import com.flowforge.ai.exception.ResourceConflictException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                3L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(workflowService.restoreVersion(flowId, versionId, 3L)).thenReturn(response);

        mockMvc.perform(post("/api/flows/{id}/versions/{versionId}/restore", flowId, versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":3}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(workflowService).restoreVersion(flowId, versionId, 3L);
    }

    @Test
    void previewsTheServerCompiledExecutionInputForASavedFlow() throws Exception {
        UUID flowId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 15, 10, 20);
        FlowExecutionPreviewResponse response = new FlowExecutionPreviewResponse(
                "single-pass",
                1,
                "flow-compiler-v1",
                "8f2a4a8bd2f30ec4880b55df102d714d1f05d5dc7e60d7cc15bfc5bf5f660b8a",
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
                ),
                List.of(new FlowExecutionSectionResponse(
                        "objective",
                        null,
                        "Launch brief",
                        "Prepare a focused launch"
                )),
                new FlowExecutionPlanResponse(
                        "flow-plan-v2",
                        "linear",
                        List.of(new FlowExecutionStepResponse(
                                1,
                                "input-1",
                                "input",
                                "Intent",
                                "supply-context",
                                List.of(),
                                false,
                                new FlowArtifactContractResponse("flow:objective", "flow-objective", "flow-snapshot"),
                                new FlowArtifactContractResponse(
                                        "node:input-1:context-contribution",
                                        "context-contribution",
                                        "trace-content"
                                )
                        ))
                ),
                true,
                List.of(),
                List.of()
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
                .andExpect(jsonPath("$.executionMode").value("single-pass"))
                .andExpect(jsonPath("$.providerCallCount").value(1))
                .andExpect(jsonPath("$.compilerVersion").value("flow-compiler-v1"))
                .andExpect(jsonPath("$.executionInputFingerprint").value("8f2a4a8bd2f30ec4880b55df102d714d1f05d5dc7e60d7cc15bfc5bf5f660b8a"))
                .andExpect(jsonPath("$.executionPlan.version").value("flow-plan-v2"))
                .andExpect(jsonPath("$.executionPlan.steps[0].operation").value("supply-context"))
                .andExpect(jsonPath("$.executionPlan.steps[0].inputArtifact.type").value("flow-objective"))
                .andExpect(jsonPath("$.executionPlan.steps[0].outputArtifact.type").value("context-contribution"))
                .andExpect(jsonPath("$.executionInput").value("Flow: Launch brief\n本次运行上下文:\nFocus on the first release."))
                .andExpect(jsonPath("$.flowRunSnapshot.flowId").value(flowId.toString()))
                .andExpect(jsonPath("$.flowRunSnapshot.variableValues.audience").value("product teams"))
                .andExpect(jsonPath("$.sections[0].kind").value("objective"))
                .andExpect(jsonPath("$.sections[0].title").value("Launch brief"))
                .andExpect(jsonPath("$.executable").value(true))
                .andExpect(jsonPath("$.missingVariables").isEmpty())
                .andExpect(jsonPath("$.incompleteNodes").isEmpty());

        verify(taskService).previewFlowExecution(
                eq(flowId),
                argThat(request -> request.runtimeContext().equals("Focus on the first release.")
                        && request.variableValues().equals(Map.of("audience", "product teams")))
        );
    }

    @Test
    void returnsBadRequestWhenTheFlowDefinitionBreaksTheRuntimeContract() throws Exception {
        when(workflowService.createFlow(any())).thenThrow(new IllegalArgumentException(
                "当前 Flow 运行模型需要且只支持一个 AI Task 节点"
        ));

        mockMvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Broken Flow",
                                  "description": "A Flow without an execution step",
                                  "nodes": [
                                    {
                                      "id": "input-1",
                                      "type": "input",
                                      "title": "Intent",
                                      "description": "Starting context",
                                      "content": "Prepare a launch plan"
                                    },
                                    {
                                      "id": "output-1",
                                      "type": "output",
                                      "title": "Result",
                                      "description": "Expected delivery",
                                      "content": "Return a clear plan"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("当前 Flow 运行模型需要且只支持一个 AI Task 节点"));
    }

    @Test
    void returnsConflictWhenAFlowUpdateUsesAStaleRevision() throws Exception {
        UUID flowId = UUID.randomUUID();
        when(workflowService.updateFlow(eq(flowId), any())).thenThrow(new ResourceConflictException(
                "Flow 已在其他窗口更新，请基于最新版本重新确认修改"
        ));

        mockMvc.perform(put("/api/flows/{id}", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Launch Flow",
                                  "description": "Prepare a launch",
                                  "revision": 2,
                                  "nodes": [
                                    {
                                      "id": "input-1",
                                      "type": "input",
                                      "title": "Intent",
                                      "description": "Starting context",
                                      "content": "Prepare a launch plan"
                                    },
                                    {
                                      "id": "ai-task-1",
                                      "type": "ai-task",
                                      "title": "Execute",
                                      "description": "Run the task",
                                      "content": "Create the plan"
                                    },
                                    {
                                      "id": "output-1",
                                      "type": "output",
                                      "title": "Result",
                                      "description": "Expected delivery",
                                      "content": "Return a clear plan"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Flow 已在其他窗口更新，请基于最新版本重新确认修改"));
    }

    @Test
    void deletesAFlowAgainstTheRevisionVisibleToTheClient() throws Exception {
        UUID flowId = UUID.randomUUID();

        mockMvc.perform(delete("/api/flows/{id}", flowId).queryParam("revision", "7"))
                .andExpect(status().isOk());

        verify(workflowService).deleteFlow(flowId, 7L);
    }

    @Test
    void rejectsAFlowDeleteWithoutARevisionAsBadRequest() throws Exception {
        UUID flowId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("revision is required"))
                .when(workflowService).deleteFlow(flowId, null);

        mockMvc.perform(delete("/api/flows/{id}", flowId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("revision is required"));
    }

    @Test
    void rejectsAnUnreadableFlowRevisionBodyAsBadRequest() throws Exception {
        mockMvc.perform(post(
                        "/api/flows/{id}/versions/{versionId}/restore",
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request body"));
    }
}
