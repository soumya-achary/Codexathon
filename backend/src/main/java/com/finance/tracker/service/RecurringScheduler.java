package com.finance.tracker.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringScheduler {
    private final FinanceService financeService;

    public RecurringScheduler(FinanceService financeService) {
        this.financeService = financeService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runRecurringTransactions() {
        financeService.processRecurringTransactions();
    }
}
