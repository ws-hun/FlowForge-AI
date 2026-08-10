package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.dto.ProviderConnectionTestResponse;
import com.flowforge.ai.entity.AiApiKey;
import com.flowforge.ai.exception.AiExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
    void formatsStructuredDeepSeekResultsWhilePreservingTheProviderResponse() throws Exception {
        AiApiKeyService apiKeyService = mock(AiApiKeyService.class);
        when(apiKeyService.getActiveKey()).thenReturn(providerConfig());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper();
        String aiContent = objectMapper.writeValueAsString(Map.of(
                "summary", "API draft ready",
                "result", Map.of(
                        "base_url", "/api/v1",
                        "endpoints", List.of(Map.of(
                                "method", "post",
                                "path", "/flows",
                                "description", "Create a flow"
                        ))
                )
        ));
        String providerResponse = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", aiContent))),
                "usage", Map.of(
                        "prompt_tokens", 20,
                        "completion_tokens", 30,
                        "total_tokens", 50
                )
        ));
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess(providerResponse, org.springframework.http.MediaType.APPLICATION_JSON));
        OpenAiService service = new OpenAiService(builder.build(), apiKeyService, objectMapper);

        OpenAiTaskResult result = service.processTask("Design a flow API");

        assertThat(result.summary()).isEqualTo("API draft ready");
        assertThat(result.result()).isEqualTo("""
                - **Base URL:** /api/v1

                ## Endpoints

                ### POST /flows

                - **Description:** Create a flow""");
        assertThat(objectMapper.readTree(result.raw())).isEqualTo(objectMapper.readTree(providerResponse));
        assertThat(result.provider()).isEqualTo("deepseek");
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.totalTokens()).isEqualTo(50);
        server.verify();
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

    @Test
    void wrapsAMissingActiveProviderAsAnAiExecutionFailure() {
        AiApiKeyService apiKeyService = mock(AiApiKeyService.class);
        when(apiKeyService.getActiveKey()).thenThrow(new IllegalStateException("No active AI API key configured"));
        OpenAiService service = new OpenAiService(null, apiKeyService, new ObjectMapper());

        assertThatThrownBy(() -> service.processTask("Execute this task"))
                .isInstanceOfSatisfying(AiExecutionException.class, error -> {
                    assertThat(error.getProvider()).isNull();
                    assertThat(error.getModel()).isNull();
                    assertThat(error.getMessage()).isEqualTo("No active AI API key configured");
                });
    }

    @Test
    void reportsProviderTimeoutsWithoutLeakingTheTransportFailure() {
        AiApiKeyService apiKeyService = mock(AiApiKeyService.class);
        when(apiKeyService.getActiveKey()).thenReturn(providerConfig());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("Read timed out at socket layer")));
        OpenAiService service = new OpenAiService(builder.build(), apiKeyService, new ObjectMapper());

        assertThatThrownBy(() -> service.processTask("Execute this task"))
                .isInstanceOfSatisfying(AiExecutionException.class, error -> {
                    assertThat(error.getProvider()).isEqualTo("deepseek");
                    assertThat(error.getModel()).isEqualTo("deepseek-chat");
                    assertThat(error.getMessage()).isEqualTo("AI Provider request timed out");
                });
        server.verify();
    }

    @Test
    void reportsProviderConnectionFailuresAsStableGatewayErrors() {
        AiApiKeyService apiKeyService = mock(AiApiKeyService.class);
        when(apiKeyService.getActiveKey()).thenReturn(providerConfig());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withException(new ConnectException("Connection refused by test transport")));
        OpenAiService service = new OpenAiService(builder.build(), apiKeyService, new ObjectMapper());

        assertThatThrownBy(() -> service.processTask("Execute this task"))
                .isInstanceOfSatisfying(AiExecutionException.class, error -> {
                    assertThat(error.getProvider()).isEqualTo("deepseek");
                    assertThat(error.getModel()).isEqualTo("deepseek-chat");
                    assertThat(error.getMessage()).isEqualTo("AI Provider connection failed");
                });
        server.verify();
    }

    @Test
    void verifiesASavedProviderWithoutCreatingATaskExecution() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/models"))
                .andRespond(withSuccess("{\"data\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));
        OpenAiService service = new OpenAiService(builder.build(), null, new ObjectMapper());

        ProviderConnectionTestResponse response = service.testConnection(providerConfig());

        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.model()).isEqualTo("deepseek-chat");
        assertThat(response.status()).isEqualTo("connected");
        assertThat(response.checkedAt()).isNotNull();
        server.verify();
    }

    @Test
    void keepsConnectionTestFailuresOnTheProviderGatewayBoundary() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/models"))
                .andRespond(withStatus(HttpStatusCode.valueOf(401))
                        .body("provider-internal-sensitive-body"));
        OpenAiService service = new OpenAiService(builder.build(), null, new ObjectMapper());

        assertThatThrownBy(() -> service.testConnection(providerConfig()))
                .isInstanceOfSatisfying(AiExecutionException.class, error -> {
                    assertThat(error.getProvider()).isEqualTo("deepseek");
                    assertThat(error.getModel()).isEqualTo("deepseek-chat");
                    assertThat(error.getMessage()).isEqualTo("AI Provider 鉴权失败，请检查当前 API Key");
                });
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400,AI Provider 拒绝了当前请求",
            "401,AI Provider 鉴权失败，请检查当前 API Key",
            "403,AI Provider 鉴权失败，请检查当前 API Key",
            "408,AI Provider request timed out",
            "429,AI Provider 请求频率受限，请稍后重试",
            "500,AI Provider 暂时不可用",
            "503,AI Provider 暂时不可用"
    })
    void mapsProviderHttpFailuresWithoutExposingTheResponseBody(int status, String expectedMessage) {
        AiApiKeyService apiKeyService = mock(AiApiKeyService.class);
        when(apiKeyService.getActiveKey()).thenReturn(providerConfig());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status))
                        .body("provider-internal-sensitive-body"));
        OpenAiService service = new OpenAiService(builder.build(), apiKeyService, new ObjectMapper());

        assertThatThrownBy(() -> service.processTask("Execute this task"))
                .isInstanceOfSatisfying(AiExecutionException.class, error -> {
                    assertThat(error.getProvider()).isEqualTo("deepseek");
                    assertThat(error.getModel()).isEqualTo("deepseek-chat");
                    assertThat(error.getMessage()).isEqualTo(expectedMessage);
                    assertThat(error.getMessage()).doesNotContain("provider-internal-sensitive-body");
                });
        server.verify();
    }

    private AiApiKey providerConfig() {
        return AiApiKey.builder()
                .provider("deepseek")
                .apiKey("sk-test")
                .baseUrl("https://api.deepseek.com")
                .model("deepseek-chat")
                .build();
    }
}
