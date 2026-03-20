package com.finance.tracker.repository;

import com.finance.tracker.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByUserIdAndMonthAndYearOrderByCategoryNameAsc(UUID userId, int month, int year);
    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndCategoryIdAndMonthAndYear(UUID userId, UUID categoryId, int month, int year);
    boolean existsByUserIdAndCategoryIdAndMonthAndYearAndIdNot(UUID userId, UUID categoryId, int month, int year, UUID id);
    long countByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
