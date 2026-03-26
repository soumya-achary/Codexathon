package com.finance.tracker.repository;

import com.finance.tracker.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByUserIdOrderByTargetDateAsc(UUID userId);
    @Query("""
        select g from Goal g
        where g.user.id = :userId or (g.linkedAccount is not null and g.linkedAccount.id in :accountIds)
        order by g.targetDate asc
        """)
    List<Goal> findAccessibleGoals(UUID userId, List<UUID> accountIds);
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);
    long countByUserIdAndLinkedAccountId(UUID userId, UUID accountId);
}
