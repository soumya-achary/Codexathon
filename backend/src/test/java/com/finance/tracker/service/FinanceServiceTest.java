package com.finance.tracker.service;

import com.finance.tracker.exception.ApiException;
import com.finance.tracker.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private RuleRepository ruleRepository;
    @Mock private AccountMemberRepository accountMemberRepository;
    @Mock private AuditService auditService;

    @Test
    void deleteAccountRejectsLinkedData() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, ruleRepository, accountMemberRepository, auditService);
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        var account = new com.finance.tracker.entity.Account();
        account.setId(accountId);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(java.util.Optional.of(account));
        when(transactionRepository.countByAccountId(accountId)).thenReturn(1L);

        assertThatThrownBy(() -> financeService.deleteAccount(userId, accountId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("linked to transactions");
    }

    @Test
    void deleteCategoryRejectsLinkedData() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, ruleRepository, accountMemberRepository, auditService);
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

    @Test
    void healthScoreReturnsFactorBreakdownAndSuggestions() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, ruleRepository, accountMemberRepository, auditService);
        UUID userId = UUID.randomUUID();
        var account = new com.finance.tracker.entity.Account();
        account.setId(UUID.randomUUID());
        account.setOpeningBalance(new BigDecimal("1000"));
        account.setCurrentBalance(new BigDecimal("2500"));
        when(accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(account));
        when(accountMemberRepository.findByUserId(userId)).thenReturn(List.of());
        when(budgetRepository.findAccessibleBudgets(eq(userId), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(transactionRepository.findByAccountIdInAndTransactionDateBetween(any(), any(), any())).thenReturn(List.of());

        var response = financeService.getHealthScore(userId);

        assertThat(response).containsKeys("score", "status", "factors", "suggestions");
        assertThat((List<?>) response.get("factors")).hasSize(4);
        assertThat((List<?>) response.get("suggestions")).isNotEmpty();
    }

    @Test
    void insightsHandlesAccountsWithMissingBalances() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, ruleRepository, accountMemberRepository, auditService);
        UUID userId = UUID.randomUUID();
        var account = new com.finance.tracker.entity.Account();
        account.setId(UUID.randomUUID());
        account.setOpeningBalance(null);
        account.setCurrentBalance(null);
        when(accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(account));
        when(accountMemberRepository.findByUserId(userId)).thenReturn(List.of());
        when(budgetRepository.findAccessibleBudgets(eq(userId), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(transactionRepository.findByAccountIdInAndTransactionDateBetween(any(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        var response = financeService.getInsights(userId);

        assertThat(response).containsKeys("healthScore", "highlights", "incomeVsExpense", "savingsRateTrend", "netWorthTrend", "categoryTrends");
        assertThat((List<?>) response.get("netWorthTrend")).isNotEmpty();
    }

    @Test
    void healthScoreHandlesBudgetsWithoutAlertThreshold() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, ruleRepository, accountMemberRepository, auditService);
        UUID userId = UUID.randomUUID();
        var account = new com.finance.tracker.entity.Account();
        account.setId(UUID.randomUUID());
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setCurrentBalance(new BigDecimal("250"));
        var category = new com.finance.tracker.entity.Category();
        category.setId(UUID.randomUUID());
        category.setName("Food");
        var budget = new com.finance.tracker.entity.Budget();
        budget.setId(UUID.randomUUID());
        budget.setCategory(category);
        budget.setAmount(new BigDecimal("500"));
        budget.setAlertThresholdPercent(null);
        when(accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(account));
        when(accountMemberRepository.findByUserId(userId)).thenReturn(List.of());
        when(budgetRepository.findAccessibleBudgets(eq(userId), any(), anyInt(), anyInt())).thenReturn(List.of(budget));
        when(transactionRepository.findByAccountIdInAndTransactionDateBetween(any(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        var response = financeService.getHealthScore(userId);

        assertThat(response).containsKeys("score", "status", "factors", "suggestions");
    }

    @Test
    void forecastDailyHandlesAccountsWithMissingBalances() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, ruleRepository, accountMemberRepository, auditService);
        UUID userId = UUID.randomUUID();
        var account = new com.finance.tracker.entity.Account();
        account.setId(UUID.randomUUID());
        account.setCurrentBalance(null);
        when(accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(account));
        when(accountMemberRepository.findByUserId(userId)).thenReturn(List.of());
        when(recurringTransactionRepository.findByAccountIdInOrderByNextRunDateAsc(any())).thenReturn(List.of());
        when(transactionRepository.findByAccountIdInAndTransactionDateBetween(any(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        var response = financeService.getForecastDaily(userId);

        assertThat(response).isNotEmpty();
    }

    @Test
    void forecastMonthSkipsRecurringItemsWithoutNextRunDate() {
        FinanceService financeService = new FinanceService(userRepository, accountRepository, categoryRepository, transactionRepository, budgetRepository, goalRepository, recurringTransactionRepository, ruleRepository, accountMemberRepository, auditService);
        UUID userId = UUID.randomUUID();
        var account = new com.finance.tracker.entity.Account();
        account.setId(UUID.randomUUID());
        account.setCurrentBalance(new BigDecimal("500"));
        when(accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(account));
        when(accountMemberRepository.findByUserId(userId)).thenReturn(List.of());
        var recurring = new com.finance.tracker.entity.RecurringTransaction();
        recurring.setPaused(false);
        recurring.setTitle("Rent");
        recurring.setType("expense");
        recurring.setAmount(new BigDecimal("100"));
        recurring.setNextRunDate(null);
        when(recurringTransactionRepository.findByAccountIdInOrderByNextRunDateAsc(any())).thenReturn(List.of(recurring));
        when(transactionRepository.findByAccountIdInAndTransactionDateBetween(any(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        var response = financeService.getForecastMonth(userId);

        assertThat(response).containsEntry("currentBalance", new BigDecimal("500"));
        assertThat((List<?>) response.get("upcomingKnownExpenses")).isEmpty();
    }
}



