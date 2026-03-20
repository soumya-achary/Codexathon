package com.finance.tracker.repository;

import com.finance.tracker.entity.PasswordResetToken;
import com.finance.tracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(String token);
    List<PasswordResetToken> findAllByUser(User user);
    void deleteByUser(User user);
    void deleteByExpiresAtBefore(LocalDateTime time);
}
