package com.flowforge.ai.dto;

public record AuthStatusResponse(
        boolean setupRequired,
        boolean authenticated,
        AuthUserResponse user
) {
}
