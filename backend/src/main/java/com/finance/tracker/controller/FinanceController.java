package com.finance.tracker.controller;

import com.finance.tracker.dto.FinanceDtos;
import com.finance.tracker.entity.*;
import com.finance.tracker.security.AppUserDetails;
import com.finance.tracker.service.FinanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FinanceController {
    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getDashboard(user.getId());
    }

    @GetMapping("/accounts")
    public List<Account> accounts(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getAccounts(user.getId());
    }

    @PostMapping("/accounts")
    public Account createAccount(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.AccountRequest request) {
        return financeService.saveAccount(user.getId(), null, request);
    }

    @PutMapping("/accounts/{id}")
    public Account updateAccount(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.AccountRequest request) {
        return financeService.saveAccount(user.getId(), id, request);
    }

    @PostMapping("/accounts/transfer")
    public void transfer(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.TransferRequest request) {
        financeService.transfer(user.getId(), request);
    }

    @DeleteMapping("/accounts/{id}")
    public void deleteAccount(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        financeService.deleteAccount(user.getId(), id);
    }

    @GetMapping("/categories")
    public List<Category> categories(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getCategories(user.getId());
    }

    @PostMapping("/categories")
    public Category createCategory(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.CategoryRequest request) {
        return financeService.saveCategory(user.getId(), null, request);
    }

    @PutMapping("/categories/{id}")
    public Category updateCategory(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.CategoryRequest request) {
        return financeService.saveCategory(user.getId(), id, request);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        financeService.deleteCategory(user.getId(), id);
    }

    @GetMapping("/transactions")
    public Page<Transaction> transactions(@AuthenticationPrincipal AppUserDetails user,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) UUID accountId,
                                          @RequestParam(required = false) UUID categoryId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                          @RequestParam(required = false) BigDecimal minAmount,
                                          @RequestParam(required = false) BigDecimal maxAmount,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return financeService.getTransactions(user.getId(), search, type, accountId, categoryId, startDate, endDate, minAmount, maxAmount, page, size);
    }

    @GetMapping("/transactions/{id}")
    public Transaction transaction(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        return financeService.getTransaction(user.getId(), id);
    }

    @PostMapping("/transactions")
    public Transaction createTransaction(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.TransactionRequest request) {
        return financeService.saveTransaction(user.getId(), null, request);
    }

    @PostMapping("/transactions/import")
    public List<Transaction> importTransactions(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.TransactionImportRequest request) {
        return financeService.importTransactions(user.getId(), request);
    }

    @PutMapping("/transactions/{id}")
    public Transaction updateTransaction(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.TransactionRequest request) {
        return financeService.saveTransaction(user.getId(), id, request);
    }

    @DeleteMapping("/transactions/{id}")
    public void deleteTransaction(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        financeService.deleteTransaction(user.getId(), id);
    }

    @GetMapping("/budgets")
    public List<Budget> budgets(@AuthenticationPrincipal AppUserDetails user, @RequestParam int month, @RequestParam int year) {
        return financeService.getBudgets(user.getId(), month, year);
    }

    @PostMapping("/budgets")
    public Budget createBudget(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.BudgetRequest request) {
        return financeService.saveBudget(user.getId(), null, request);
    }

    @PutMapping("/budgets/{id}")
    public Budget updateBudget(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.BudgetRequest request) {
        return financeService.saveBudget(user.getId(), id, request);
    }

    @DeleteMapping("/budgets/{id}")
    public void deleteBudget(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        financeService.deleteBudget(user.getId(), id);
    }

    @PostMapping("/budgets/duplicate-last-month")
    public List<Budget> duplicateLastMonthBudget(@AuthenticationPrincipal AppUserDetails user, @RequestParam int month, @RequestParam int year) {
        return financeService.duplicatePreviousMonthBudgets(user.getId(), month, year);
    }

    @GetMapping("/goals")
    public List<Goal> goals(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getGoals(user.getId());
    }

    @PostMapping("/goals")
    public Goal createGoal(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.GoalRequest request) {
        return financeService.saveGoal(user.getId(), null, request);
    }

    @PutMapping("/goals/{id}")
    public Goal updateGoal(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.GoalRequest request) {
        return financeService.saveGoal(user.getId(), id, request);
    }

    @DeleteMapping("/goals/{id}")
    public void deleteGoal(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        financeService.deleteGoal(user.getId(), id);
    }

    @PostMapping("/goals/{id}/contribute")
    public Goal contribute(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.GoalAmountRequest request) {
        return financeService.changeGoalAmount(user.getId(), id, request, true);
    }

    @PostMapping("/goals/{id}/withdraw")
    public Goal withdraw(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.GoalAmountRequest request) {
        return financeService.changeGoalAmount(user.getId(), id, request, false);
    }

    @GetMapping("/recurring")
    public List<RecurringTransaction> recurring(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getRecurring(user.getId());
    }

    @PostMapping("/recurring")
    public RecurringTransaction createRecurring(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.RecurringRequest request) {
        return financeService.saveRecurring(user.getId(), null, request);
    }

    @PutMapping("/recurring/{id}")
    public RecurringTransaction updateRecurring(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.RecurringRequest request) {
        return financeService.saveRecurring(user.getId(), id, request);
    }

    @DeleteMapping("/recurring/{id}")
    public void deleteRecurring(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        financeService.deleteRecurring(user.getId(), id);
    }

    @GetMapping("/audit-events")
    public List<AuditEvent> auditEvents(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getAuditEvents(user.getId());
    }

    @PostMapping("/audit-events")
    public void createAuditEvent(@AuthenticationPrincipal AppUserDetails user, @RequestBody Map<String, Object> payload) {
        String eventType = String.valueOf(payload.getOrDefault("eventType", "ui_event"));
        financeService.recordAuditEvent(user.getId(), eventType, payload);
    }

    @GetMapping("/reports/category-spend")
    public Map<String, Object> categorySpend(@AuthenticationPrincipal AppUserDetails user,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(required = false) UUID accountId,
                                             @RequestParam(required = false) UUID categoryId) {
        return financeService.getReports(user.getId(), startDate, endDate, type, accountId, categoryId);
    }

    @GetMapping("/reports/income-vs-expense")
    public Map<String, Object> incomeVsExpense(@AuthenticationPrincipal AppUserDetails user,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) UUID accountId,
                                               @RequestParam(required = false) UUID categoryId) {
        return financeService.getReports(user.getId(), startDate, endDate, type, accountId, categoryId);
    }

    @GetMapping("/reports/account-balance-trend")
    public Map<String, Object> accountBalanceTrend(@AuthenticationPrincipal AppUserDetails user,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) UUID accountId,
                                                   @RequestParam(required = false) UUID categoryId) {
        return financeService.getReports(user.getId(), startDate, endDate, type, accountId, categoryId);
    }

    @GetMapping("/insights/health-score")
    public Map<String, Object> healthScore(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getHealthScore(user.getId());
    }

    @GetMapping("/insights")
    public Map<String, Object> insights(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getInsights(user.getId());
    }

    @GetMapping("/forecast/month")
    public Map<String, Object> forecastMonth(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getForecastMonth(user.getId());
    }

    @GetMapping("/forecast/daily")
    public List<Map<String, Object>> forecastDaily(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getForecastDaily(user.getId());
    }

    @GetMapping("/rules")
    public List<Rule> rules(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getRules(user.getId());
    }

    @PostMapping("/rules")
    public Rule createRule(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody FinanceDtos.RuleRequest request) {
        return financeService.saveRule(user.getId(), null, request);
    }

    @PutMapping("/rules/{id}")
    public Rule updateRule(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.RuleRequest request) {
        return financeService.saveRule(user.getId(), id, request);
    }

    @DeleteMapping("/rules/{id}")
    public void deleteRule(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        financeService.deleteRule(user.getId(), id);
    }

    @PostMapping("/accounts/{id}/invite")
    public Map<String, Object> inviteAccountMember(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id, @Valid @RequestBody FinanceDtos.AccountInviteRequest request) {
        return financeService.inviteAccountMember(user.getId(), id, request);
    }

    @GetMapping("/accounts/{id}/members")
    public List<Map<String, Object>> accountMembers(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        return financeService.getAccountMembers(user.getId(), id);
    }

    @PutMapping("/accounts/{id}/members/{memberUserId}")
    public Map<String, Object> updateAccountMember(@AuthenticationPrincipal AppUserDetails user,
                                                   @PathVariable UUID id,
                                                   @PathVariable UUID memberUserId,
                                                   @Valid @RequestBody FinanceDtos.AccountMemberUpdateRequest request) {
        return financeService.updateAccountMember(user.getId(), id, memberUserId, request);
    }

    @GetMapping("/reports/trends")
    public Map<String, Object> reportTrends(@AuthenticationPrincipal AppUserDetails user,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                            @RequestParam(required = false) UUID accountId,
                                            @RequestParam(required = false) UUID categoryId) {
        return financeService.getReportTrends(user.getId(), startDate, endDate, accountId, categoryId);
    }

    @GetMapping("/reports/net-worth")
    public Map<String, Object> reportNetWorth(@AuthenticationPrincipal AppUserDetails user) {
        return financeService.getNetWorthReport(user.getId());
    }
}
