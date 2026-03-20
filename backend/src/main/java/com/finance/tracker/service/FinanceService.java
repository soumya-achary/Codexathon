package com.finance.tracker.service;

import com.finance.tracker.dto.FinanceDtos;
import com.finance.tracker.entity.*;
import com.finance.tracker.exception.ApiException;
import com.finance.tracker.repository.*;
import org.springframework.data.domain.Page;
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
    private final AuditService auditService;

    public FinanceService(UserRepository userRepository, AccountRepository accountRepository, CategoryRepository categoryRepository,
                          TransactionRepository transactionRepository, BudgetRepository budgetRepository,
                          GoalRepository goalRepository, RecurringTransactionRepository recurringRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.goalRepository = goalRepository;
        this.recurringRepository = recurringRepository;
        this.auditService = auditService;
    }

    public User getUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public List<Account> getAccounts(UUID userId) {
        return accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
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
        Account from = requireAccount(userId, request.fromAccountId());
        Account to = requireAccount(userId, request.toAccountId());
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
        long transactionCount = transactionRepository.countByUserIdAndAccountId(userId, id);
        long recurringCount = recurringRepository.countByUserIdAndAccountId(userId, id);
        long goalCount = goalRepository.countByUserIdAndLinkedAccountId(userId, id);
        if (transactionCount > 0 || recurringCount > 0 || goalCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This account is linked to transactions, recurring items, or goals. Remove those links before deleting the account.");
        }
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
        String normalizedSearch = emptyToNull(search);
        String normalizedType = emptyToNull(type);
        if (normalizedSearch == null && normalizedType == null && accountId == null && categoryId == null && startDate == null && endDate == null && minAmount == null && maxAmount == null) {
            return transactionRepository.findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId, PageRequest.of(page, size));
        }
        return transactionRepository.search(userId, normalizedSearch == null ? "" : normalizedSearch, normalizedType, accountId, categoryId, startDate, endDate, minAmount, maxAmount, PageRequest.of(page, size));
    }

    public Transaction getTransaction(UUID userId, UUID id) {
        return transactionRepository.findByIdAndUserId(id, userId)
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
            revertBalance(existing);
            transactionRepository.delete(existing);
            transactionRepository.flush();
        }
        if ("transfer".equalsIgnoreCase(request.type())) {
            transfer(userId, new FinanceDtos.TransferRequest(request.accountId(), request.destinationAccountId(), request.amount(), request.date(), request.note()));
            Transaction latest = transactionRepository.findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(userId).stream().findFirst().orElseThrow();
            auditService.recordEvent(getUser(userId), id == null ? "transaction_created" : "transaction_updated", Map.of(
                    "transactionId", latest.getId(),
                    "type", latest.getType(),
                    "amount", latest.getAmount()
            ));
            return latest;
        }
        Account account = requireAccount(userId, request.accountId());
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
        revertBalance(transaction);
        transactionRepository.delete(transaction);
        auditService.recordEvent(transaction.getUser(), "transaction_deleted", Map.of("transactionId", transaction.getId()));
    }

    public List<Budget> getBudgets(UUID userId, int month, int year) {
        return budgetRepository.findByUserIdAndMonthAndYearOrderByCategoryNameAsc(userId, month, year);
    }

    @Transactional
    public Budget saveBudget(UUID userId, UUID id, FinanceDtos.BudgetRequest request) {
        boolean exists = id == null
                ? budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYear(userId, request.categoryId(), request.month(), request.year())
                : budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYearAndIdNot(userId, request.categoryId(), request.month(), request.year(), id);
        if (exists) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A budget already exists for this category and month");
        }
        Budget budget = id == null ? new Budget() : budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Budget not found"));
        budget.setUser(getUser(userId));
        budget.setCategory(requireCategory(userId, request.categoryId()));
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
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Budget not found"));
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
        return goalRepository.findByUserIdOrderByTargetDateAsc(userId);
    }

    @Transactional
    public Goal saveGoal(UUID userId, UUID id, FinanceDtos.GoalRequest request) {
        Goal goal = id == null ? new Goal() : goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Goal not found"));
        goal.setUser(getUser(userId));
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setCurrentAmount(defaultMoney(request.currentAmount()));
        goal.setTargetDate(request.targetDate());
        goal.setLinkedAccount(request.linkedAccountId() == null ? null : requireAccount(userId, request.linkedAccountId()));
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
        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Goal not found"));
        goalRepository.delete(goal);
        auditService.recordEvent(goal.getUser(), "goal_deleted", Map.of("goalId", goal.getId()));
    }

    @Transactional
    public Goal changeGoalAmount(UUID userId, UUID id, FinanceDtos.GoalAmountRequest request, boolean add) {
        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Goal not found"));
        BigDecimal nextAmount = add ? goal.getCurrentAmount().add(request.amount()) : goal.getCurrentAmount().subtract(request.amount());
        if (nextAmount.compareTo(BigDecimal.ZERO) < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Goal amount cannot be negative");
        if (request.accountId() != null) {
            Account account = requireAccount(userId, request.accountId());
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
        return recurringRepository.findByUserIdOrderByNextRunDateAsc(userId);
    }

    @Transactional
    public RecurringTransaction saveRecurring(UUID userId, UUID id, FinanceDtos.RecurringRequest request) {
        RecurringTransaction recurring = id == null ? new RecurringTransaction() : recurringRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring item not found"));
        recurring.setUser(getUser(userId));
        recurring.setTitle(request.title().trim());
        recurring.setType(request.type().trim().toLowerCase());
        recurring.setAmount(request.amount());
        recurring.setCategory(request.categoryId() == null ? null : requireCategory(userId, request.categoryId()));
        recurring.setAccount(request.accountId() == null ? null : requireAccount(userId, request.accountId()));
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
        RecurringTransaction recurring = recurringRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring item not found"));
        recurringRepository.delete(recurring);
        auditService.recordEvent(recurring.getUser(), "recurring_deleted", Map.of("recurringId", recurring.getId()));
    }

    public Map<String, Object> getDashboard(UUID userId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        List<Transaction> monthTransactions = transactionRepository.findByUserIdAndTransactionDateBetween(userId, start, end);
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
        List<RecurringTransaction> dueToday = recurringRepository.findTop5ByUserIdAndPausedFalseAndNextRunDateOrderByNextRunDateAsc(userId, LocalDate.now());
        List<RecurringTransaction> overdue = recurringRepository.findTop5ByUserIdAndPausedFalseAndNextRunDateLessThanOrderByNextRunDateAsc(userId, LocalDate.now());
        List<RecurringTransaction> upcoming = recurringRepository.findTop5ByUserIdAndPausedFalseAndNextRunDateGreaterThanEqualOrderByNextRunDateAsc(userId, LocalDate.now());
        BigDecimal income = sumByType(monthTransactions, "income");
        BigDecimal expense = sumByType(monthTransactions, "expense");
        BigDecimal trackedBalance = accounts.stream().map(Account::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
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
        dashboard.put("recentTransactions", transactionRepository.findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(userId));
        dashboard.put("upcomingRecurring", upcoming);
        dashboard.put("dueTodayRecurring", dueToday);
        dashboard.put("overdueRecurring", overdue);
        dashboard.put("goals", goals);
        dashboard.put("budgets", budgets);
        dashboard.put("budgetAlerts", budgetAlerts);
        dashboard.put("categorySpend", buildCategorySpend(monthTransactions));
        dashboard.put("incomeExpenseTrend", buildIncomeExpenseTrend(userId, 6));
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
        List<Transaction> transactions = transactionRepository.findByUserIdAndTransactionDateBetween(userId, start, end).stream()
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
            item.put("thresholdReached", pct.compareTo(BigDecimal.valueOf(b.getAlertThresholdPercent())) >= 0);
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
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            List<Transaction> bucket = transactionRepository.findByUserIdAndTransactionDateBetween(userId, ym.atDay(1), ym.atEndOfMonth());
            result.add(Map.of(
                    "label", ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + ym.getYear(),
                    "income", sumByType(bucket, "income"),
                    "expense", sumByType(bucket, "expense")
            ));
        }
        return result;
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












