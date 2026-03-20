package com.finance.tracker.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimitService {
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public boolean isAllowed(String key) {
        cleanup(key);
        AttemptWindow window = attempts.get(key);
        return window == null || window.count < MAX_ATTEMPTS;
    }

    public void registerFailure(String key) {
        cleanup(key);
        attempts.compute(key, (ignored, current) -> {
            Instant now = Instant.now();
            if (current == null || now.isAfter(current.expiresAt)) {
                return new AttemptWindow(1, now.plus(WINDOW));
            }
            current.count++;
            return current;
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private void cleanup(String key) {
        AttemptWindow window = attempts.get(key);
        if (window != null && Instant.now().isAfter(window.expiresAt)) {
            attempts.remove(key);
        }
    }

    private static class AttemptWindow {
        int count;
        Instant expiresAt;

        AttemptWindow(int count, Instant expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }
}
