package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.ai.dto.OpenAiTaskResult;
import com.flowforge.ai.entity.AiApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final RestClient restClient;
    private final AiApiKeyService aiApiKeyService;
    private final ObjectMapper objectMapper;

    public OpenAiTaskResult processTask(String input) {
        AiApiKey activeKey = aiApiKeyService.getActiveKey();
        return switch (activeKey.getProvider()) {
            case "deepseek" -> processWithDeepSeek(input, activeKey);
            case "openai" -> processWithOpenAi(input, activeKey);
            default -> throw new IllegalStateException("Unsupported AI provider: " + activeKey.getProvider());
        };
    }

    private OpenAiTaskResult processWithDeepSeek(String input, AiApiKey config) {
        validateConfig(config);

        Map<String, Object> requestBody = Map.of(
                "model", config.getModel(),
                "messages", buildMessages(input),
                "response_format", Map.of("type", "json_object"),
                "max_tokens", 4096,
                "stream", false
        );

        String raw = sendRequest(config, "/chat/completions", requestBody);
        String jsonText = extractChatCompletionText(raw);
        JsonNode parsed = parseJson(jsonText);

        return new OpenAiTaskResult(
                parsed.path("summary").asText(""),
                parsed.path("result").asText(""),
                raw
        );
    }

    private OpenAiTaskResult processWithOpenAi(String input, AiApiKey config) {
        validateConfig(config);

        Map<String, Object> requestBody = Map.of(
                "model", config.getModel(),
                "input", buildMessages(input),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "flowforge_task_result",
                                "strict", true,
                                "schema", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "properties", Map.of(
                                                "summary", Map.of("type", "string"),
                                                "result", Map.of("type", "string")
                                        ),
                                        "required", List.of("summary", "result")
                                )
                        )
                )
        );

        String raw = sendRequest(config, "/responses", requestBody);
        String jsonText = extractOpenAiResponsesText(raw);
        JsonNode parsed = parseJson(jsonText);

        return new OpenAiTaskResult(
                parsed.path("summary").asText(""),
                parsed.path("result").asText(""),
                raw
        );
    }

    private void validateConfig(AiApiKey config) {
        if (config == null || !StringUtils.hasText(config.getApiKey())) {
            throw new IllegalStateException("No active AI API key configured");
        }
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new IllegalStateException("AI base URL is not configured");
        }
        if (!StringUtils.hasText(config.getModel())) {
            throw new IllegalStateException("AI model is not configured");
        }
    }

    private List<Map<String, String>> buildMessages(String input) {
        return List.of(
                Map.of(
                        "role", "system",
                        "content", "You are FlowForge AI. Analyze the user's task and always return valid JSON with summary and result fields."
                ),
                Map.of(
                        "role", "user",
                        "content", buildPrompt(input)
                )
        );
    }

    private String buildPrompt(String input) {
        return """
                请处理下面的用户任务，并严格返回 JSON：
                {
                  "summary": "用一句话总结任务和处理结论",
                  "result": "详细、可执行、结构化的处理结果"
                }

                用户任务：
                %s
                """.formatted(input);
    }

    private String sendRequest(AiApiKey config, String path, Map<String, Object> requestBody) {
        try {
            JsonNode response = restClient.post()
                    .uri(normalizeBaseUrl(config.getBaseUrl()) + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            try {
                return objectMapper.writeValueAsString(response);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize AI response", e);
            }
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("AI API error: " + e.getResponseBodyAsString(), e);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String extractOpenAiResponsesText(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode outputText = root.path("output_text");
            if (outputText.isTextual() && StringUtils.hasText(outputText.asText())) {
                return outputText.asText();
            }

            for (JsonNode output : root.path("output")) {
                for (JsonNode content : output.path("content")) {
                    JsonNode text = content.path("text");
                    if (text.isTextual() && StringUtils.hasText(text.asText())) {
                        return text.asText();
                    }
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse OpenAI raw response", e);
        }

        throw new IllegalStateException("OpenAI response does not contain output text");
    }

    private String extractChatCompletionText(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual() && StringUtils.hasText(content.asText())) {
                return content.asText();
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse AI raw response", e);
        }

        throw new IllegalStateException("AI response does not contain message content");
    }

    private JsonNode parseJson(String jsonText) {
        try {
            return objectMapper.readTree(jsonText);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI output is not valid JSON: " + jsonText, e);
        }
    }
}
