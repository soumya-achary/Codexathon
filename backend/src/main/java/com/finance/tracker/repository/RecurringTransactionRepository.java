package com.finance.tracker.repository;

import com.finance.tracker.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {
    List<RecurringTransaction> findByUserIdOrderByNextRunDateAsc(UUID userId);
    Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);
    List<RecurringTransaction> findByPausedFalseAndAutoCreateTransactionTrueAndNextRunDateLessThanEqual(LocalDate date);
    List<RecurringTransaction> findTop5ByUserIdAndPausedFalseAndNextRunDateGreaterThanEqualOrderByNextRunDateAsc(UUID userId, LocalDate date);
    List<RecurringTransaction> findTop5ByUserIdAndPausedFalseAndNextRunDateOrderByNextRunDateAsc(UUID userId, LocalDate date);
    List<RecurringTransaction> findTop5ByUserIdAndPausedFalseAndNextRunDateLessThanOrderByNextRunDateAsc(UUID userId, LocalDate date);
    long countByUserIdAndAccountId(UUID userId, UUID accountId);
    long countByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
