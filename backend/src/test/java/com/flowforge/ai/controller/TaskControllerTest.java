package com.flowforge.ai.controller;

import com.flowforge.ai.dto.FlowNodeArtifactDetailResponse;
import com.flowforge.ai.dto.FlowNodeArtifactSummaryResponse;
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
                        LocalDateTime.of(2026, 8, 17, 10, 30)
                )
        ));

        mockMvc.perform(get("/api/tasks/{id}/artifacts", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifactKey").value(artifactKey))
                .andExpect(jsonPath("$[0].sequence").value(1))
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
                        LocalDateTime.of(2026, 8, 17, 10, 31)
                )
        );

        mockMvc.perform(get("/api/tasks/{id}/artifacts/{artifactKey}", taskId, artifactKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactKey").value(artifactKey))
                .andExpect(jsonPath("$.payload").value("Summary\nResult"))
                .andExpect(jsonPath("$.mediaType").value("text/markdown"));

        verify(flowNodeArtifactQueryService).getForTask(taskId, artifactKey);
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
