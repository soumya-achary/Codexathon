package com.finance.tracker.repository;

import com.finance.tracker.entity.AccountMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountMemberRepository extends JpaRepository<AccountMember, UUID> {
    List<AccountMember> findByUserId(UUID userId);
    List<AccountMember> findByAccountIdOrderByCreatedAtAsc(UUID accountId);
    Optional<AccountMember> findByAccountIdAndUserId(UUID accountId, UUID userId);
    boolean existsByAccountIdAndUserId(UUID accountId, UUID userId);
}
