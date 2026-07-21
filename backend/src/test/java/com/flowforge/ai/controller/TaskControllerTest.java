package com.flowforge.ai.controller;

import com.flowforge.ai.dto.TaskRunResponse;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
                "Exact stored execution input",
                newTaskId,
                null
        ));

        mockMvc.perform(post("/api/tasks/{id}/rerun", sourceTaskId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.taskId").value(newTaskId.toString()))
                .andExpect(jsonPath("$.executionInput").value("Exact stored execution input"))
                .andExpect(jsonPath("$.provider").value("openai"))
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
}
