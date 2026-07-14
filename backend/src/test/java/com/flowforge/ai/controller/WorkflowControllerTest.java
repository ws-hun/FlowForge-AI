package com.flowforge.ai.controller;

import com.flowforge.ai.dto.FlowResponse;
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
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(workflowService.restoreVersion(flowId, versionId)).thenReturn(response);

        mockMvc.perform(post("/api/flows/{id}/versions/{versionId}/restore", flowId, versionId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(workflowService).restoreVersion(flowId, versionId);
    }
}
