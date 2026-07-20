package com.flowforge.ai.dto;

public record OpenAiTaskResult(
        String summary,
        String result,
        String raw,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens
) {

    public OpenAiTaskResult(String summary, String result, String raw) {
        this(summary, result, raw, null, null, null, null, null);
    }

    public OpenAiTaskResult(String summary, String result, String raw, String provider, String model) {
        this(summary, result, raw, provider, model, null, null, null);
    }
}
