package com.flowforge.ai.controller;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageEntryResponse;
import com.flowforge.ai.dto.FlowNodeArtifactLineageResponse;
import com.flowforge.ai.dto.FlowNodeArtifactSummaryResponse;
import com.flowforge.ai.dto.FlowProviderCallResponse;
import com.flowforge.ai.dto.FlowProviderAttemptResponse;
import com.flowforge.ai.dto.FlowProviderAttemptPolicyResponse;
import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.exception.AiExecutionException;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.service.FlowNodeArtifactQueryService;
import com.flowforge.ai.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private FlowNodeArtifactQueryService flowNodeArtifactQueryService;

    @Test
    void startsAContinuationFromAStoredTaskResult() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID newTaskId = UUID.randomUUID();
        when(taskService.runTask(argThat(request -> sourceTaskId.equals(request.continuedFromTaskId()))))
                .thenReturn(new TaskRunResponse(
                        "Continued result",
                        "Continued content",
                        "{}",
                        "deepseek",
                        "deepseek-chat",
                        300,
                        180,
                        480,
                        1250L,
                        null,
                        sourceTaskId,
                        null,
                        "Server-compiled continuation input",
                        newTaskId,
                        null,
                        null
                ));

        mockMvc.perform(post("/api/tasks/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "补充验证计划",
                                  "continuedFromTaskId": "%s"
                                }
                                """.formatted(sourceTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.continuedFromTaskId").value(sourceTaskId.toString()))
                .andExpect(jsonPath("$.executionInput").value("Server-compiled continuation input"));

        verify(taskService).runTask(argThat(request ->
                request.input().equals("补充验证计划")
                        && sourceTaskId.equals(request.continuedFromTaskId())
        ));
    }

    @Test
    void startsAnEditableInputVariantFromAStoredTask() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID newTaskId = UUID.randomUUID();
        when(taskService.runTask(argThat(request -> sourceTaskId.equals(request.inputVariantOfTaskId()))))
                .thenReturn(new TaskRunResponse(
                        "Variant result",
                        "Variant content",
                        "{}",
                        "openai",
                        "gpt-4.1",
                        420,
                        210,
                        630,
                        1040L,
                        null,
                        null,
                        sourceTaskId,
                        "Edited historical execution input",
                        newTaskId,
                        null,
                        null
                ));

        mockMvc.perform(post("/api/tasks/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "Edited historical execution input",
                                  "inputVariantOfTaskId": "%s"
                                }
                                """.formatted(sourceTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputVariantOfTaskId").value(sourceTaskId.toString()))
                .andExpect(jsonPath("$.executionInput").value("Edited historical execution input"));

        verify(taskService).runTask(argThat(request ->
                request.input().equals("Edited historical execution input")
                        && sourceTaskId.equals(request.inputVariantOfTaskId())
        ));
    }

    @Test
    void rerunsAStoredTaskThroughTheRestEndpoint() throws Exception {
        UUID sourceTaskId = UUID.randomUUID();
        UUID newTaskId = UUID.randomUUID();
        when(taskService.rerunTask(sourceTaskId)).thenReturn(new TaskRunResponse(
                "Fresh result",
                "Fresh content",
                "{}",
                "openai",
                "gpt-4.1",
                400,
                200,
                600,
                980L,
                sourceTaskId,
                null,
                null,
                "Exact stored execution input",
                newTaskId,
                null,
                null
        ));

        mockMvc.perform(post("/api/tasks/{id}/rerun", sourceTaskId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.taskId").value(newTaskId.toString()))
                .andExpect(jsonPath("$.rerunOfTaskId").value(sourceTaskId.toString()))
                .andExpect(jsonPath("$.executionInput").value("Exact stored execution input"))
                .andExpect(jsonPath("$.provider").value("openai"))
                .andExpect(jsonPath("$.durationMs").value(980))
                .andExpect(jsonPath("$.totalTokens").value(600));

        verify(taskService).rerunTask(sourceTaskId);
    }

    @Test
    void returnsNotFoundWhenTheSourceTaskIsMissing() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(taskService.rerunTask(taskId)).thenThrow(new ResourceNotFoundException("Task run not found"));

        mockMvc.perform(post("/api/tasks/{id}/rerun", taskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task run not found"));
    }

    @Test
    void listsAddressableArtifactsWithoutEagerlyReturningPayloads() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        String artifactKey = "node:input-1:context-contribution";
        when(flowNodeArtifactQueryService.listForTask(taskId)).thenReturn(List.of(
                new FlowNodeArtifactSummaryResponse(
                        artifactId,
                        taskId,
                        flowId,
                        "input-1",
                        1,
                        artifactKey,
                        "context-contribution",
                        "materialized",
                        "text/plain",
                        "a".repeat(64),
                        "flow:objective",
                        "flow-objective",
                        "flow-snapshot",
                        "materialized",
                        "compiled-reference",
                        "c".repeat(64),
                        LocalDateTime.of(2026, 8, 17, 10, 30)
                )
        ));

        mockMvc.perform(get("/api/tasks/{id}/artifacts", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifactKey").value(artifactKey))
                .andExpect(jsonPath("$[0].sequence").value(1))
                .andExpect(jsonPath("$[0].inputArtifactKey").value("flow:objective"))
                .andExpect(jsonPath("$[0].inputResolution").value("compiled-reference"))
                .andExpect(jsonPath("$[0].payload").doesNotExist());

        verify(flowNodeArtifactQueryService).listForTask(taskId);
    }

    @Test
    void returnsOneArtifactPayloadByItsStableKey() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        String artifactKey = "node:ai-task-1:provider-result";
        when(flowNodeArtifactQueryService.getForTask(taskId, artifactKey)).thenReturn(
                new FlowNodeArtifactDetailResponse(
                        artifactId,
                        taskId,
                        flowId,
                        "ai-task-1",
                        2,
                        artifactKey,
                        "provider-result",
                        "materialized",
                        "text/markdown",
                        "Summary\nResult",
                        "b".repeat(64),
                        "node:input-1:context-contribution",
                        "context-contribution",
                        "node-artifact",
                        "materialized",
                        "compiled-reference",
                        "c".repeat(64),
                        new FlowProviderCallResponse(
                                "completed",
                                "deepseek",
                                "deepseek-chat",
                                120,
                                80,
                                200,
                                840L,
                                null
                        ),
                        List.of(new FlowProviderAttemptResponse(
                                UUID.randomUUID(),
                                1,
                                "initial",
                                null,
                                "completed",
                                "deepseek",
                                "deepseek-chat",
                                120,
                                80,
                                200,
                                840L,
                                null,
                                LocalDateTime.of(2026, 8, 17, 10, 31)
                        )),
                        new FlowProviderAttemptPolicyResponse(
                                "flow-provider-attempt-policy-v1",
                                "completed",
                                1,
                                false,
                                false,
                                "none"
                        ),
                        LocalDateTime.of(2026, 8, 17, 10, 31)
                )
        );

        mockMvc.perform(get("/api/tasks/{id}/artifacts/{artifactKey}", taskId, artifactKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactKey").value(artifactKey))
                .andExpect(jsonPath("$.payload").value("Summary\nResult"))
                .andExpect(jsonPath("$.mediaType").value("text/markdown"))
                .andExpect(jsonPath("$.inputArtifactKey")
                        .value("node:input-1:context-contribution"))
                .andExpect(jsonPath("$.inputArtifactStorage").value("node-artifact"))
                .andExpect(jsonPath("$.inputResolution").value("compiled-reference"))
                .andExpect(jsonPath("$.providerCall.status").value("completed"))
                .andExpect(jsonPath("$.providerCall.provider").value("deepseek"))
                .andExpect(jsonPath("$.providerCall.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.providerCall.totalTokens").value(200))
                .andExpect(jsonPath("$.providerCall.durationMs").value(840))
                .andExpect(jsonPath("$.providerCall.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.providerAttempts").isArray())
                .andExpect(jsonPath("$.providerAttempts[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.providerAttempts[0].triggerType").value("initial"))
                .andExpect(jsonPath("$.providerAttempts[0].previousAttemptId").doesNotExist())
                .andExpect(jsonPath("$.providerAttempts[0].status").value("completed"))
                .andExpect(jsonPath("$.providerAttempts[0].totalTokens").value(200))
                .andExpect(jsonPath("$.providerAttempts[0].durationMs").value(840))
                .andExpect(jsonPath("$.providerAttemptPolicy.version")
                        .value("flow-provider-attempt-policy-v1"))
                .andExpect(jsonPath("$.providerAttemptPolicy.currentState").value("completed"))
                .andExpect(jsonPath("$.providerAttemptPolicy.recordedAttempts").value(1))
                .andExpect(jsonPath("$.providerAttemptPolicy.automaticRetryEnabled").value(false))
                .andExpect(jsonPath("$.providerAttemptPolicy.sameArtifactRecoveryEnabled").value(false))
                .andExpect(jsonPath("$.providerAttemptPolicy.failedRunRecoveryAction").value("none"));

        verify(flowNodeArtifactQueryService).getForTask(taskId, artifactKey);
    }

    @Test
    void returnsMetadataOnlyLineagePathForAnArtifact() throws Exception {
        UUID taskId = UUID.randomUUID();
        String artifactKey = "node:output-1:result-document";
        when(flowNodeArtifactQueryService.getLineageForTask(taskId, artifactKey)).thenReturn(
                new FlowNodeArtifactLineageResponse(
                        taskId,
                        artifactKey,
                        true,
                        "flow-snapshot",
                        List.of(
                                new FlowNodeArtifactLineageEntryResponse(
                                        UUID.randomUUID(),
                                        "output-1",
                                        3,
                                        artifactKey,
                                        "result-document",
                                        "node-artifact",
                                        "materialized",
                                        "text/markdown",
                                        "a".repeat(64),
                                        "compiled-reference",
                                        true
                                ),
                                new FlowNodeArtifactLineageEntryResponse(
                                        null,
                                        null,
                                        null,
                                        "flow:objective",
                                        "flow-objective",
                                        "flow-snapshot",
                                        "materialized",
                                        null,
                                        "b".repeat(64),
                                        null,
                                        false
                                )
                        )
                )
        );

        mockMvc.perform(get("/api/tasks/{id}/artifacts/{artifactKey}/lineage", taskId, artifactKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedArtifactKey").value(artifactKey))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.termination").value("flow-snapshot"))
                .andExpect(jsonPath("$.path").isArray())
                .andExpect(jsonPath("$.path[0].artifactKey").value(artifactKey))
                .andExpect(jsonPath("$.path[0].persisted").value(true))
                .andExpect(jsonPath("$.path[1].artifactKey").value("flow:objective"))
                .andExpect(jsonPath("$.path[1].persisted").value(false))
                .andExpect(jsonPath("$.path[0].payload").doesNotExist());

        verify(flowNodeArtifactQueryService).getLineageForTask(taskId, artifactKey);
    }

    @Test
    void separatesProviderFailuresFromInternalApplicationFailures() throws Exception {
        UUID failedRunId = UUID.randomUUID();
        doThrow(new AiExecutionException(
                "deepseek",
                "deepseek-chat",
                "AI API error: provider unavailable",
                new IllegalStateException("provider unavailable")
        ).attachRunId(failedRunId)).when(taskService).runTask(any());

        mockMvc.perform(post("/api/tasks/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "input": "Generate a release brief" }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("AI API error: provider unavailable"))
                .andExpect(jsonPath("$.runId").value(failedRunId.toString()));

        reset(taskService);
        doThrow(new IllegalStateException("database payload is corrupt"))
                .when(taskService).runTask(any());

        mockMvc.perform(post("/api/tasks/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "input": "Generate another release brief" }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.runId").doesNotExist());
    }
}
