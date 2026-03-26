export type User = { id: string; email: string; displayName: string };
export type AccountMember = { userId: string; displayName: string; email: string; role: string };
export type Account = { id: string; name: string; type: string; openingBalance: number; currentBalance: number; institutionName?: string };
export type Category = { id: string; name: string; type: string; color?: string; icon?: string; archived: boolean };
export type Transaction = { id: string; type: string; amount: number; transactionDate: string; merchant?: string; note?: string; paymentMethod?: string; tags?: string; account: Account; category?: Category };
export type Budget = { id: string; month: number; year: number; amount: number; alertThresholdPercent: number; category: Category; account?: Account };
export type Goal = { id: string; name: string; targetAmount: number; currentAmount: number; targetDate?: string; color?: string; icon?: string; status: string; linkedAccount?: Account };
export type RecurringTransaction = { id: string; title: string; type: string; amount: number; frequency: string; startDate: string; endDate?: string; nextRunDate: string; autoCreateTransaction: boolean; paused: boolean; account?: Account; category?: Category };
export type Rule = { id: string; conditionField: string; conditionOperator: string; conditionValue: string; actionType: string; actionValue: string; priority: number; active: boolean };
export type AuthResponse = { accessToken: string; refreshToken: string; user: User };
export type AuditEvent = { id: string; eventType: string; payload: string; createdAt: string };
export type DashboardAlert = { level: string; title: string; body: string };
export type HealthFactor = { label: string; score: number; detail: string };
export type HealthScore = { score: number; status: string; summary: string; factors: HealthFactor[]; suggestions: string[] };
export type InsightHighlight = { tone: string; title: string; body: string };
export type ForecastMonth = {
  currentBalance: number;
  forecastedBalance: number;
  safeToSpend: number;
  negativeBalanceLikely: boolean;
  riskWarning?: string | null;
  upcomingKnownExpenses: Array<{ date: string; title: string; type: string; amount: number }>;
};
export type ForecastDailyPoint = { date: string; projectedBalance: number; projectedIncome: number; projectedExpense: number };
export type Dashboard = {
  monthIncome: number;
  monthExpense: number;
  netBalance: number;
  trackedBalance: number;
  topExpenseCategory: string;
  overBudgetCount: number;
  savingsProgressPercent: number;
  recentTransactions: Transaction[];
  upcomingRecurring: RecurringTransaction[];
  dueTodayRecurring: RecurringTransaction[];
  overdueRecurring: RecurringTransaction[];
  goals: Goal[];
  budgets: Array<{ id: string; category: string; amount: number; spent: number; percent: number; thresholdReached: boolean; alertLevel?: string }>;
  budgetAlerts: DashboardAlert[];
  categorySpend: Array<{ name: string; value: number }>;
  incomeExpenseTrend: Array<{ label: string; income: number; expense: number }>;
  accounts: Account[];
  accountBreakdown: Array<{ id: string; name: string; type: string; value: number }>;
  healthScore?: HealthScore;
  forecastMonth?: ForecastMonth;
};
export type ReportTransaction = { date: string; type: string; merchant: string; account: string; category: string; amount: number; paymentMethod: string; note: string };
export type ReportResponse = {
  summary: { income: number; expense: number; net: number; transactionCount: number; rangeLabel: string };
  filters: { type?: string | null; accountId?: string | null; categoryId?: string | null };
  categorySpend: Array<{ name: string; value: number }>;
  incomeVsExpense: Array<{ label: string; income: number; expense: number }>;
  accountBalanceTrend: Array<{ name: string; value: number }>;
  savingsProgress: Array<{ name: string; currentAmount: number; targetAmount: number }>;
  topCategories: string[];
  transactions: ReportTransaction[];
};
export type InsightsResponse = {
  healthScore: HealthScore;
  highlights: InsightHighlight[];
  incomeVsExpense: Array<{ label: string; income: number; expense: number }>;
  savingsRateTrend: Array<{ label: string; savingsRate: number; net: number }>;
  netWorthTrend: Array<{ label: string; value: number }>;
  categoryTrends: Array<Record<string, string | number>>;
};
export type TrendReportResponse = ReportResponse & {
  savingsRateTrend: Array<{ label: string; savingsRate: number; net: number }>;
  netWorthTrend: Array<{ label: string; value: number }>;
  categoryTrends: Array<Record<string, string | number>>;
};
export type NetWorthReportResponse = {
  netWorthTrend: Array<{ label: string; value: number }>;
  accounts: Array<{ id: string; name: string; value: number }>;
};


