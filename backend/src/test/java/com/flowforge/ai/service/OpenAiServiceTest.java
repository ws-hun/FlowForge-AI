package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.entity.AiApiKey;
import com.flowforge.ai.exception.AiExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiServiceTest {

    private final OpenAiService openAiService = new OpenAiService(null, null, new ObjectMapper());

    @Test
    void readsDeepSeekTokenUsageFromChatCompletionResponse() {
        AiApiKey config = AiApiKey.builder()
                .provider("deepseek")
                .model("deepseek-chat")
                .build();
        OpenAiTaskResult result = new OpenAiTaskResult(
                "Summary",
                "Result",
                "{\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200}}"
        );

        OpenAiTaskResult enriched = openAiService.attachExecutionMetadata(config, result);

        assertThat(enriched.provider()).isEqualTo("deepseek");
        assertThat(enriched.model()).isEqualTo("deepseek-chat");
        assertThat(enriched.inputTokens()).isEqualTo(120);
        assertThat(enriched.outputTokens()).isEqualTo(80);
        assertThat(enriched.totalTokens()).isEqualTo(200);
    }

    @Test
    void readsOpenAiTokenUsageAndDerivesTotalWhenItIsMissing() {
        AiApiKey config = AiApiKey.builder()
                .provider("openai")
                .model("gpt-4.1")
                .build();
        OpenAiTaskResult result = new OpenAiTaskResult(
                "Summary",
                "Result",
                "{\"usage\":{\"input_tokens\":75,\"output_tokens\":25}}"
        );

        OpenAiTaskResult enriched = openAiService.attachExecutionMetadata(config, result);

        assertThat(enriched.inputTokens()).isEqualTo(75);
        assertThat(enriched.outputTokens()).isEqualTo(25);
        assertThat(enriched.totalTokens()).isEqualTo(100);
    }

    @Test
    void keepsUsageEmptyWhenProviderResponseDoesNotIncludeIt() {
        AiApiKey config = AiApiKey.builder()
                .provider("openai")
                .model("gpt-4.1")
                .build();

        OpenAiTaskResult enriched = openAiService.attachExecutionMetadata(
                config,
                new OpenAiTaskResult("Summary", "Result", "{}")
        );

        assertThat(enriched.inputTokens()).isNull();
        assertThat(enriched.outputTokens()).isNull();
        assertThat(enriched.totalTokens()).isNull();
    }

    @Test
    void preservesProviderAndModelWhenExecutionFails() {
        AiApiKeyService apiKeyService = mock(AiApiKeyService.class);
        AiApiKey config = AiApiKey.builder()
                .provider("unsupported")
                .model("custom-model")
                .build();
        when(apiKeyService.getActiveKey()).thenReturn(config);
        OpenAiService service = new OpenAiService(null, apiKeyService, new ObjectMapper());

        assertThatThrownBy(() -> service.processTask("Execute this task"))
                .isInstanceOfSatisfying(AiExecutionException.class, error -> {
                    assertThat(error.getProvider()).isEqualTo("unsupported");
                    assertThat(error.getModel()).isEqualTo("custom-model");
                    assertThat(error.getMessage()).isEqualTo("Unsupported AI provider: unsupported");
                });
    }
}
