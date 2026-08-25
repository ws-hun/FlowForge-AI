package com.flowforge.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthCredentialsRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        @Size(max = 254, message = "email must be less than 254 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 10, max = 128, message = "password must contain 10 to 128 characters")
        String password
) {
}
