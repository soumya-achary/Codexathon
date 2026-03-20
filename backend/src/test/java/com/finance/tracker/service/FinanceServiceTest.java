package com.finance.tracker.service;

import com.finance.tracker.exception.ApiException;
import com.finance.tracker.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private AuditService auditService;

    @Test
    void deleteAccountRejectsLinkedData() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, auditService);
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        var account = new com.finance.tracker.entity.Account();
        account.setId(accountId);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(java.util.Optional.of(account));
        when(transactionRepository.countByUserIdAndAccountId(userId, accountId)).thenReturn(1L);

        assertThatThrownBy(() -> financeService.deleteAccount(userId, accountId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("linked to transactions");
    }

    @Test
    void deleteCategoryRejectsLinkedData() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, auditService);
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        var user = new com.finance.tracker.entity.User();
        user.setId(userId);
        var category = new com.finance.tracker.entity.Category();
        category.setId(categoryId);
        category.setUser(user);
        when(categoryRepository.findById(categoryId)).thenReturn(java.util.Optional.of(category));
        when(transactionRepository.countByUserIdAndCategoryId(userId, categoryId)).thenReturn(0L);
        when(budgetRepository.countByUserIdAndCategoryId(userId, categoryId)).thenReturn(1L);

        assertThatThrownBy(() -> financeService.deleteCategory(userId, categoryId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already used");
    }
}
