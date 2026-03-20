package com.finance.tracker.repository;

import com.finance.tracker.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByUserIdOrderByTargetDateAsc(UUID userId);
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);
    long countByUserIdAndLinkedAccountId(UUID userId, UUID accountId);
}
