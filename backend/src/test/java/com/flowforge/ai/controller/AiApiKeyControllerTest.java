package com.flowforge.ai.controller;

import com.flowforge.ai.dto.ProviderConnectionTestResponse;
import com.flowforge.ai.entity.AiApiKey;
import com.flowforge.ai.exception.AiExecutionException;
import com.flowforge.ai.service.AiApiKeyService;
import com.flowforge.ai.service.OpenAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiApiKeyController.class)
class AiApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiApiKeyService aiApiKeyService;

    @MockBean
    private OpenAiService openAiService;

    @Test
    void testsAStoredProviderWithoutReturningItsPlaintextKey() throws Exception {
        UUID id = UUID.randomUUID();
        AiApiKey decryptedConfig = AiApiKey.builder()
                .id(id)
                .provider("deepseek")
                .apiKey("sk-server-only")
                .baseUrl("https://api.deepseek.com")
                .model("deepseek-chat")
                .build();
        ProviderConnectionTestResponse response = new ProviderConnectionTestResponse(
                "deepseek",
                "deepseek-chat",
                "connected",
                LocalDateTime.of(2026, 7, 29, 11, 30)
        );
        when(aiApiKeyService.getKey(id)).thenReturn(decryptedConfig);
        when(openAiService.testConnection(decryptedConfig)).thenReturn(response);

        mockMvc.perform(post("/api/settings/api-keys/{id}/test", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("deepseek"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.status").value("connected"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        verify(aiApiKeyService).getKey(id);
        verify(openAiService).testConnection(decryptedConfig);
    }

    @Test
    void returnsBadGatewayWhenTheProviderConnectionTestFails() throws Exception {
        UUID id = UUID.randomUUID();
        AiApiKey decryptedConfig = AiApiKey.builder()
                .id(id)
                .provider("deepseek")
                .apiKey("sk-server-only")
                .baseUrl("https://api.deepseek.com")
                .model("deepseek-chat")
                .build();
        when(aiApiKeyService.getKey(id)).thenReturn(decryptedConfig);
        when(openAiService.testConnection(decryptedConfig)).thenThrow(new AiExecutionException(
                "deepseek",
                "deepseek-chat",
                "AI Provider 鉴权失败，请检查当前 API Key",
                null
        ));

        mockMvc.perform(post("/api/settings/api-keys/{id}/test", id))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("AI Provider 鉴权失败，请检查当前 API Key"));
    }
}
