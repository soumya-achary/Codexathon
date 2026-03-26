package com.finance.tracker.service;

import com.finance.tracker.dto.AuthDtos;
import com.finance.tracker.entity.Category;
import com.finance.tracker.entity.RefreshToken;
import com.finance.tracker.entity.User;
import com.finance.tracker.exception.ApiException;
import com.finance.tracker.repository.CategoryRepository;
import com.finance.tracker.repository.RefreshTokenRepository;
import com.finance.tracker.repository.UserRepository;
import com.finance.tracker.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private static final Map<String, List<String>> DEFAULT_CATEGORIES = Map.of(
            "expense", List.of("Food", "Rent", "Utilities", "Transport", "Entertainment", "Shopping", "Health", "Education", "Travel", "Subscriptions", "Miscellaneous"),
            "income", List.of("Salary", "Freelance", "Bonus", "Investment", "Gift", "Refund", "Other")
    );

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthRateLimitService authRateLimitService;
    private final AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            CategoryRepository categoryRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AuthRateLimitService authRateLimitService,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authRateLimitService = authRateLimitService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        seedCategories(user);
        auditService.recordEvent(user, "signup_completed", Map.of("email", user.getEmail()));
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, String rateLimitKey) {
        if (!authRateLimitService.isAllowed(rateLimitKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Please wait a few minutes and try again.");
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));
        } catch (BadCredentialsException ex) {
            authRateLimitService.registerFailure(rateLimitKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        authRateLimitService.reset(rateLimitKey);
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(LocalDateTime.now()) || !jwtService.isValid(stored.getToken())) {
            refreshTokenRepository.delete(stored);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        return issueTokens(stored.getUser());
    }

    @Transactional
    protected AuthDtos.AuthResponse issueTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
        String access = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refresh = jwtService.generateRefreshToken(user.getId());
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(refresh);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(refreshToken);
        return new AuthDtos.AuthResponse(access, refresh, new AuthDtos.UserSummary(user.getId(), user.getEmail(), user.getDisplayName()));
    }

    private void seedCategories(User user) {
        DEFAULT_CATEGORIES.forEach((type, names) -> names.forEach(name -> {
            Category category = new Category();
            category.setUser(user);
            category.setName(name);
            category.setType(type);
            category.setColor("expense".equals(type) ? "#ef4444" : "#16a34a");
            category.setIcon(name.substring(0, Math.min(2, name.length())).toUpperCase());
            categoryRepository.save(category);
        }));
    }
}
