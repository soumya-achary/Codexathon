package com.finance.tracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) @Pattern(regexp = PASSWORD_PATTERN, message = "Password must include upper, lower, and number") String password,
            @NotBlank String displayName
    ) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record ForgotPasswordRequest(@Email @NotBlank String email) {}
    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8) @Pattern(regexp = PASSWORD_PATTERN, message = "Password must include upper, lower, and number") String newPassword
    ) {}
    public record AuthResponse(String accessToken, String refreshToken, UserSummary user) {}
    public record ForgotPasswordResponse(String message, String resetUrl) {}
    public record UserSummary(java.util.UUID id, String email, String displayName) {}
}
