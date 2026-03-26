package com.finance.tracker.repository;

import com.finance.tracker.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {
    List<RecurringTransaction> findByUserIdOrderByNextRunDateAsc(UUID userId);
    List<RecurringTransaction> findByAccountIdInOrderByNextRunDateAsc(List<UUID> accountIds);
    Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);
    Optional<RecurringTransaction> findByIdAndAccountIdIn(UUID id, List<UUID> accountIds);
    List<RecurringTransaction> findByPausedFalseAndAutoCreateTransactionTrueAndNextRunDateLessThanEqual(LocalDate date);
    List<RecurringTransaction> findTop5ByUserIdAndPausedFalseAndNextRunDateGreaterThanEqualOrderByNextRunDateAsc(UUID userId, LocalDate date);
    List<RecurringTransaction> findTop5ByUserIdAndPausedFalseAndNextRunDateOrderByNextRunDateAsc(UUID userId, LocalDate date);
    List<RecurringTransaction> findTop5ByUserIdAndPausedFalseAndNextRunDateLessThanOrderByNextRunDateAsc(UUID userId, LocalDate date);
    List<RecurringTransaction> findTop5ByAccountIdInAndPausedFalseAndNextRunDateGreaterThanEqualOrderByNextRunDateAsc(List<UUID> accountIds, LocalDate date);
    List<RecurringTransaction> findTop5ByAccountIdInAndPausedFalseAndNextRunDateOrderByNextRunDateAsc(List<UUID> accountIds, LocalDate date);
    List<RecurringTransaction> findTop5ByAccountIdInAndPausedFalseAndNextRunDateLessThanOrderByNextRunDateAsc(List<UUID> accountIds, LocalDate date);
    long countByUserIdAndAccountId(UUID userId, UUID accountId);
    long countByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
