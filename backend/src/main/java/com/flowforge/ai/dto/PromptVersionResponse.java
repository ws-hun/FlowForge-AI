package com.flowforge.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PromptVersionResponse(
        UUID id,
        UUID promptId,
        int versionNumber,
        String title,
        String category,
        String description,
        String content,
        List<String> tags,
        boolean favorite,
        LocalDateTime createdAt
) {
}
