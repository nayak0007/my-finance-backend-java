package com.finance.tracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 128) String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 500) String redirectUrl
    ) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 128) String password
    ) {}
}
