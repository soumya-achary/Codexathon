package com.finance.tracker.service;

import com.finance.tracker.dto.FinanceDtos;
import com.finance.tracker.entity.*;
import com.finance.tracker.exception.ApiException;
import com.finance.tracker.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinanceService {
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final GoalRepository goalRepository;
    private final RecurringTransactionRepository recurringRepository;
    private final RuleRepository ruleRepository;
    private final AccountMemberRepository accountMemberRepository;
    private final AuditService auditService;

    public FinanceService(UserRepository userRepository, AccountRepository accountRepository, CategoryRepository categoryRepository,
                          TransactionRepository transactionRepository, BudgetRepository budgetRepository,
                          GoalRepository goalRepository, RecurringTransactionRepository recurringRepository,
                          RuleRepository ruleRepository, AccountMemberRepository accountMemberRepository,
                          AuditService auditService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.goalRepository = goalRepository;
        this.recurringRepository = recurringRepository;
        this.ruleRepository = ruleRepository;
        this.accountMemberRepository = accountMemberRepository;
        this.auditService = auditService;
    }

    public User getUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public List<Account> getAccounts(UUID userId) {
        return loadAccessibleAccounts(userId);
    }

    @Transactional
    public Account saveAccount(UUID userId, UUID id, FinanceDtos.AccountRequest request) {
        String normalizedName = request.name().trim();
        boolean exists = id == null
                ? accountRepository.existsByUserIdAndNameIgnoreCase(userId, normalizedName)
                : accountRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, normalizedName, id);
        if (exists) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An account with this name already exists");
        }
        Account account = id == null ? new Account() : accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        BigDecimal requestedOpeningBalance = defaultMoney(request.openingBalance());
        if (account.getUser() == null) {
            account.setUser(getUser(userId));
            account.setOpeningBalance(requestedOpeningBalance);
            account.setCurrentBalance(requestedOpeningBalance);
        } else {
            BigDecimal delta = requestedOpeningBalance.subtract(defaultMoney(account.getOpeningBalance()));
            account.setOpeningBalance(requestedOpeningBalance);
            account.setCurrentBalance(defaultMoney(account.getCurrentBalance()).add(delta));
        }
        account.setName(normalizedName);
        account.setType(request.type());
        account.setInstitutionName(blankToNull(request.institutionName()));
        return accountRepository.save(account);
    }

    @Transactional
    public void transfer(UUID userId, FinanceDtos.TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Source and destination account must differ");
        }
        Account from = requireAccountAccess(userId, request.fromAccountId(), "editor");
        Account to = requireAccountAccess(userId, request.toAccountId(), "editor");
        if (from.getCurrentBalance().compareTo(request.amount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient funds for transfer");
        }
        UUID pairId = UUID.randomUUID();
        from.setCurrentBalance(from.getCurrentBalance().subtract(request.amount()));
        to.setCurrentBalance(to.getCurrentBalance().add(request.amount()));
        accountRepository.saveAll(List.of(from, to));
        Transaction debit = buildTransferTx(getUser(userId), from, request.amount(), request.date(), request.note(), pairId, "transfer-out");
        Transaction credit = buildTransferTx(getUser(userId), to, request.amount(), request.date(), request.note(), pairId, "transfer-in");
        transactionRepository.saveAll(List.of(debit, credit));
    }

    private Transaction buildTransferTx(User user, Account account, BigDecimal amount, LocalDate date, String note, UUID pairId, String merchant) {
        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAccount(account);
        tx.setType("transfer");
        tx.setAmount(amount);
        tx.setTransactionDate(date != null ? date : LocalDate.now());
        tx.setNote(blankToNull(note));
        tx.setMerchant(merchant);
        tx.setTransferPairId(pairId);
        return tx;
    }

    @Transactional
    public void deleteAccount(UUID userId, UUID id) {
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        long transactionCount = transactionRepository.countByAccountId(id);
        long recurringCount = recurringRepository.countByUserIdAndAccountId(userId, id);
        long goalCount = goalRepository.countByUserIdAndLinkedAccountId(userId, id);
        if (transactionCount > 0 || recurringCount > 0 || goalCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This account is linked to transactions, recurring items, or goals. Remove those links before deleting the account.");
        }
        accountMemberRepository.findByAccountIdOrderByCreatedAtAsc(id).forEach(accountMemberRepository::delete);
        accountRepository.delete(account);
    }

    public List<Category> getCategories(UUID userId) {
        return categoryRepository.findByUserIdOrUserIsNullOrderByTypeAscNameAsc(userId);
    }

    @Transactional
    public Category saveCategory(UUID userId, UUID id, FinanceDtos.CategoryRequest request) {
        String normalizedName = request.name().trim();
        String normalizedType = request.type().trim().toLowerCase();
        boolean exists = id == null
                ? categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeIgnoreCase(userId, normalizedName, normalizedType)
                : categoryRepository.existsByUserIdAndNameIgnoreCaseAndTypeIgnoreCaseAndIdNot(userId, normalizedName, normalizedType, id);
        if (exists) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You already have a category with this name and type");
        }
        Category category = id == null ? new Category() : categoryRepository.findById(id)
                .filter(item -> item.getUser() != null && item.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
        if (category.getUser() == null) category.setUser(getUser(userId));
        category.setName(normalizedName);
        category.setType(normalizedType);
        category.setColor(blankToNull(request.color()));
        category.setIcon(blankToNull(request.icon()));
        category.setArchived(Boolean.TRUE.equals(request.archived()));
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(UUID userId, UUID id) {
        Category category = categoryRepository.findById(id)
                .filter(item -> item.getUser() != null && item.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
        long transactionCount = transactionRepository.countByUserIdAndCategoryId(userId, id);
        long budgetCount = budgetRepository.countByUserIdAndCategoryId(userId, id);
        long recurringCount = recurringRepository.countByUserIdAndCategoryId(userId, id);
        if (transactionCount > 0 || budgetCount > 0 || recurringCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This category is already used by transactions, budgets, or recurring items. Reassign those items before deleting the category.");
        }
        categoryRepository.delete(category);
    }

    public Page<Transaction> getTransactions(UUID userId, String search, String type, UUID accountId, UUID categoryId,
                                             LocalDate startDate, LocalDate endDate, BigDecimal minAmount, BigDecimal maxAmount,
                                             int page, int size) {
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        if (accessibleAccountIds.isEmpty()) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }
        String normalizedSearch = emptyToNull(search);
        String normalizedType = emptyToNull(type);
        if (normalizedSearch == null && normalizedType == null && accountId == null && categoryId == null && startDate == null && endDate == null && minAmount == null && maxAmount == null) {
            return transactionRepository.findByAccountIdInOrderByTransactionDateDescCreatedAtDesc(accessibleAccountIds, PageRequest.of(page, size));
        }
        return transactionRepository.searchAccessible(accessibleAccountIds, normalizedSearch == null ? "" : normalizedSearch, normalizedType, accountId, categoryId, startDate, endDate, minAmount, maxAmount, PageRequest.of(page, size));
    }

    public Transaction getTransaction(UUID userId, UUID id) {
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        return transactionRepository.findByIdAndAccountIdIn(id, accessibleAccountIds)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    @Transactional
    public Transaction saveTransaction(UUID userId, UUID id, FinanceDtos.TransactionRequest request) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0");
        }
        if (!"transfer".equalsIgnoreCase(request.type()) && request.categoryId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category is required except for transfer");
        }
        if ("transfer".equalsIgnoreCase(request.type()) && request.destinationAccountId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Destination account is required for transfer");
        }
        if (id != null) {
            Transaction existing = getTransaction(userId, id);
            requireAccountAccess(userId, existing.getAccount().getId(), "editor");
            revertBalance(existing);
            transactionRepository.delete(existing);
            transactionRepository.flush();
        }
        if ("transfer".equalsIgnoreCase(request.type())) {
            transfer(userId, new FinanceDtos.TransferRequest(request.accountId(), request.destinationAccountId(), request.amount(), request.date(), request.note()));
            Transaction latest = transactionRepository.findTop5ByAccountIdInOrderByTransactionDateDescCreatedAtDesc(getAccessibleAccountIds(userId)).stream().findFirst().orElseThrow();
            auditService.recordEvent(getUser(userId), id == null ? "transaction_created" : "transaction_updated", Map.of(
                    "transactionId", latest.getId(),
                    "type", latest.getType(),
                    "amount", latest.getAmount()
            ));
            return latest;
        }
        Account account = requireAccountAccess(userId, request.accountId(), "editor");
        Category category = requireCategory(userId, request.categoryId());
        Transaction transaction = new Transaction();
        transaction.setUser(getUser(userId));
        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setType(request.type().trim().toLowerCase());
        transaction.setAmount(request.amount());
        transaction.setTransactionDate(request.date());
        transaction.setMerchant(blankToNull(request.merchant()));
        transaction.setNote(blankToNull(request.note()));
        transaction.setPaymentMethod(blankToNull(request.paymentMethod()));
        transaction.setTags(request.tags() == null ? null : request.tags().stream().map(String::trim).filter(tag -> !tag.isBlank()).collect(Collectors.joining(",")));
        applyRules(userId, transaction);
        applyTransactionBalance(transaction, true);
        accountRepository.save(account);
        Transaction saved = transactionRepository.save(transaction);
        auditService.recordEvent(saved.getUser(), id == null ? "transaction_created" : "transaction_updated", Map.of(
                "transactionId", saved.getId(),
                "type", saved.getType(),
                "amount", saved.getAmount()
        ));
        return saved;
    }

    @Transactional
    public void deleteTransaction(UUID userId, UUID id) {
        Transaction transaction = getTransaction(userId, id);
        requireAccountAccess(userId, transaction.getAccount().getId(), "editor");
        revertBalance(transaction);
        transactionRepository.delete(transaction);
        auditService.recordEvent(transaction.getUser(), "transaction_deleted", Map.of("transactionId", transaction.getId()));
    }

    public List<Budget> getBudgets(UUID userId, int month, int year) {
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        return accessibleAccountIds.isEmpty()
                ? budgetRepository.findByUserIdAndMonthAndYearOrderByCategoryNameAsc(userId, month, year)
                : budgetRepository.findAccessibleBudgets(userId, accessibleAccountIds, month, year);
    }

    @Transactional
    public Budget saveBudget(UUID userId, UUID id, FinanceDtos.BudgetRequest request) {
        boolean exists = id == null
                ? budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYear(userId, request.categoryId(), request.month(), request.year())
                : budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYearAndIdNot(userId, request.categoryId(), request.month(), request.year(), id);
        if (exists) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A budget already exists for this category and month");
        }
        Budget budget = id == null ? new Budget() : requireBudgetAccess(userId, id, "editor");
        budget.setUser(getUser(userId));
        budget.setCategory(requireCategory(userId, request.categoryId()));
        budget.setAccount(request.accountId() == null ? null : requireAccountAccess(userId, request.accountId(), "editor"));
        budget.setMonth(request.month());
        budget.setYear(request.year());
        budget.setAmount(request.amount());
        budget.setAlertThresholdPercent(request.alertThresholdPercent() == null ? 80 : request.alertThresholdPercent());
        Budget saved = budgetRepository.save(budget);
        auditService.recordEvent(saved.getUser(), id == null ? "budget_created" : "budget_updated", Map.of(
                "budgetId", saved.getId(),
                "category", saved.getCategory().getName(),
                "amount", saved.getAmount(),
                "month", saved.getMonth(),
                "year", saved.getYear()
        ));
        return saved;
    }

    @Transactional
    public void deleteBudget(UUID userId, UUID id) {
        Budget budget = requireBudgetAccess(userId, id, "editor");
        budgetRepository.delete(budget);
        auditService.recordEvent(budget.getUser(), "budget_deleted", Map.of("budgetId", budget.getId()));
    }

    @Transactional
    public List<Budget> duplicatePreviousMonthBudgets(UUID userId, int month, int year) {
        YearMonth targetMonth = YearMonth.of(year, month);
        YearMonth sourceMonth = targetMonth.minusMonths(1);
        List<Budget> sourceBudgets = getBudgets(userId, sourceMonth.getMonthValue(), sourceMonth.getYear());
        if (sourceBudgets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No budgets found in the previous month to duplicate");
        }
        List<Budget> created = new ArrayList<>();
        User user = getUser(userId);
        for (Budget source : sourceBudgets) {
            if (budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYear(userId, source.getCategory().getId(), month, year)) {
                continue;
            }
            Budget budget = new Budget();
            budget.setUser(user);
            budget.setCategory(source.getCategory());
            budget.setMonth(month);
            budget.setYear(year);
            budget.setAmount(source.getAmount());
            budget.setAlertThresholdPercent(source.getAlertThresholdPercent());
            created.add(budgetRepository.save(budget));
        }
        auditService.recordEvent(user, "budget_duplicated", Map.of(
                "sourceMonth", sourceMonth.getMonthValue(),
                "sourceYear", sourceMonth.getYear(),
                "month", month,
                "year", year,
                "count", created.size()
        ));
        return created;
    }

    public List<Goal> getGoals(UUID userId) {
        return goalRepository.findAccessibleGoals(userId, getAccessibleAccountIds(userId));
    }

    @Transactional
    public Goal saveGoal(UUID userId, UUID id, FinanceDtos.GoalRequest request) {
        Goal goal = id == null ? new Goal() : requireGoalAccess(userId, id, "editor");
        goal.setUser(getUser(userId));
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setCurrentAmount(defaultMoney(request.currentAmount()));
        goal.setTargetDate(request.targetDate());
        goal.setLinkedAccount(request.linkedAccountId() == null ? null : requireAccountAccess(userId, request.linkedAccountId(), "editor"));
        goal.setIcon(blankToNull(request.icon()));
        goal.setColor(blankToNull(request.color()));
        goal.setStatus(blankToNull(request.status()) == null ? "active" : request.status().trim().toLowerCase());
        Goal saved = goalRepository.save(goal);
        auditService.recordEvent(saved.getUser(), id == null ? "goal_created" : "goal_updated", Map.of(
                "goalId", saved.getId(),
                "name", saved.getName(),
                "targetAmount", saved.getTargetAmount()
        ));
        return saved;
    }

    @Transactional
    public void deleteGoal(UUID userId, UUID id) {
        Goal goal = requireGoalAccess(userId, id, "editor");
        goalRepository.delete(goal);
        auditService.recordEvent(goal.getUser(), "goal_deleted", Map.of("goalId", goal.getId()));
    }

    @Transactional
    public Goal changeGoalAmount(UUID userId, UUID id, FinanceDtos.GoalAmountRequest request, boolean add) {
        Goal goal = requireGoalAccess(userId, id, "editor");
        BigDecimal nextAmount = add ? goal.getCurrentAmount().add(request.amount()) : goal.getCurrentAmount().subtract(request.amount());
        if (nextAmount.compareTo(BigDecimal.ZERO) < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Goal amount cannot be negative");
        UUID effectiveAccountId = request.accountId();
        if (effectiveAccountId == null && goal.getLinkedAccount() != null) {
            effectiveAccountId = goal.getLinkedAccount().getId();
        }
        if (effectiveAccountId != null) {
            Account account = requireAccountAccess(userId, effectiveAccountId, "editor");
            if (add) {
                if (account.getCurrentBalance().compareTo(request.amount()) < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient balance");
                account.setCurrentBalance(account.getCurrentBalance().subtract(request.amount()));
            } else {
                account.setCurrentBalance(account.getCurrentBalance().add(request.amount()));
            }
            accountRepository.save(account);
        }
        goal.setCurrentAmount(nextAmount);
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus("completed");
        } else if (!"active".equalsIgnoreCase(goal.getStatus())) {
            goal.setStatus("active");
        }
        Goal saved = goalRepository.save(goal);
        auditService.recordEvent(saved.getUser(), id == null ? "goal_created" : "goal_updated", Map.of(
                "goalId", saved.getId(),
                "name", saved.getName(),
                "targetAmount", saved.getTargetAmount()
        ));
        return saved;
    }

    public List<RecurringTransaction> getRecurring(UUID userId) {
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        return accessibleAccountIds.isEmpty() ? List.of() : recurringRepository.findByAccountIdInOrderByNextRunDateAsc(accessibleAccountIds);
    }

    @Transactional
    public RecurringTransaction saveRecurring(UUID userId, UUID id, FinanceDtos.RecurringRequest request) {
        RecurringTransaction recurring = id == null ? new RecurringTransaction() : requireRecurringAccess(userId, id, "editor");
        recurring.setUser(getUser(userId));
        recurring.setTitle(request.title().trim());
        recurring.setType(request.type().trim().toLowerCase());
        recurring.setAmount(request.amount());
        recurring.setCategory(request.categoryId() == null ? null : requireCategory(userId, request.categoryId()));
        recurring.setAccount(request.accountId() == null ? null : requireAccountAccess(userId, request.accountId(), "editor"));
        recurring.setFrequency(request.frequency().trim().toLowerCase());
        recurring.setStartDate(request.startDate());
        recurring.setEndDate(request.endDate());
        recurring.setNextRunDate(request.nextRunDate() == null ? request.startDate() : request.nextRunDate());
        recurring.setAutoCreateTransaction(request.autoCreateTransaction() == null || request.autoCreateTransaction());
        recurring.setPaused(Boolean.TRUE.equals(request.paused()));
        RecurringTransaction saved = recurringRepository.save(recurring);
        auditService.recordEvent(saved.getUser(), id == null ? "recurring_created" : "recurring_updated", Map.of(
                "recurringId", saved.getId(),
                "title", saved.getTitle(),
                "nextRunDate", saved.getNextRunDate()
        ));
        return saved;
    }

    @Transactional
    public void deleteRecurring(UUID userId, UUID id) {
        RecurringTransaction recurring = requireRecurringAccess(userId, id, "editor");
        recurringRepository.delete(recurring);
        auditService.recordEvent(recurring.getUser(), "recurring_deleted", Map.of("recurringId", recurring.getId()));
    }

    public Map<String, Object> getDashboard(UUID userId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        List<Transaction> monthTransactions = accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, start, end);
        List<Account> accounts = getAccounts(userId);
        List<Goal> goals = getGoals(userId);
        List<Map<String, Object>> budgets = buildBudgetSnapshot(userId, currentMonth, monthTransactions);
        List<Map<String, Object>> budgetAlerts = budgets.stream()
                .filter(item -> item.get("alertLevel") != null)
                .map(item -> {
                    String level = String.valueOf(item.get("alertLevel"));
                    String category = String.valueOf(item.get("category"));
                    BigDecimal percent = (BigDecimal) item.get("percent");
                    return switch (level) {
                        case "danger" -> alert("danger", category + " budget exceeded", "Spending has crossed 120% of the monthly budget at " + percent.stripTrailingZeros().toPlainString() + "%.");
                        case "warning" -> alert("warning", category + " budget fully used", "Spending has crossed 100% of the monthly budget.");
                        default -> alert("info", category + " budget nearing limit", "Spending has crossed 80% of the monthly budget.");
                    };
                })
                .toList();
        List<RecurringTransaction> dueToday = accessibleAccountIds.isEmpty() ? List.of() : recurringRepository.findTop5ByAccountIdInAndPausedFalseAndNextRunDateOrderByNextRunDateAsc(accessibleAccountIds, LocalDate.now());
        List<RecurringTransaction> overdue = accessibleAccountIds.isEmpty() ? List.of() : recurringRepository.findTop5ByAccountIdInAndPausedFalseAndNextRunDateLessThanOrderByNextRunDateAsc(accessibleAccountIds, LocalDate.now());
        List<RecurringTransaction> upcoming = accessibleAccountIds.isEmpty() ? List.of() : recurringRepository.findTop5ByAccountIdInAndPausedFalseAndNextRunDateGreaterThanEqualOrderByNextRunDateAsc(accessibleAccountIds, LocalDate.now());
        BigDecimal income = sumByType(monthTransactions, "income");
        BigDecimal expense = sumByType(monthTransactions, "expense");
        BigDecimal trackedBalance = accounts.stream()
                .map(account -> defaultMoney(account.getCurrentBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGoalTarget = goals.stream().map(Goal::getTargetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGoalCurrent = goals.stream().map(Goal::getCurrentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String topExpenseCategory = monthTransactions.stream()
                .filter(t -> "expense".equalsIgnoreCase(t.getType()) && t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(), Collectors.mapping(Transaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No expenses yet");

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("monthIncome", income);
        dashboard.put("monthExpense", expense);
        dashboard.put("netBalance", income.subtract(expense));
        dashboard.put("trackedBalance", trackedBalance);
        dashboard.put("topExpenseCategory", topExpenseCategory);
        dashboard.put("overBudgetCount", budgets.stream().filter(item -> ((BigDecimal) item.get("percent")).compareTo(BigDecimal.valueOf(100)) > 0).count());
        dashboard.put("savingsProgressPercent", percentage(totalGoalCurrent, totalGoalTarget));
        dashboard.put("recentTransactions", accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findTop5ByAccountIdInOrderByTransactionDateDescCreatedAtDesc(accessibleAccountIds));
        dashboard.put("upcomingRecurring", upcoming);
        dashboard.put("dueTodayRecurring", dueToday);
        dashboard.put("overdueRecurring", overdue);
        dashboard.put("goals", goals);
        dashboard.put("budgets", budgets);
        dashboard.put("budgetAlerts", budgetAlerts);
        dashboard.put("categorySpend", buildCategorySpend(monthTransactions));
        dashboard.put("incomeExpenseTrend", buildIncomeExpenseTrend(userId, 6));
        dashboard.put("forecastMonth", getForecastMonth(userId));
        dashboard.put("accounts", accounts);
        dashboard.put("accountBreakdown", accounts.stream().map(account -> Map.of(
                "id", account.getId(),
                "name", account.getName(),
                "type", account.getType(),
                "value", account.getCurrentBalance()
        )).toList());
        return dashboard;
    }

    public Map<String, Object> getReports(UUID userId, LocalDate start, LocalDate end, String type, UUID accountId, UUID categoryId) {
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        List<Transaction> transactions = (accessibleAccountIds.isEmpty() ? List.<Transaction>of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, start, end)).stream()
                .filter(t -> type == null || type.isBlank() || type.equalsIgnoreCase(t.getType()))
                .filter(t -> accountId == null || t.getAccount().getId().equals(accountId))
                .filter(t -> categoryId == null || (t.getCategory() != null && t.getCategory().getId().equals(categoryId)))
                .toList();
        Map<String, BigDecimal> category = transactions.stream()
                .filter(t -> "expense".equalsIgnoreCase(t.getType()) && t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(), Collectors.mapping(Transaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        BigDecimal income = sumByType(transactions, "income");
        BigDecimal expense = sumByType(transactions, "expense");
        BigDecimal net = income.subtract(expense);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("type", type);
        filters.put("accountId", accountId);
        filters.put("categoryId", categoryId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", Map.of(
                "income", income,
                "expense", expense,
                "net", net,
                "transactionCount", transactions.size(),
                "rangeLabel", start + " to " + end
        ));
        response.put("filters", filters);
        response.put("categorySpend", category.entrySet().stream().map(e -> Map.of("name", e.getKey(), "value", e.getValue())).toList());
        response.put("incomeVsExpense", buildRangeTrend(transactions));
        response.put("accountBalanceTrend", getAccounts(userId).stream().map(a -> Map.of("name", a.getName(), "value", a.getCurrentBalance())).toList());
        response.put("savingsProgress", getGoals(userId).stream().map(g -> Map.of("name", g.getName(), "currentAmount", g.getCurrentAmount(), "targetAmount", g.getTargetAmount())).toList());
        response.put("topCategories", category.entrySet().stream().sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(5).map(Map.Entry::getKey).toList());
        response.put("transactions", transactions.stream().map(t -> Map.of(
                "date", t.getTransactionDate(),
                "type", t.getType(),
                "merchant", t.getMerchant() == null ? "" : t.getMerchant(),
                "account", t.getAccount().getName(),
                "category", t.getCategory() == null ? "" : t.getCategory().getName(),
                "amount", t.getAmount(),
                "paymentMethod", t.getPaymentMethod() == null ? "" : t.getPaymentMethod(),
                "note", t.getNote() == null ? "" : t.getNote()
        )).toList());
        return response;
    }

    public Map<String, Object> getHealthScore(UUID userId) {
        List<Account> accounts = getAccounts(userId);
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        List<Transaction> currentMonthTransactions = accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, start, end);
        List<Map<String, Object>> budgets = buildBudgetSnapshot(userId, currentMonth, currentMonthTransactions);
        BigDecimal monthIncome = sumByType(currentMonthTransactions, "income");
        BigDecimal monthExpense = sumByType(currentMonthTransactions, "expense");
        BigDecimal trackedBalance = accounts.stream()
                .map(account -> defaultMoney(account.getCurrentBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal savingsRate = monthIncome.compareTo(BigDecimal.ZERO) > 0
                ? monthIncome.subtract(monthExpense).max(BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(100))
                .divide(monthIncome, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        int savingsScore = clampScore(savingsRate);

        List<BigDecimal> monthlyExpenses = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth bucket = currentMonth.minusMonths(i);
            List<Transaction> bucketTransactions = accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, bucket.atDay(1), bucket.atEndOfMonth());
            monthlyExpenses.add(sumByType(bucketTransactions, "expense"));
        }
        int expenseStabilityScore = calculateExpenseStabilityScore(monthlyExpenses);

        int budgetAdherenceScore = budgets.isEmpty()
                ? (monthExpense.compareTo(BigDecimal.ZERO) > 0 ? 70 : 100)
                : budgets.stream()
                .map(item -> {
                    BigDecimal percent = (BigDecimal) item.get("percent");
                    BigDecimal penalty = percent.compareTo(BigDecimal.valueOf(100)) <= 0
                            ? BigDecimal.ZERO
                            : percent.subtract(BigDecimal.valueOf(100));
                    return BigDecimal.valueOf(100).subtract(penalty.min(BigDecimal.valueOf(100)));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(budgets.size()), 0, RoundingMode.HALF_UP)
                .intValue();

        BigDecimal cashBufferMonths = monthExpense.compareTo(BigDecimal.ZERO) > 0
                ? trackedBalance.divide(monthExpense, 2, RoundingMode.HALF_UP)
                : (trackedBalance.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(3) : BigDecimal.ZERO);
        int cashBufferScore = clampScore(cashBufferMonths.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(3), 0, RoundingMode.HALF_UP));

        int score = BigDecimal.valueOf(savingsScore).multiply(BigDecimal.valueOf(0.30))
                .add(BigDecimal.valueOf(expenseStabilityScore).multiply(BigDecimal.valueOf(0.20)))
                .add(BigDecimal.valueOf(budgetAdherenceScore).multiply(BigDecimal.valueOf(0.25)))
                .add(BigDecimal.valueOf(cashBufferScore).multiply(BigDecimal.valueOf(0.25)))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        List<Map<String, Object>> factors = List.of(
                factor("Savings rate", savingsScore, savingsRate.stripTrailingZeros().toPlainString() + "% of income saved this month"),
                factor("Expense stability", expenseStabilityScore, expenseStabilityScore >= 75 ? "Your monthly spending has been fairly consistent" : "Spending is moving around noticeably month to month"),
                factor("Budget adherence", budgetAdherenceScore, budgets.isEmpty() ? "No budgets set yet, so this uses a neutral baseline" : budgets.stream().filter(item -> ((BigDecimal) item.get("percent")).compareTo(BigDecimal.valueOf(100)) <= 0).count() + " of " + budgets.size() + " budgets are still on track"),
                factor("Cash buffer", cashBufferScore, cashBufferMonths.stripTrailingZeros().toPlainString() + " month(s) of expenses covered by current balances")
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("score", score);
        response.put("status", score >= 80 ? "Strong" : score >= 60 ? "Stable" : score >= 40 ? "Needs attention" : "At risk");
        response.put("summary", score >= 80
                ? "Your current cash habits are supporting healthy day-to-day finances."
                : score >= 60
                ? "You have a workable baseline, with a few areas worth tightening."
                : "The numbers point to pressure in your monthly cash flow and planning.");
        response.put("factors", factors);
        response.put("suggestions", buildHealthSuggestions(savingsScore, expenseStabilityScore, budgetAdherenceScore, cashBufferScore, monthIncome, budgets));
        return response;
    }

    public Map<String, Object> getInsights(UUID userId) {
        Map<String, Object> healthScore = getHealthScore(userId);
        List<Map<String, Object>> incomeExpenseTrend = buildIncomeExpenseTrend(userId, 6);
        List<Map<String, Object>> savingsRateTrend = buildSavingsRateTrend(incomeExpenseTrend);
        List<Map<String, Object>> netWorthTrend = buildNetWorthTrend(userId, 6);
        List<Map<String, Object>> categoryTrend = buildCategoryTrendSeries(userId, 6, 4);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("healthScore", healthScore);
        response.put("highlights", buildHighlights(userId, incomeExpenseTrend, savingsRateTrend));
        response.put("incomeVsExpense", incomeExpenseTrend);
        response.put("savingsRateTrend", savingsRateTrend);
        response.put("netWorthTrend", netWorthTrend);
        response.put("categoryTrends", categoryTrend);
        return response;
    }

    public List<Rule> getRules(UUID userId) {
        return ruleRepository.findByUserIdOrderByPriorityAscCreatedAtAsc(userId);
    }

    @Transactional
    public Rule saveRule(UUID userId, UUID id, FinanceDtos.RuleRequest request) {
        Rule rule = id == null ? new Rule() : ruleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rule not found"));
        rule.setUser(getUser(userId));
        rule.setConditionField(request.conditionField().trim().toLowerCase());
        rule.setConditionOperator(request.conditionOperator().trim().toLowerCase());
        rule.setConditionValue(request.conditionValue().trim());
        rule.setActionType(request.actionType().trim().toLowerCase());
        rule.setActionValue(request.actionValue().trim());
        rule.setPriority(request.priority() == null ? 100 : request.priority());
        rule.setActive(request.active() == null || request.active());
        return ruleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(UUID userId, UUID id) {
        Rule rule = ruleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rule not found"));
        ruleRepository.delete(rule);
    }

    @Transactional
    public List<Transaction> importTransactions(UUID userId, FinanceDtos.TransactionImportRequest request) {
        if (request.transactions() == null || request.transactions().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No transactions supplied for import");
        }
        List<Transaction> imported = new ArrayList<>();
        for (FinanceDtos.TransactionRequest item : request.transactions()) {
            imported.add(saveTransaction(userId, null, item));
        }
        return imported;
    }

    @Transactional
    public Map<String, Object> inviteAccountMember(UUID userId, UUID accountId, FinanceDtos.AccountInviteRequest request) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        User invitedUser = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No registered user found for that email"));
        if (invitedUser.getId().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You already own this account");
        }
        if (accountMemberRepository.existsByAccountIdAndUserId(accountId, invitedUser.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That user is already a member of this account");
        }
        AccountMember member = new AccountMember();
        member.setAccount(account);
        member.setUser(invitedUser);
        member.setRole(normalizeRole(request.role()));
        AccountMember saved = accountMemberRepository.save(member);
        auditService.recordEvent(getUser(userId), "account_member_invited", Map.of(
                "accountId", accountId,
                "memberUserId", invitedUser.getId(),
                "role", saved.getRole()
        ));
        return accountMemberResponse(saved);
    }

    public List<Map<String, Object>> getAccountMembers(UUID userId, UUID accountId) {
        requireAccountAccess(userId, accountId, "viewer");
        List<Map<String, Object>> members = new ArrayList<>();
        Account account = accountRepository.findById(accountId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        members.add(Map.of(
                "userId", account.getUser().getId(),
                "displayName", account.getUser().getDisplayName(),
                "email", account.getUser().getEmail(),
                "role", "owner"
        ));
        accountMemberRepository.findByAccountIdOrderByCreatedAtAsc(accountId).forEach(member -> members.add(accountMemberResponse(member)));
        return members;
    }

    @Transactional
    public Map<String, Object> updateAccountMember(UUID userId, UUID accountId, UUID memberUserId, FinanceDtos.AccountMemberUpdateRequest request) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        AccountMember member = accountMemberRepository.findByAccountIdAndUserId(accountId, memberUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account member not found"));
        member.setRole(normalizeRole(request.role()));
        return accountMemberResponse(accountMemberRepository.save(member));
    }

    public Map<String, Object> getForecastMonth(UUID userId) {
        List<Map<String, Object>> daily = getForecastDaily(userId);
        BigDecimal currentBalance = getAccounts(userId).stream()
                .map(account -> defaultMoney(account.getCurrentBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal endBalance = daily.isEmpty() ? currentBalance : (BigDecimal) daily.get(daily.size() - 1).get("projectedBalance");
        List<Map<String, Object>> upcoming = forecastKnownRecurring(userId).stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", item.getNextRunDate());
                    row.put("title", item.getTitle());
                    row.put("type", item.getType());
                    row.put("amount", item.getAmount());
                    return row;
                })
                .toList();
        BigDecimal remainingExpense = daily.stream().map(item -> (BigDecimal) item.get("projectedExpense")).reduce(BigDecimal.ZERO, BigDecimal::add);
        long daysRemaining = Math.max(1, daily.size());
        BigDecimal safeToSpend = endBalance.subtract(BigDecimal.valueOf(0).max(remainingExpense.multiply(BigDecimal.valueOf(0.10))))
                .max(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(daysRemaining), 2, RoundingMode.HALF_UP);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currentBalance", currentBalance);
        response.put("forecastedBalance", endBalance);
        response.put("safeToSpend", safeToSpend);
        response.put("negativeBalanceLikely", endBalance.compareTo(BigDecimal.ZERO) < 0);
        response.put("riskWarning", endBalance.compareTo(BigDecimal.ZERO) < 0 ? "Negative balance likely before month-end" : null);
        response.put("upcomingKnownExpenses", upcoming);
        return response;
    }

    public List<Map<String, Object>> getForecastDaily(UUID userId) {
        List<Account> accounts = getAccounts(userId);
        BigDecimal runningBalance = accounts.stream()
                .map(account -> defaultMoney(account.getCurrentBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (accounts.isEmpty()) {
            return List.of();
        }
        YearMonth currentMonth = YearMonth.now();
        LocalDate today = LocalDate.now();
        Map<LocalDate, BigDecimal> knownIncome = new HashMap<>();
        Map<LocalDate, BigDecimal> knownExpense = new HashMap<>();
        for (RecurringTransaction item : forecastKnownRecurring(userId)) {
            if ("income".equalsIgnoreCase(item.getType())) {
                knownIncome.merge(item.getNextRunDate(), item.getAmount(), BigDecimal::add);
            } else {
                knownExpense.merge(item.getNextRunDate(), item.getAmount(), BigDecimal::add);
            }
        }
        BigDecimal avgDailyIncome = averageDailyAmount(userId, "income", 90);
        BigDecimal avgDailyExpense = averageDailyAmount(userId, "expense", 90);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate date = today; !date.isAfter(currentMonth.atEndOfMonth()); date = date.plusDays(1)) {
            BigDecimal dayIncome = knownIncome.getOrDefault(date, avgDailyIncome);
            BigDecimal dayExpense = knownExpense.getOrDefault(date, avgDailyExpense);
            runningBalance = runningBalance.add(dayIncome).subtract(dayExpense);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            row.put("projectedBalance", runningBalance);
            row.put("projectedIncome", dayIncome);
            row.put("projectedExpense", dayExpense);
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> getReportTrends(UUID userId, LocalDate start, LocalDate end, UUID accountId, UUID categoryId) {
        Map<String, Object> base = getReports(userId, start, end, null, accountId, categoryId);
        List<Map<String, Object>> incomeExpense = (List<Map<String, Object>>) base.get("incomeVsExpense");
        Map<String, Object> response = new LinkedHashMap<>(base);
        response.put("savingsRateTrend", buildSavingsRateTrend(incomeExpense));
        response.put("netWorthTrend", buildNetWorthTrend(userId, 6));
        response.put("categoryTrends", buildCategoryTrendSeries(userId, 6, 4));
        return response;
    }

    public Map<String, Object> getNetWorthReport(UUID userId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("netWorthTrend", buildNetWorthTrend(userId, 6));
        response.put("accounts", getAccounts(userId).stream().map(account -> Map.of(
                "id", account.getId(),
                "name", account.getName(),
                "value", account.getCurrentBalance()
        )).toList());
        return response;
    }

    public List<AuditEvent> getAuditEvents(UUID userId) {
        return auditService.recentEvents(userId);
    }

    public void recordAuditEvent(UUID userId, String eventType, Map<String, Object> payload) {
        auditService.recordEvent(getUser(userId), eventType, payload);
    }

    @Transactional
    public void processRecurringTransactions() {
        for (RecurringTransaction recurring : recurringRepository.findByPausedFalseAndAutoCreateTransactionTrueAndNextRunDateLessThanEqual(LocalDate.now())) {
            if (recurring.getEndDate() != null && recurring.getNextRunDate().isAfter(recurring.getEndDate())) {
                recurring.setPaused(true);
                recurringRepository.save(recurring);
                continue;
            }
            if (recurring.getAccount() != null) {
                Transaction transaction = new Transaction();
                transaction.setUser(recurring.getUser());
                transaction.setAccount(recurring.getAccount());
                transaction.setCategory(recurring.getCategory());
                transaction.setType(recurring.getType().toLowerCase());
                transaction.setAmount(recurring.getAmount());
                transaction.setTransactionDate(recurring.getNextRunDate());
                transaction.setMerchant(recurring.getTitle());
                transaction.setRecurringTransactionId(recurring.getId());
                applyTransactionBalance(transaction, true);
                transactionRepository.save(transaction);
                accountRepository.save(recurring.getAccount());
            }
            recurring.setNextRunDate(nextFutureRunDate(recurring.getNextRunDate(), recurring.getFrequency(), LocalDate.now()));
            recurringRepository.save(recurring);
        }
    }

    private List<Map<String, Object>> buildBudgetSnapshot(UUID userId, YearMonth month, List<Transaction> monthTransactions) {
        return getBudgets(userId, month.getMonthValue(), month.getYear()).stream().map(b -> {
            BigDecimal spent = monthTransactions.stream()
                    .filter(t -> "expense".equalsIgnoreCase(t.getType()) && t.getCategory() != null && t.getCategory().getId().equals(b.getCategory().getId()))
                    .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pct = percentage(spent, b.getAmount());
            String alertLevel = pct.compareTo(BigDecimal.valueOf(120)) >= 0 ? "danger"
                    : pct.compareTo(BigDecimal.valueOf(100)) >= 0 ? "warning"
                    : pct.compareTo(BigDecimal.valueOf(80)) >= 0 ? "info"
                    : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", b.getId());
            item.put("category", b.getCategory().getName());
            item.put("amount", b.getAmount());
            item.put("spent", spent);
            item.put("percent", pct);
            int alertThreshold = b.getAlertThresholdPercent() == null ? 80 : b.getAlertThresholdPercent();
            item.put("thresholdReached", pct.compareTo(BigDecimal.valueOf(alertThreshold)) >= 0);
            item.put("alertLevel", alertLevel);
            return item;
        }).toList();
    }

    private List<Map<String, Object>> buildCategorySpend(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> "expense".equalsIgnoreCase(t.getType()) && t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(), Collectors.mapping(Transaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
                .entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    return item;
                }).toList();
    }

    private List<Map<String, Object>> buildIncomeExpenseTrend(UUID userId, int months) {
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            List<Transaction> bucket = accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, ym.atDay(1), ym.atEndOfMonth());
            result.add(Map.of(
                    "label", ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + ym.getYear(),
                    "income", sumByType(bucket, "income"),
                    "expense", sumByType(bucket, "expense")
            ));
        }
        return result;
    }

    private List<Map<String, Object>> buildSavingsRateTrend(List<Map<String, Object>> incomeExpenseTrend) {
        return incomeExpenseTrend.stream().map(item -> {
            BigDecimal income = (BigDecimal) item.get("income");
            BigDecimal expense = (BigDecimal) item.get("expense");
            BigDecimal rate = income.compareTo(BigDecimal.ZERO) > 0
                    ? income.subtract(expense).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> trendItem = new LinkedHashMap<>();
            trendItem.put("label", item.get("label"));
            trendItem.put("savingsRate", rate);
            trendItem.put("net", income.subtract(expense));
            return trendItem;
        }).toList();
    }

    private List<Map<String, Object>> buildNetWorthTrend(UUID userId, int months) {
        List<Account> accounts = getAccounts(userId);
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        BigDecimal openingBalances = accounts.stream()
                .map(account -> defaultMoney(account.getOpeningBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            List<Transaction> transactions = accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, LocalDate.of(2000, 1, 1), ym.atEndOfMonth());
            BigDecimal netMovement = transactions.stream()
                    .filter(t -> !"transfer".equalsIgnoreCase(t.getType()))
                    .map(t -> "expense".equalsIgnoreCase(t.getType()) ? t.getAmount().negate() : t.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + ym.getYear());
            point.put("value", openingBalances.add(netMovement));
            result.add(point);
        }
        return result;
    }

    private List<Map<String, Object>> buildCategoryTrendSeries(UUID userId, int months, int topCategoryCount) {
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        Map<YearMonth, List<Transaction>> monthly = new TreeMap<>();
        Map<String, BigDecimal> totals = new HashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            List<Transaction> bucket = accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, ym.atDay(1), ym.atEndOfMonth());
            monthly.put(ym, bucket);
            bucket.stream()
                    .filter(t -> "expense".equalsIgnoreCase(t.getType()) && t.getCategory() != null)
                    .forEach(t -> totals.merge(t.getCategory().getName(), t.getAmount(), BigDecimal::add));
        }

        List<String> topCategories = totals.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(topCategoryCount)
                .map(Map.Entry::getKey)
                .toList();

        return monthly.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + entry.getKey().getYear());
            Map<String, BigDecimal> byCategory = entry.getValue().stream()
                    .filter(t -> "expense".equalsIgnoreCase(t.getType()) && t.getCategory() != null)
                    .collect(Collectors.groupingBy(t -> t.getCategory().getName(), Collectors.mapping(Transaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
            for (String category : topCategories) {
                row.put(category, byCategory.getOrDefault(category, BigDecimal.ZERO));
            }
            return row;
        }).toList();
    }

    private List<Map<String, Object>> buildHighlights(UUID userId, List<Map<String, Object>> incomeExpenseTrend, List<Map<String, Object>> savingsRateTrend) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        List<Transaction> currentTransactions = accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, currentMonth.atDay(1), currentMonth.atEndOfMonth());
        List<Transaction> previousTransactions = accessibleAccountIds.isEmpty() ? List.of() : transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, previousMonth.atDay(1), previousMonth.atEndOfMonth());
        if (incomeExpenseTrend.isEmpty() || savingsRateTrend.isEmpty()) {
            return List.of(
                    highlight("info", "Not enough history yet", "Add a bit more transaction history to unlock month-over-month insight highlights.")
            );
        }

        BigDecimal currentFood = currentTransactions.stream()
                .filter(t -> "expense".equalsIgnoreCase(t.getType()) && t.getCategory() != null && "food".equalsIgnoreCase(t.getCategory().getName()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousFood = previousTransactions.stream()
                .filter(t -> "expense".equalsIgnoreCase(t.getType()) && t.getCategory() != null && "food".equalsIgnoreCase(t.getCategory().getName()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentNet = (BigDecimal) incomeExpenseTrend.get(incomeExpenseTrend.size() - 1).get("income");
        currentNet = currentNet.subtract((BigDecimal) incomeExpenseTrend.get(incomeExpenseTrend.size() - 1).get("expense"));
        BigDecimal previousNet = incomeExpenseTrend.size() > 1
                ? ((BigDecimal) incomeExpenseTrend.get(incomeExpenseTrend.size() - 2).get("income"))
                .subtract((BigDecimal) incomeExpenseTrend.get(incomeExpenseTrend.size() - 2).get("expense"))
                : BigDecimal.ZERO;

        BigDecimal currentSavingsRate = (BigDecimal) savingsRateTrend.get(savingsRateTrend.size() - 1).get("savingsRate");
        BigDecimal previousSavingsRate = savingsRateTrend.size() > 1
                ? (BigDecimal) savingsRateTrend.get(savingsRateTrend.size() - 2).get("savingsRate")
                : BigDecimal.ZERO;

        List<Map<String, Object>> highlights = new ArrayList<>();
        if (previousFood.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal delta = currentFood.subtract(previousFood)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(previousFood, 0, RoundingMode.HALF_UP);
            highlights.add(highlight(
                    delta.compareTo(BigDecimal.ZERO) > 0 ? "warning" : "success",
                    "Food spending " + (delta.compareTo(BigDecimal.ZERO) > 0 ? "increased" : "decreased"),
                    "Food spend moved " + delta.abs().stripTrailingZeros().toPlainString() + "% compared with last month."
            ));
        }
        highlights.add(highlight(
                currentNet.compareTo(previousNet) >= 0 ? "success" : "warning",
                currentNet.compareTo(previousNet) >= 0 ? "You saved more than last month" : "Savings slipped versus last month",
                "Net cash flow changed from " + previousNet.stripTrailingZeros().toPlainString() + " to " + currentNet.stripTrailingZeros().toPlainString() + "."
        ));
        highlights.add(highlight(
                currentSavingsRate.compareTo(previousSavingsRate) >= 0 ? "success" : "info",
                "Savings rate is now " + currentSavingsRate.stripTrailingZeros().toPlainString() + "%",
                "That compares with " + previousSavingsRate.stripTrailingZeros().toPlainString() + "% in the previous month."
        ));
        return highlights;
    }

    private Map<String, Object> factor(String label, int score, String detail) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("label", label);
        factor.put("score", score);
        factor.put("detail", detail);
        return factor;
    }

    private Map<String, Object> highlight(String tone, String title, String body) {
        Map<String, Object> highlight = new LinkedHashMap<>();
        highlight.put("tone", tone);
        highlight.put("title", title);
        highlight.put("body", body);
        return highlight;
    }

    private List<String> buildHealthSuggestions(int savingsScore, int expenseStabilityScore, int budgetAdherenceScore, int cashBufferScore, BigDecimal monthIncome, List<Map<String, Object>> budgets) {
        List<String> suggestions = new ArrayList<>();
        if (monthIncome.compareTo(BigDecimal.ZERO) <= 0) {
            suggestions.add("Record at least one income source this month so the app can measure your savings rate more accurately.");
        }
        if (savingsScore < 60) {
            suggestions.add("Try moving a fixed amount into savings right after income lands to improve your savings rate.");
        }
        if (expenseStabilityScore < 60) {
            suggestions.add("Review the last few months for spikes in variable categories and set caps for the noisiest ones.");
        }
        if (budgetAdherenceScore < 60 || budgets.isEmpty()) {
            suggestions.add("Add or tighten category budgets so overspending gets caught earlier in the month.");
        }
        if (cashBufferScore < 60) {
            suggestions.add("Build a larger cash buffer to cover at least three months of typical expenses.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Keep your current cadence: your savings, budget use, and cash buffer are all in a healthy range.");
        }
        return suggestions;
    }

    private int calculateExpenseStabilityScore(List<BigDecimal> monthlyExpenses) {
        List<BigDecimal> nonZero = monthlyExpenses.stream().filter(value -> value.compareTo(BigDecimal.ZERO) > 0).toList();
        if (nonZero.size() < 2) {
            return 70;
        }
        BigDecimal average = nonZero.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(nonZero.size()), 4, RoundingMode.HALF_UP);
        if (average.compareTo(BigDecimal.ZERO) == 0) {
            return 100;
        }
        BigDecimal variance = nonZero.stream()
                .map(value -> value.subtract(average))
                .map(diff -> diff.multiply(diff))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(nonZero.size()), 4, RoundingMode.HALF_UP);
        double coefficientOfVariation = Math.sqrt(variance.doubleValue()) / average.doubleValue();
        int score = (int) Math.round(100 - Math.min(coefficientOfVariation * 100, 100));
        return Math.max(score, 0);
    }

    private int clampScore(BigDecimal value) {
        return Math.max(0, Math.min(100, value.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    private Map<String, Object> alert(String level, String title, String body) {
        return Map.of("level", level, "title", title, "body", body);
    }

    private List<Map<String, Object>> buildRangeTrend(List<Transaction> transactions) {
        return transactions.stream().collect(Collectors.groupingBy(t -> YearMonth.from(t.getTransactionDate()), TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", e.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + e.getKey().getYear());
                    item.put("income", sumByType(e.getValue(), "income"));
                    item.put("expense", sumByType(e.getValue(), "expense"));
                    return item;
                }).toList();
    }

    private BigDecimal sumByType(List<Transaction> transactions, String type) {
        return transactions.stream().filter(t -> type.equalsIgnoreCase(t.getType())).map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percentage(BigDecimal current, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return current.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private void applyTransactionBalance(Transaction tx, boolean apply) {
        BigDecimal delta = apply ? tx.getAmount() : tx.getAmount().negate();
        if ("expense".equalsIgnoreCase(tx.getType())) delta = delta.negate();
        tx.getAccount().setCurrentBalance(tx.getAccount().getCurrentBalance().add(delta));
    }

    private void revertBalance(Transaction transaction) {
        Account account = transaction.getAccount();
        transaction.setAccount(account);
        applyTransactionBalance(transaction, false);
        accountRepository.save(account);
    }

    private List<Account> loadAccessibleAccounts(UUID userId) {
        List<Account> owned = accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<AccountMember> memberships = accountMemberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return owned;
        }
        LinkedHashMap<UUID, Account> accounts = new LinkedHashMap<>();
        owned.forEach(account -> accounts.put(account.getId(), account));
        List<UUID> sharedIds = memberships.stream().map(member -> member.getAccount().getId()).toList();
        accountRepository.findByIdInOrderByCreatedAtDesc(sharedIds).forEach(account -> accounts.put(account.getId(), account));
        return new ArrayList<>(accounts.values());
    }

    private List<UUID> getAccessibleAccountIds(UUID userId) {
        return loadAccessibleAccounts(userId).stream().map(Account::getId).toList();
    }

    private String getRoleForAccount(UUID userId, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getUser().getId().equals(userId)) {
            return "owner";
        }
        return accountMemberRepository.findByAccountIdAndUserId(accountId, userId)
                .map(AccountMember::getRole)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this account"));
    }

    private boolean roleAtLeast(String actual, String minimum) {
        Map<String, Integer> ranks = Map.of("viewer", 1, "editor", 2, "owner", 3);
        return ranks.getOrDefault(actual, 0) >= ranks.getOrDefault(minimum, 0);
    }

    private Account requireAccountAccess(UUID userId, UUID accountId, String minimumRole) {
        String role = getRoleForAccount(userId, accountId);
        if (!roleAtLeast(role, minimumRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission for that action");
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private Budget requireBudgetAccess(UUID userId, UUID budgetId, String minimumRole) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Budget not found"));
        if (budget.getUser() != null && budget.getUser().getId().equals(userId)) {
            return budget;
        }
        if (budget.getAccount() != null) {
            requireAccountAccess(userId, budget.getAccount().getId(), minimumRole);
            return budget;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this budget");
    }

    private Goal requireGoalAccess(UUID userId, UUID goalId, String minimumRole) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Goal not found"));
        if (goal.getUser() != null && goal.getUser().getId().equals(userId)) {
            return goal;
        }
        if (goal.getLinkedAccount() != null) {
            requireAccountAccess(userId, goal.getLinkedAccount().getId(), minimumRole);
            return goal;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this goal");
    }

    private RecurringTransaction requireRecurringAccess(UUID userId, UUID recurringId, String minimumRole) {
        RecurringTransaction recurring = recurringRepository.findById(recurringId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring item not found"));
        if (recurring.getUser() != null && recurring.getUser().getId().equals(userId)) {
            return recurring;
        }
        if (recurring.getAccount() != null) {
            requireAccountAccess(userId, recurring.getAccount().getId(), minimumRole);
            return recurring;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this recurring item");
    }

    private void applyRules(UUID userId, Transaction transaction) {
        for (Rule rule : ruleRepository.findByUserIdOrderByPriorityAscCreatedAtAsc(userId)) {
            if (!rule.isActive() || !ruleMatches(rule, transaction)) {
                continue;
            }
            switch (rule.getActionType()) {
                case "set_category" -> transaction.setCategory(findCategoryByName(userId, rule.getActionValue()));
                case "add_tag" -> transaction.setTags(mergeTag(transaction.getTags(), rule.getActionValue()));
                case "trigger_alert" -> auditService.recordEvent(getUser(userId), "rule_alert_triggered", Map.of(
                        "merchant", transaction.getMerchant(),
                        "amount", transaction.getAmount(),
                        "message", rule.getActionValue()
                ));
                default -> {
                }
            }
        }
    }

    private boolean ruleMatches(Rule rule, Transaction transaction) {
        String fieldValue = switch (rule.getConditionField()) {
            case "merchant" -> blankToNull(transaction.getMerchant());
            case "category" -> transaction.getCategory() == null ? null : transaction.getCategory().getName();
            case "type" -> transaction.getType();
            case "amount" -> transaction.getAmount() == null ? null : transaction.getAmount().stripTrailingZeros().toPlainString();
            default -> null;
        };
        if (fieldValue == null) {
            return false;
        }
        return switch (rule.getConditionOperator()) {
            case "equals" -> fieldValue.equalsIgnoreCase(rule.getConditionValue());
            case "contains" -> fieldValue.toLowerCase().contains(rule.getConditionValue().toLowerCase());
            case "greater_than" -> new BigDecimal(fieldValue).compareTo(new BigDecimal(rule.getConditionValue())) > 0;
            case "less_than" -> new BigDecimal(fieldValue).compareTo(new BigDecimal(rule.getConditionValue())) < 0;
            default -> false;
        };
    }

    private Category findCategoryByName(UUID userId, String categoryName) {
        return getCategories(userId).stream()
                .filter(category -> category.getName().equalsIgnoreCase(categoryName))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Rule category action could not find category " + categoryName));
    }

    private String mergeTag(String existingTags, String nextTag) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (existingTags != null && !existingTags.isBlank()) {
            tags.addAll(Arrays.stream(existingTags.split(",")).map(String::trim).filter(tag -> !tag.isBlank()).toList());
        }
        if (nextTag != null && !nextTag.isBlank()) {
            tags.add(nextTag.trim());
        }
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    private List<RecurringTransaction> forecastKnownRecurring(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate end = YearMonth.now().atEndOfMonth();
        return getRecurring(userId).stream()
                .filter(item -> !item.isPaused())
                .filter(item -> item.getNextRunDate() != null)
                .filter(item -> !item.getNextRunDate().isBefore(today) && !item.getNextRunDate().isAfter(end))
                .toList();
    }

    private BigDecimal averageDailyAmount(UUID userId, String type, int lookbackDays) {
        List<UUID> accessibleAccountIds = getAccessibleAccountIds(userId);
        if (accessibleAccountIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusDays(lookbackDays - 1L);
        List<Transaction> transactions = transactionRepository.findByAccountIdInAndTransactionDateBetween(accessibleAccountIds, start, end).stream()
                .filter(item -> type.equalsIgnoreCase(item.getType()))
                .toList();
        BigDecimal total = transactions.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(lookbackDays), 2, RoundingMode.HALF_UP);
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if (!Set.of("viewer", "editor").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Role must be viewer or editor");
        }
        return normalized;
    }

    private Map<String, Object> accountMemberResponse(AccountMember member) {
        return Map.of(
                "userId", member.getUser().getId(),
                "displayName", member.getUser().getDisplayName(),
                "email", member.getUser().getEmail(),
                "role", member.getRole()
        );
    }

    private Account requireAccount(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private Category requireCategory(UUID userId, UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(category -> category.getUser() == null || category.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDate nextFutureRunDate(LocalDate current, String frequency, LocalDate today) {
        LocalDate next = current;
        do {
            next = switch (frequency.toLowerCase()) {
                case "daily" -> next.plusDays(1);
                case "weekly" -> next.plusWeeks(1);
                case "yearly" -> next.plusYears(1);
                default -> next.plusMonths(1);
            };
        } while (next.isBefore(today));
        return next;
    }
}
















