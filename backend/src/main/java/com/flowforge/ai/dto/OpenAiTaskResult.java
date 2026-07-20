package com.flowforge.ai.dto;

public record OpenAiTaskResult(
        String summary,
        String result,
        String raw,
        String provider,
        String model
) {

    public OpenAiTaskResult(String summary, String result, String raw) {
        this(summary, result, raw, null, null);
    }
}
