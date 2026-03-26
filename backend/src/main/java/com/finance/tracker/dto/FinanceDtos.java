package com.finance.tracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class FinanceDtos {
    public record AccountRequest(@NotBlank String name, @NotBlank String type, BigDecimal openingBalance, String institutionName) {}
    public record TransferRequest(@NotNull UUID fromAccountId, @NotNull UUID toAccountId, @NotNull @Positive BigDecimal amount, LocalDate date, String note) {}
    public record CategoryRequest(@NotBlank String name, @NotBlank String type, String color, String icon, Boolean archived) {}
    public record TransactionRequest(@NotBlank String type, @NotNull @Positive BigDecimal amount, @NotNull LocalDate date,
                                     @NotNull UUID accountId, UUID categoryId, String merchant, String note,
                                     String paymentMethod, List<String> tags, UUID destinationAccountId) {}
    public record BudgetRequest(@NotNull UUID categoryId, UUID accountId, int month, int year, @NotNull @Positive BigDecimal amount, Integer alertThresholdPercent) {}
    public record GoalRequest(@NotBlank String name, @NotNull @Positive BigDecimal targetAmount, BigDecimal currentAmount,
                              LocalDate targetDate, UUID linkedAccountId, String icon, String color, String status) {}
    public record GoalAmountRequest(@NotNull @Positive BigDecimal amount, UUID accountId) {}
    public record RecurringRequest(@NotBlank String title, @NotBlank String type, @NotNull @Positive BigDecimal amount,
                                   UUID categoryId, UUID accountId, @NotBlank String frequency, @NotNull LocalDate startDate,
                                   LocalDate endDate, LocalDate nextRunDate, Boolean autoCreateTransaction, Boolean paused) {}
    public record TransactionImportRequest(@NotNull List<TransactionRequest> transactions) {}
    public record RuleRequest(@NotBlank String conditionField, @NotBlank String conditionOperator, @NotBlank String conditionValue,
                              @NotBlank String actionType, @NotBlank String actionValue, Integer priority, Boolean active) {}
    public record AccountInviteRequest(@NotBlank @Email String email, @NotBlank String role) {}
    public record AccountMemberUpdateRequest(@NotBlank String role) {}
}
