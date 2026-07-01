package com.flowforge.ai.dto;

public record TaskRunResponse(
        String summary,
        String result,
        String raw
) {
}
