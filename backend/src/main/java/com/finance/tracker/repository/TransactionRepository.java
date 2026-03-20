package com.finance.tracker.repository;

import com.finance.tracker.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    Page<Transaction> findByUserIdOrderByTransactionDateDescCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
        select t from Transaction t
        where t.user.id = :userId
          and (lower(coalesce(t.merchant, '')) like lower(concat('%', :search, '%'))
            or lower(coalesce(t.note, '')) like lower(concat('%', :search, '%')))
          and (:type is null or t.type = :type)
          and (:accountId is null or t.account.id = :accountId)
          and (:categoryId is null or t.category.id = :categoryId)
          and (:startDate is null or t.transactionDate >= :startDate)
          and (:endDate is null or t.transactionDate <= :endDate)
          and (:minAmount is null or t.amount >= :minAmount)
          and (:maxAmount is null or t.amount <= :maxAmount)
        order by t.transactionDate desc, t.createdAt desc
        """)
    Page<Transaction> search(UUID userId, String search, String type, UUID accountId, UUID categoryId,
                             LocalDate startDate, LocalDate endDate, BigDecimal minAmount, BigDecimal maxAmount,
                             Pageable pageable);

    List<Transaction> findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(UUID userId);
    List<Transaction> findByUserIdAndTransactionDateBetween(UUID userId, LocalDate start, LocalDate end);
    long countByUserIdAndAccountId(UUID userId, UUID accountId);
    long countByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
