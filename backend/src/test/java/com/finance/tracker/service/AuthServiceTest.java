package com.finance.tracker.service;

import com.finance.tracker.dto.AuthDtos;
import com.finance.tracker.entity.PasswordResetToken;
import com.finance.tracker.entity.User;
import com.finance.tracker.exception.ApiException;
import com.finance.tracker.repository.CategoryRepository;
import com.finance.tracker.repository.PasswordResetTokenRepository;
import com.finance.tracker.repository.RefreshTokenRepository;
import com.finance.tracker.repository.UserRepository;
import com.finance.tracker.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private AuthRateLimitService authRateLimitService;
    @Mock private AuditService auditService;

    @Test
    void forgotPasswordCreatesResetLinkForExistingUser() {
        AuthService authService = new AuthService(userRepository, refreshTokenRepository, passwordResetTokenRepository, categoryRepository, passwordEncoder, authenticationManager, jwtService, "http://localhost:5173", 30, authRateLimitService, auditService);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("soumya@gmail.com");
        when(userRepository.findByEmailIgnoreCase("soumya@gmail.com")).thenReturn(Optional.of(user));

        AuthDtos.ForgotPasswordResponse response = authService.forgotPassword(new AuthDtos.ForgotPasswordRequest("soumya@gmail.com"));

        assertThat(response.message()).contains("Reset link created");
        assertThat(response.resetUrl()).contains("http://localhost:5173/login?mode=reset&token=");
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(29));
    }

    @Test
    void resetPasswordUpdatesPasswordAndRevokesRefreshTokens() {
        AuthService authService = new AuthService(userRepository, refreshTokenRepository, passwordResetTokenRepository, categoryRepository, passwordEncoder, authenticationManager, jwtService, "http://localhost:5173", 30, authRateLimitService, auditService);
        User user = new User();
        user.setId(UUID.randomUUID());
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken("reset-token");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByToken("reset-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("Soumya123")).thenReturn("encoded");

        var response = authService.resetPassword(new AuthDtos.ResetPasswordRequest("reset-token", "Soumya123"));

        assertThat(response.get("message")).contains("Password reset successfully");
        assertThat(user.getPasswordHash()).isEqualTo("encoded");
        verify(userRepository).save(user);
        verify(refreshTokenRepository).deleteByUser(user);
        verify(passwordResetTokenRepository).save(token);
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        AuthService authService = new AuthService(userRepository, refreshTokenRepository, passwordResetTokenRepository, categoryRepository, passwordEncoder, authenticationManager, jwtService, "http://localhost:5173", 30, authRateLimitService, auditService);
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expired-token");
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(new AuthDtos.ResetPasswordRequest("expired-token", "Soumya123")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid or expired");
    }
}
