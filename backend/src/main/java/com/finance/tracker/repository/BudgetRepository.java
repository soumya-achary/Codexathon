package com.finance.tracker.repository;

import com.finance.tracker.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByUserIdAndMonthAndYearOrderByCategoryNameAsc(UUID userId, int month, int year);
    @Query("""
        select b from Budget b
        where (b.user.id = :userId or (b.account is not null and b.account.id in :accountIds))
          and b.month = :month and b.year = :year
        order by b.category.name asc
        """)
    List<Budget> findAccessibleBudgets(UUID userId, List<UUID> accountIds, int month, int year);
    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndCategoryIdAndMonthAndYear(UUID userId, UUID categoryId, int month, int year);
    boolean existsByUserIdAndCategoryIdAndMonthAndYearAndIdNot(UUID userId, UUID categoryId, int month, int year, UUID id);
    long countByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
