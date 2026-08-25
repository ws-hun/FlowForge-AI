package com.flowforge.ai.dto;

public record AuthSessionResult(
        String token,
        AuthStatusResponse status
) {
}
