package com.finance.tracker.repository;

import com.finance.tracker.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByUserIdOrUserIsNullOrderByTypeAscNameAsc(UUID userId);
    Optional<Category> findByIdAndUserIdOrIdAndUserIsNull(UUID id1, UUID userId, UUID id2);
    boolean existsByUserIdAndNameIgnoreCaseAndTypeIgnoreCase(UUID userId, String name, String type);
    boolean existsByUserIdAndNameIgnoreCaseAndTypeIgnoreCaseAndIdNot(UUID userId, String name, String type, UUID id);
}
