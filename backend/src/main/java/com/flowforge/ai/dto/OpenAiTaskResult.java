package com.flowforge.ai.dto;

public record OpenAiTaskResult(
        String summary,
        String result,
        String raw
) {
}
