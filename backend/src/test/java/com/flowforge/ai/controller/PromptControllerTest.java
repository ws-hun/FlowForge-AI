package com.flowforge.ai.controller;

import com.flowforge.ai.dto.PromptResponse;
import com.flowforge.ai.exception.ResourceConflictException;
import com.flowforge.ai.service.PromptService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PromptController.class)
class PromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PromptService promptService;

    @MockBean
    private TaskService taskService;

    @Test
    void returnsConflictWhenAPromptUpdateUsesAStaleRevision() throws Exception {
        UUID promptId = UUID.randomUUID();
        when(promptService.updatePrompt(eq(promptId), any())).thenThrow(new ResourceConflictException(
                "Prompt 已在其他窗口更新，请基于最新版本重新确认修改"
        ));

        mockMvc.perform(put("/api/prompts/{id}", promptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Product brief",
                                  "category": "Product",
                                  "description": "Prepare a product brief",
                                  "content": "Create a focused brief",
                                  "tags": ["Product"],
                                  "favorite": false,
                                  "revision": 2
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Prompt 已在其他窗口更新，请基于最新版本重新确认修改"));
    }

    @Test
    void togglesFavoriteAgainstTheRevisionVisibleToTheClient() throws Exception {
        UUID promptId = UUID.randomUUID();
        when(promptService.toggleFavorite(promptId, 3L)).thenReturn(response(promptId, 4L, true));

        mockMvc.perform(patch("/api/prompts/{id}/favorite", promptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.revision").value(4));

        verify(promptService).toggleFavorite(promptId, 3L);
    }

    @Test
    void rejectsAPromptDeleteWithoutARevisionAsBadRequest() throws Exception {
        UUID promptId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("revision is required"))
                .when(promptService).deletePrompt(promptId, null);

        mockMvc.perform(delete("/api/prompts/{id}", promptId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("revision is required"));
    }

    private PromptResponse response(UUID id, long revision, boolean favorite) {
        LocalDateTime now = LocalDateTime.now();
        return new PromptResponse(
                id,
                "Product brief",
                "Product",
                "Prepare a product brief",
                "Create a focused brief",
                List.of("Product"),
                favorite,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                revision,
                now,
                now
        );
    }
}
