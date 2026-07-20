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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OpenAiService {

    private static final Pattern SUMMARY_PATTERN = Pattern.compile("\"summary\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    private final RestClient restClient;
    private final AiApiKeyService aiApiKeyService;
    private final ObjectMapper objectMapper;

    public OpenAiTaskResult processTask(String input) {
        AiApiKey activeKey = aiApiKeyService.getActiveKey();
        OpenAiTaskResult result = switch (activeKey.getProvider()) {
            case "deepseek" -> processWithDeepSeek(input, activeKey);
            case "openai" -> processWithOpenAi(input, activeKey);
            default -> throw new IllegalStateException("Unsupported AI provider: " + activeKey.getProvider());
        };

        return new OpenAiTaskResult(
                result.summary(),
                result.result(),
                result.raw(),
                activeKey.getProvider(),
                activeKey.getModel()
        );
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
        return parseTaskResult(jsonText, raw);
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
        return parseTaskResult(jsonText, raw);
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
                        "content", """
                                You are FlowForge AI. Return one strict JSON object only.
                                Requirements:
                                - No markdown fences.
                                - No comments.
                                - No placeholder tokens such as [...] or {...}.
                                - The summary field must be a string.
                                - The result field must be a string. If the result is structured, write it as readable markdown text inside the string.
                                """
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
                  "result": "详细、可执行、结构化的处理结果。必须是字符串，不要返回对象或数组。"
                }

                注意：
                - 只返回 JSON，不要使用 Markdown 代码块。
                - 不要使用 [...]、{...} 这类占位符。
                - 如果内容很多，请写成字符串中的分段文本。

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

    private OpenAiTaskResult parseTaskResult(String aiText, String raw) {
        JsonNode parsed = tryParseJson(aiText);
        if (parsed == null) {
            return new OpenAiTaskResult(
                    extractSummaryFromText(aiText),
                    aiText,
                    raw
            );
        }

        return new OpenAiTaskResult(
                readSummary(parsed, aiText),
                readResult(parsed, aiText),
                raw
        );
    }

    private JsonNode tryParseJson(String jsonText) {
        String normalized = stripMarkdownFence(jsonText.trim());
        try {
            return objectMapper.readTree(normalized);
        } catch (JsonProcessingException e) {
            String candidate = extractJsonObject(normalized);
            if (!candidate.equals(normalized)) {
                try {
                    return objectMapper.readTree(candidate);
                } catch (JsonProcessingException ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    private String readSummary(JsonNode parsed, String fallbackText) {
        JsonNode summary = parsed.path("summary");
        if (summary.isTextual() && StringUtils.hasText(summary.asText())) {
            return summary.asText();
        }
        return extractSummaryFromText(fallbackText);
    }

    private String readResult(JsonNode parsed, String fallbackText) {
        JsonNode result = parsed.path("result");
        if (result.isMissingNode() || result.isNull()) {
            return fallbackText;
        }
        if (result.isTextual()) {
            return result.asText();
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return result.toString();
        }
    }

    private String extractSummaryFromText(String text) {
        Matcher matcher = SUMMARY_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return objectMapper.readValue("\"" + matcher.group(1) + "\"", String.class);
            } catch (JsonProcessingException ignored) {
                return matcher.group(1);
            }
        }

        String firstLine = text.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("AI 已返回结果，但格式不是严格 JSON");
        return firstLine.length() > 160 ? firstLine.substring(0, 160) : firstLine;
    }

    private String stripMarkdownFence(String text) {
        if (text.startsWith("```")) {
            return text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        return text;
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
