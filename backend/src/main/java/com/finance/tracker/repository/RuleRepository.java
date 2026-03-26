package com.finance.tracker.repository;

import com.finance.tracker.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleRepository extends JpaRepository<Rule, UUID> {
    List<Rule> findByUserIdOrderByPriorityAscCreatedAtAsc(UUID userId);
    Optional<Rule> findByIdAndUserId(UUID id, UUID userId);
}
