
import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, Route, Routes, useNavigate, useSearchParams } from "react-router-dom";
import { PieChart, Pie, Cell, ResponsiveContainer, LineChart, Line, CartesianGrid, XAxis, YAxis, Tooltip, BarChart, Bar } from "recharts";
import { AxiosError } from "axios";
import { api } from "./api/client";
import { Shell } from "./components/Shell";
import { Card, EmptyState, PageHeader } from "./components/Ui";
import { useAuthStore } from "./store/auth";
import type {
  Account,
  AuthResponse,
  Budget,
  Category,
  Dashboard,
  ForgotPasswordResponse,
  Goal,
  RecurringTransaction,
  ReportResponse,
  Transaction,
} from "./types";
import { currency, dateLabel, percent } from "./utils/format";
import { useThemeMode } from "./hooks/useThemeMode";

const CHART_COLORS = ["#4f8cff", "#34d399", "#f97316", "#f43f5e", "#a855f7", "#0ea5e9"];

type ToastMessage = { type: "success" | "error"; text: string } | null;
type AuthMode = "login" | "register" | "forgot" | "reset";
type TransactionForm = { type: string; amount: string; date: string; accountId: string; categoryId: string; destinationAccountId: string; merchant: string; note: string; paymentMethod: string; tags: string };
type BudgetForm = { categoryId: string; month: string; year: string; amount: string; alertThresholdPercent: string };
type GoalForm = { name: string; targetAmount: string; currentAmount: string; targetDate: string; linkedAccountId: string; color: string; icon: string; status: string };
type RecurringForm = { title: string; type: string; amount: string; categoryId: string; accountId: string; frequency: string; startDate: string; endDate: string; nextRunDate: string; autoCreateTransaction: boolean; paused: boolean };
type AccountForm = { name: string; type: string; openingBalance: string; institutionName: string };

function useBootstrap() {
  const hydrate = useAuthStore((state) => state.hydrate);
  useEffect(() => {
    hydrate();
  }, [hydrate]);
}

function Protected() {
  const { user, hydrated } = useAuthStore();
  if (!hydrated) return null;
  return user ? <Shell /> : <Navigate to="/login" replace />;
}

function getApiErrorMessage(error: unknown) {
  if (error instanceof AxiosError) {
    const data = error.response?.data as { message?: string; errors?: Record<string, string> } | undefined;
    if (data?.errors) return Object.values(data.errors)[0];
    if (data?.message) return data.message;
  }
  return error instanceof Error ? error.message : "Something went wrong.";
}

function formatAccountType(type: string) {
  const labels: Record<string, string> = {
    bank: "Bank account",
    cash: "Cash wallet",
    savings: "Savings account",
    "credit-card": "Credit card",
  };
  return labels[type] ?? type;
}

function FeedbackToast({ message, onClose }: { message: ToastMessage; onClose: () => void }) {
  if (!message) return null;
  return (
    <div className={`toast ${message.type}`}>
      <span>{message.text}</span>
      <button type="button" className="toast-close" onClick={onClose}>x</button>
    </div>
  );
}

function ConfirmDialog({ open, title, body, confirmLabel = "Delete", onCancel, onConfirm }: { open: boolean; title: string; body: string; confirmLabel?: string; onCancel: () => void; onConfirm: () => void }) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" role="presentation" onClick={onCancel}>
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="confirm-title" onClick={(e) => e.stopPropagation()}>
        <h3 id="confirm-title">{title}</h3>
        <p className="muted">{body}</p>
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
          <button type="button" className="action-chip action-delete modal-delete" onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}

function ThemeToggleButton() {
  const { theme, toggleTheme } = useThemeMode();
  return (
    <button type="button" className="theme-switch icon-only auth-theme-switch" onClick={toggleTheme} aria-label={theme === "light" ? "Switch to dark mode" : "Switch to light mode"} title={theme === "light" ? "Switch to dark mode" : "Switch to light mode"}>
      {theme === "light" ? (
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z" /></svg>
      ) : (
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2.5M12 19.5V22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M2 12h2.5M19.5 12H22M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8" /></svg>
      )}
      <span className="sr-only">{theme === "light" ? "Switch to dark mode" : "Switch to light mode"}</span>
    </button>
  );
}

function PasswordField({ value, onChange, placeholder, autoComplete = "new-password" }: { value: string; onChange: (value: string) => void; placeholder: string; autoComplete?: string }) {
  const [showPassword, setShowPassword] = useState(false);
  return (
    <div className="password-wrap">
      <input autoComplete={autoComplete} type={showPassword ? "text" : "password"} placeholder={placeholder} value={value} onChange={(e) => onChange(e.target.value)} />
      <button type="button" className="password-toggle" onClick={() => setShowPassword((current) => !current)}>
        <span className="sr-only">{showPassword ? "Hide password" : "Show password"}</span>
        {showPassword ? (
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18" /><path d="M10.6 10.7a3 3 0 004.2 4.2" /><path d="M9.4 5.5A10.7 10.7 0 0112 5c5 0 8.3 4.4 9.2 5.8a1 1 0 010 1.1 16.8 16.8 0 01-4 4.3" /><path d="M6.5 6.7A16.1 16.1 0 002.8 12a1 1 0 000 1.1C3.7 14.4 7 18.8 12 18.8c1.5 0 2.9-.3 4.1-.8" /></svg>
        ) : (
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.8 12a1 1 0 010-1.1C3.7 9.6 7 5.2 12 5.2s8.3 4.4 9.2 5.7a1 1 0 010 1.1c-.9 1.4-4.2 5.8-9.2 5.8S3.7 13.4 2.8 12z" /><circle cx="12" cy="12" r="3" /></svg>
        )}
      </button>
    </div>
  );
}

function LoginPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const setAuth = useAuthStore((state) => state.setAuth);
  const user = useAuthStore((state) => state.user);
  const [mode, setMode] = useState<AuthMode>((searchParams.get("mode") as AuthMode) || "login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [resetToken, setResetToken] = useState(searchParams.get("token") ?? "");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [resetPreview, setResetPreview] = useState<string | null>(null);

  useEffect(() => {
    setMode((searchParams.get("mode") as AuthMode) || "login");
    setResetToken(searchParams.get("token") ?? "");
  }, [searchParams]);

  useEffect(() => {
    if (user) navigate("/", { replace: true });
  }, [user, navigate]);

  const switchMode = (nextMode: AuthMode) => {
    setSuccessMessage("");
    setResetPreview(null);
    setPassword("");
    setConfirmPassword("");
    setMode(nextMode);
    const next = new URLSearchParams();
    if (nextMode !== "login") next.set("mode", nextMode);
    if (nextMode === "reset" && resetToken) next.set("token", resetToken);
    setSearchParams(next, { replace: true });
  };

  const mutation = useMutation({
    mutationFn: async () => {
      if (mode === "login") return (await api.post<AuthResponse>("/auth/login", { email, password })).data;
      if (mode === "register") return (await api.post<AuthResponse>("/auth/register", { email, password, displayName })).data;
      if (mode === "forgot") return (await api.post<ForgotPasswordResponse>("/auth/forgot-password", { email })).data;
      return (await api.post<{ message: string }>("/auth/reset-password", { token: resetToken, newPassword: password })).data;
    },
    onSuccess: (data) => {
      if (mode === "login") {
        setAuth(data as AuthResponse);
        window.location.replace("/");
        return;
      }
      if (mode === "register") {
        setSuccessMessage("Account created successfully. Please log in.");
        setMode("login");
        setSearchParams(new URLSearchParams(), { replace: true });
        return;
      }
      if (mode === "forgot") {
        const response = data as ForgotPasswordResponse;
        setSuccessMessage(response.message);
        setResetPreview(response.resetUrl ?? null);
        return;
      }
      setSuccessMessage((data as { message: string }).message);
      setMode("login");
      setSearchParams(new URLSearchParams(), { replace: true });
    },
  });

  const authInvalid = mode === "register"
    ? !displayName.trim() || !email.trim() || !password.trim()
    : mode === "login"
      ? !email.trim() || !password.trim()
      : mode === "forgot"
        ? !email.trim()
        : !resetToken.trim() || !password.trim() || password !== confirmPassword;

  return (
    <div className="auth-screen">
      <Card className="auth-card">
        <div className="auth-toolbar"><ThemeToggleButton /></div>
        <div className="hero-strip" />
        <h1>Personal Finance Tracker</h1>
        <p className="muted">Track transactions, budgets, savings goals, and recurring bills in one calm workspace.</p>
        {successMessage && <p className="success">{successMessage}</p>}
        <form className="form-grid" autoComplete="off" onSubmit={(e) => { e.preventDefault(); setSuccessMessage(""); setResetPreview(null); mutation.mutate(); }}>
          {mode === "register" && <input autoComplete="off" placeholder="Display name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />}
          {(mode === "login" || mode === "register" || mode === "forgot") && <input autoComplete="off" type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} />}
          {mode === "reset" && <input autoComplete="off" placeholder="Reset token" value={resetToken} onChange={(e) => setResetToken(e.target.value)} />}
          {(mode === "login" || mode === "register" || mode === "reset") && <PasswordField value={password} onChange={setPassword} placeholder={mode === "reset" ? "New password" : "Password"} autoComplete={mode === "login" ? "current-password" : "new-password"} />}
          {mode === "reset" && <PasswordField value={confirmPassword} onChange={setConfirmPassword} placeholder="Confirm new password" autoComplete="new-password" />}
          {mode === "register" && <p className="field-help">Password must be 8+ characters and include uppercase, lowercase, and a number.</p>}
          {mode === "reset" && password !== confirmPassword && confirmPassword && <p className="field-help field-error">Passwords must match before you can reset.</p>}
          <button type="submit" disabled={mutation.isPending || authInvalid}>{mutation.isPending ? "Please wait..." : mode === "login" ? "Log In" : mode === "register" ? "Create Account" : mode === "forgot" ? "Send Reset Link" : "Reset Password"}</button>
        </form>
        {resetPreview && <p className="field-help">Open this reset link: <a href={resetPreview}>{resetPreview}</a></p>}
        {mutation.error && <p className="error">{getApiErrorMessage(mutation.error)}</p>}
        {mode === "login" && <div className="auth-links"><button className="linkish" onClick={() => switchMode("register")}>Need an account? Sign up</button><button className="linkish" onClick={() => switchMode("forgot")}>Forgot password?</button></div>}
        {mode === "register" && <button className="linkish" onClick={() => switchMode("login")}>Already have an account? Log in</button>}
        {mode !== "login" && mode !== "register" && <button className="linkish" onClick={() => switchMode("login")}>Back to login</button>}
      </Card>
    </div>
  );
}
function DashboardPage() {
  const { data } = useQuery({ queryKey: ["dashboard"], queryFn: async () => (await api.get<Dashboard>("/dashboard")).data });
  if (!data) return null;

  return (
    <div className="page-stack">
      <PageHeader title="Dashboard" subtitle="One-screen summary of this month's financial picture." />
      {(data.accounts.length === 0 || data.recentTransactions.length === 0 || data.budgets.length === 0) && (
        <Card className="onboarding-card">
          <h3>Getting started</h3>
          <div className="page-stack compact-gap">
            <div className="list-row"><span>1. Create an account</span><strong>{data.accounts.length ? "Done" : "Next"}</strong></div>
            <div className="list-row"><span>2. Add your first transaction</span><strong>{data.recentTransactions.length ? "Done" : "Next"}</strong></div>
            <div className="list-row"><span>3. Set a monthly budget</span><strong>{data.budgets.length ? "Done" : "Optional"}</strong></div>
          </div>
        </Card>
      )}
      {data.budgetAlerts.length > 0 && <div className="page-stack compact-gap">{data.budgetAlerts.slice(0, 4).map((alert, index) => <div key={`${alert.title}-${index}`} className={`banner ${alert.level}`}><strong>{alert.title}</strong><p>{alert.body}</p></div>)}</div>}
      <div className="stats-grid">
        <StatCard label="Month Income" value={currency(data.monthIncome)} tone="success" />
        <StatCard label="Month Expense" value={currency(data.monthExpense)} tone="danger" />
        <StatCard label="Net Balance" value={currency(data.netBalance)} tone="primary" />
        <StatCard label="Tracked Balance" value={currency(data.trackedBalance)} tone="warning" />
      </div>
      {data.budgetAlerts.length > 0 && <div className="page-stack compact-gap">{data.budgetAlerts.slice(0, 4).map((alert, index) => <div key={`${alert.title}-${index}`} className={`banner ${alert.level}`}><strong>{alert.title}</strong><p>{alert.body}</p></div>)}</div>}
      <div className="stats-grid">
        <StatCard label="Top expense" value={data.topExpenseCategory} tone="primary" />
        <StatCard label="Over-budget" value={String(data.overBudgetCount)} tone="danger" />
        <StatCard label="Savings progress" value={percent(Number(data.savingsProgressPercent))} tone="success" />
        <StatCard label="Accounts" value={String(data.accounts.length)} tone="warning" />
      </div>
      <div className="two-col">
        <Card>
          <h3>Spending by category</h3>
          {data.categorySpend.length ? <div className="chart-wrap"><ResponsiveContainer width="100%" height={280}><PieChart><Pie data={data.categorySpend} dataKey="value" nameKey="name" outerRadius={100}>{data.categorySpend.map((entry, index) => <Cell key={entry.name} fill={CHART_COLORS[index % CHART_COLORS.length]} />)}</Pie><Tooltip /></PieChart></ResponsiveContainer></div> : <EmptyState title="No spending yet" body="Add your first expense to see category insights." />}
        </Card>
        <Card>
          <h3>Income vs expense trend</h3>
          <div className="chart-wrap"><ResponsiveContainer width="100%" height={280}><LineChart data={data.incomeExpenseTrend}><CartesianGrid stroke="#d7e1f3" strokeDasharray="3 3" /><XAxis dataKey="label" /><YAxis /><Tooltip /><Line type="monotone" dataKey="income" stroke="#16a34a" strokeWidth={3} /><Line type="monotone" dataKey="expense" stroke="#ef4444" strokeWidth={3} /></LineChart></ResponsiveContainer></div>
        </Card>
      </div>
      <div className="two-col">
        <Card>
          <h3>Account balance mix</h3>
          {data.accountBreakdown.length ? <div className="chart-wrap"><ResponsiveContainer width="100%" height={280}><BarChart data={data.accountBreakdown}><CartesianGrid stroke="#d7e1f3" strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis /><Tooltip /><Bar dataKey="value" fill="#4f8cff" radius={[8, 8, 0, 0]} /></BarChart></ResponsiveContainer></div> : <EmptyState title="No account balances yet" body="Create an account to start tracking where your money sits." />}
        </Card>
        <Card>
          <h3>Recurring timeline</h3>
          {data.overdueRecurring.length > 0 && <div className="page-stack compact-gap"><strong>Overdue</strong>{data.overdueRecurring.map((item) => <div key={item.id} className="list-row"><span>{item.title}</span><strong>{dateLabel(item.nextRunDate)}</strong></div>)}</div>}
          {data.dueTodayRecurring.length > 0 && <div className="page-stack compact-gap"><strong>Due today</strong>{data.dueTodayRecurring.map((item) => <div key={item.id} className="list-row"><span>{item.title}</span><strong>{dateLabel(item.nextRunDate)}</strong></div>)}</div>}
          {data.upcomingRecurring.length > 0 ? <div className="page-stack compact-gap"><strong>Upcoming</strong>{data.upcomingRecurring.map((item) => <div key={item.id} className="list-row"><span>{item.title}</span><strong>{dateLabel(item.nextRunDate)}</strong></div>)}</div> : data.overdueRecurring.length === 0 && data.dueTodayRecurring.length === 0 ? <EmptyState title="No recurring items" body="Create subscriptions or salary schedules to see them here." /> : null}
        </Card>
      </div>
      <div className="two-col">
        <Card><h3>Recent transactions</h3><SimpleTransactions rows={data.recentTransactions} /></Card>
        <Card>
          <h3>Goal snapshot</h3>
          {data.goals.length ? data.goals.slice(0, 5).map((goal) => <div key={goal.id} className="budget-row"><div><strong>{goal.name}</strong><p className="muted">{currency(goal.currentAmount)} of {currency(goal.targetAmount)}</p></div><div className="budget-bar"><span style={{ width: `${Math.min((goal.currentAmount / goal.targetAmount) * 100, 100)}%` }} /></div><strong>{percent((goal.currentAmount / goal.targetAmount) * 100)}</strong></div>) : <EmptyState title="No goals yet" body="Create a savings goal to keep long-term plans visible." />}
        </Card>
      </div>
      <Card>
        <h3>Budget progress</h3>
        {data.budgets.length ? data.budgets.map((budget) => <div key={budget.id} className="budget-row"><div><strong>{budget.category}</strong><p className="muted">{currency(budget.spent)} of {currency(budget.amount)}</p></div><div className="budget-bar"><span style={{ width: `${Math.min(Number(budget.percent), 100)}%` }} /></div><strong className={budget.thresholdReached ? "text-danger" : undefined}>{percent(Number(budget.percent))}</strong></div>) : <EmptyState title="No budgets yet" body="Set monthly budgets to compare plan versus actual spending." />}
      </Card>
    </div>
  );
}

function TransactionsPage() {
  const queryClient = useQueryClient();
  const [transactionParams, setTransactionParams] = useSearchParams();
  const initialForm: TransactionForm = { type: "expense", amount: "", date: new Date().toISOString().slice(0, 10), accountId: "", categoryId: "", destinationAccountId: "", merchant: "", note: "", paymentMethod: "", tags: "" };
  const [form, setForm] = useState<TransactionForm>(initialForm);
  const [showErrors, setShowErrors] = useState(false);
  const [message, setMessage] = useState<ToastMessage>(null);
  const [editId, setEditId] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<{ id: string; label: string } | null>(null);
    const [filters, setFilters] = useState({ search: transactionParams.get("search") ?? "", type: "", accountId: "", categoryId: "", startDate: "", endDate: "" });
  const transactionModalOpen = transactionParams.get("modal") === "new" || transactionParams.get("modal") === "edit";
  const queryString = new URLSearchParams(Object.entries(filters).filter(([, value]) => value)).toString();
  const { data } = useQuery({ queryKey: ["transactions", filters], queryFn: async () => (await api.get<{ content: Transaction[] }>(`/transactions?size=50${queryString ? `&${queryString}` : ""}`)).data });
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: async () => (await api.get<Account[]>("/accounts")).data });
  const categories = useQuery({ queryKey: ["categories"], queryFn: async () => (await api.get<Category[]>("/categories")).data });

  const save = useMutation({
    mutationFn: async () => {
      const payload = { ...form, amount: Number(form.amount), categoryId: form.type === "transfer" ? null : form.categoryId, destinationAccountId: form.type === "transfer" ? form.destinationAccountId : null, merchant: form.merchant || null, note: form.note || null, paymentMethod: form.paymentMethod || null, tags: form.tags ? form.tags.split(",").map((tag) => tag.trim()).filter(Boolean) : [] };
      return editId ? api.put(`/transactions/${editId}`, payload) : api.post("/transactions", payload);
    },
    onSuccess: async () => {
      setForm(initialForm);
      setEditId(null);
      setShowErrors(false);
      setMessage({ type: "success", text: editId ? "Transaction updated successfully." : "Transaction saved successfully." });
      setTransactionParams({});
      await queryClient.invalidateQueries({ queryKey: ["transactions"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      await queryClient.invalidateQueries({ queryKey: ["accounts"] });
    },
    onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }),
  });

  const remove = useMutation({
    mutationFn: async (id: string) => api.delete(`/transactions/${id}`),
    onSuccess: async () => {
      setMessage({ type: "success", text: "Transaction deleted successfully." });
      await queryClient.invalidateQueries({ queryKey: ["transactions"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      await queryClient.invalidateQueries({ queryKey: ["accounts"] });
    },
    onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }),
  });

  const amountInvalid = !form.amount || Number(form.amount) <= 0;
  const accountMissing = !form.accountId;
  const categoryMissing = form.type !== "transfer" && !form.categoryId;
  const destinationMissing = form.type === "transfer" && !form.destinationAccountId;
  const transactionInvalid = amountInvalid || accountMissing || categoryMissing || destinationMissing;

  const startEdit = (row: Transaction) => {
    if (row.type === "transfer") {
      setMessage({ type: "error", text: "Transfer entries are best recreated instead of edited." });
      return;
    }
    setEditId(row.id);
    setTransactionParams({ modal: "edit", id: row.id });
    setShowErrors(false);
    setForm({ type: row.type, amount: String(row.amount), date: row.transactionDate, accountId: row.account.id, categoryId: row.category?.id ?? "", destinationAccountId: "", merchant: row.merchant ?? "", note: row.note ?? "", paymentMethod: row.paymentMethod ?? "", tags: row.tags ?? "" });
  };

  return (
    <div className="page-stack">
      <PageHeader title="Transactions" subtitle="Create, edit, filter, and review income, expenses, and transfers." action={<button type="button" className="primary-ghost" onClick={() => { setEditId(null); setForm(initialForm); setShowErrors(false); setTransactionParams({ modal: "new" }); }}>Add transaction</button>} />
      {message && <FeedbackToast message={message} onClose={() => setMessage(null)} />}
      <div className="two-col">
        <Card>
          <div className="inline-header"><h3>{editId ? "Edit transaction" : "Add transaction"}</h3>{editId && <button type="button" className="secondary" onClick={() => { setEditId(null); setForm(initialForm); setShowErrors(false); }}>Cancel edit</button>}</div>
          <form className="form-grid transaction-form-grid" onSubmit={(e) => { e.preventDefault(); setShowErrors(true); if (!transactionInvalid) { setMessage(null); save.mutate(); } }}>
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value, categoryId: "", destinationAccountId: "" })}><option value="expense">Expense</option><option value="income">Income</option><option value="transfer">Transfer</option></select>
            <input placeholder="Amount" type="number" min="0" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            {showErrors && amountInvalid && <p className="field-help field-error">Enter an amount greater than 0.</p>}
            <input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
            <select value={form.accountId} onChange={(e) => setForm({ ...form, accountId: e.target.value })}><option value="">Select account</option>{accounts.data?.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
            {showErrors && accountMissing && <p className="field-help field-error">Select the account that this transaction belongs to.</p>}
            {form.type === "transfer" ? <><select value={form.destinationAccountId} onChange={(e) => setForm({ ...form, destinationAccountId: e.target.value })}><option value="">Destination account</option>{accounts.data?.filter((item) => item.id !== form.accountId).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{showErrors && destinationMissing && <p className="field-help field-error">Choose where the money should be transferred.</p>}</> : <><select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}><option value="">Select category</option>{categories.data?.filter((item) => item.type === form.type).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{showErrors && categoryMissing && <p className="field-help field-error">Choose a category to organize this entry.</p>}</>}
            <input placeholder="Merchant" value={form.merchant} onChange={(e) => setForm({ ...form, merchant: e.target.value })} />
            <input placeholder="Payment method" value={form.paymentMethod} onChange={(e) => setForm({ ...form, paymentMethod: e.target.value })} />
            <input placeholder="Tags (comma separated)" value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} />
            <textarea className="form-span-2" placeholder="Note" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
            <p className="field-help form-span-2">{form.type === "transfer" ? "Enter an amount and choose both source and destination accounts." : "Enter an amount and choose an account and category to enable save."}</p>
            <button type="submit" disabled={save.isPending}>{save.isPending ? "Saving..." : editId ? "Update transaction" : "Save transaction"}</button>
          </form>
        </Card>
        <Card>
          <h3>All transactions</h3>
          <div className="form-grid compact-form">
            <input placeholder="Search merchant or note" value={filters.search} onChange={(e) => setFilters({ ...filters, search: e.target.value })} />
            <select value={filters.type} onChange={(e) => setFilters({ ...filters, type: e.target.value, categoryId: "" })}><option value="">All types</option><option value="expense">Expense</option><option value="income">Income</option><option value="transfer">Transfer</option></select>
            <select value={filters.accountId} onChange={(e) => setFilters({ ...filters, accountId: e.target.value })}><option value="">All accounts</option>{accounts.data?.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
            <select value={filters.categoryId} onChange={(e) => setFilters({ ...filters, categoryId: e.target.value })}><option value="">All categories</option>{categories.data?.filter((item) => !filters.type || item.type === filters.type).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
            <input type="date" value={filters.startDate} onChange={(e) => setFilters({ ...filters, startDate: e.target.value })} />
            <input type="date" value={filters.endDate} onChange={(e) => setFilters({ ...filters, endDate: e.target.value })} />
          </div>
          {data?.content?.length ? data.content.map((row) => <div key={row.id} className="table-row"><div><strong>{row.merchant || row.category?.name || row.type}</strong><p className="muted">{row.account.name} - {row.category?.name || row.type} - {dateLabel(row.transactionDate)}</p></div><div className="row-actions"><strong className={row.type === "expense" ? "text-danger" : "text-success"}>{row.type === "expense" ? "-" : "+"}{currency(row.amount)}</strong>{row.type !== "transfer" && <button className="action-chip" onClick={() => startEdit(row)}>Edit</button>}<button className="action-chip action-delete" onClick={() => setPendingDelete({ id: row.id, label: row.merchant || row.category?.name || row.type })}>Delete</button></div></div>) : <EmptyState title="No transactions yet" body="Use the filters above or create your first transaction on the left." />}
        </Card>
      </div>
      <ConfirmDialog open={Boolean(pendingDelete)} title="Delete transaction?" body={`This will permanently remove ${pendingDelete?.label ?? "this transaction"}.`} onCancel={() => setPendingDelete(null)} onConfirm={() => { if (!pendingDelete) return; setMessage(null); remove.mutate(pendingDelete.id); setPendingDelete(null); }} />      {transactionModalOpen && <div className="modal-backdrop" role="presentation" onClick={() => { setTransactionParams({}); setEditId(null); setForm(initialForm); setShowErrors(false); }}><div className="modal-card" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}><div className="inline-header"><h3>{editId ? "Edit transaction" : "Add transaction"}</h3>{editId && <button type="button" className="secondary" onClick={() => { setEditId(null); setForm(initialForm); setShowErrors(false); setTransactionParams({}); }}>Cancel edit</button>}</div><form className="form-grid transaction-form-grid" onSubmit={(e) => { e.preventDefault(); setShowErrors(true); if (!transactionInvalid) { setMessage(null); save.mutate(); } }}><select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value, categoryId: "", destinationAccountId: "" })}><option value="expense">Expense</option><option value="income">Income</option><option value="transfer">Transfer</option></select><input placeholder="Amount" type="number" min="0" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />{showErrors && amountInvalid && <p className="field-help field-error">Enter an amount greater than 0.</p>}<input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} /><select value={form.accountId} onChange={(e) => setForm({ ...form, accountId: e.target.value })}><option value="">Select account</option>{accounts.data?.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{showErrors && accountMissing && <p className="field-help field-error">Select the account that this transaction belongs to.</p>}{form.type === "transfer" ? <><select value={form.destinationAccountId} onChange={(e) => setForm({ ...form, destinationAccountId: e.target.value })}><option value="">Destination account</option>{accounts.data?.filter((item) => item.id !== form.accountId).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{showErrors && destinationMissing && <p className="field-help field-error">Choose where the money should be transferred.</p>}</> : <><select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}><option value="">Select category</option>{categories.data?.filter((item) => item.type === form.type).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{showErrors && categoryMissing && <p className="field-help field-error">Choose a category to organize this entry.</p>}</>}<input placeholder="Merchant" value={form.merchant} onChange={(e) => setForm({ ...form, merchant: e.target.value })} /><input placeholder="Payment method" value={form.paymentMethod} onChange={(e) => setForm({ ...form, paymentMethod: e.target.value })} /><input placeholder="Tags (comma separated)" value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} /><textarea className="form-span-2" placeholder="Note" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /><p className="field-help form-span-2">{form.type === "transfer" ? "Enter an amount and choose both source and destination accounts." : "Enter an amount and choose an account and category to enable save."}</p><div className="modal-actions"><button type="button" className="secondary" onClick={() => { setTransactionParams({}); setEditId(null); setForm(initialForm); setShowErrors(false); }}>Close</button><button type="submit" disabled={save.isPending}>{save.isPending ? "Saving..." : editId ? "Update transaction" : "Save transaction"}</button></div></form></div></div>}
    </div>
  );
}
function BudgetsPage() {
  const today = new Date();
  const queryClient = useQueryClient();
  const initialForm: BudgetForm = { categoryId: "", month: String(today.getMonth() + 1), year: String(today.getFullYear()), amount: "", alertThresholdPercent: "80" };
  const [form, setForm] = useState<BudgetForm>(initialForm);
  const [viewMonth, setViewMonth] = useState(initialForm.month);
  const [viewYear, setViewYear] = useState(initialForm.year);
  const [editId, setEditId] = useState<string | null>(null);
  const [showErrors, setShowErrors] = useState(false);
  const [message, setMessage] = useState<ToastMessage>(null);
  const [pendingDelete, setPendingDelete] = useState<{ id: string; label: string } | null>(null);
    const budgets = useQuery({ queryKey: ["budgets", viewMonth, viewYear], queryFn: async () => (await api.get<Budget[]>(`/budgets?month=${viewMonth}&year=${viewYear}`)).data });
  const duplicate = useMutation({ mutationFn: async () => api.post(`/budgets/duplicate-last-month?month=${form.month}&year=${form.year}`), onSuccess: async () => { setMessage({ type: "success", text: "Previous month budgets duplicated." }); await queryClient.invalidateQueries({ queryKey: ["budgets"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const categories = useQuery({ queryKey: ["categories"], queryFn: async () => (await api.get<Category[]>("/categories")).data });
  const save = useMutation({ mutationFn: async () => { const payload = { ...form, month: Number(form.month), year: Number(form.year), amount: Number(form.amount), alertThresholdPercent: Number(form.alertThresholdPercent) }; return editId ? api.put(`/budgets/${editId}`, payload) : api.post("/budgets", payload); }, onSuccess: async () => { setMessage({ type: "success", text: editId ? "Budget updated successfully." : "Budget saved successfully." }); setForm(initialForm); setEditId(null); setShowErrors(false); setViewMonth(initialForm.month); setViewYear(initialForm.year); await queryClient.invalidateQueries({ queryKey: ["budgets"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const remove = useMutation({ mutationFn: async (id: string) => api.delete(`/budgets/${id}`), onSuccess: async () => { setMessage({ type: "success", text: "Budget deleted successfully." }); await queryClient.invalidateQueries({ queryKey: ["budgets"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const budgetInvalid = !form.categoryId || !form.amount || Number(form.amount) <= 0;

  return (
    <div className="page-stack">
      <PageHeader title="Budgets" subtitle="Plan monthly category limits and keep spending aligned." action={<button type="button" className="secondary" onClick={() => duplicate.mutate()}>Duplicate last month</button>} />
      {message && <FeedbackToast message={message} onClose={() => setMessage(null)} />}
      <div className="two-col">
        <Card>
          <div className="inline-header"><h3>{editId ? "Edit budget" : "Add budget"}</h3>{editId && <button type="button" className="secondary" onClick={() => { setEditId(null); setForm(initialForm); setShowErrors(false); }}>Cancel edit</button>}</div>
          <form className="form-grid" onSubmit={(e) => { e.preventDefault(); setShowErrors(true); if (!budgetInvalid) { setMessage(null); save.mutate(); } }}>
            <select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}><option value="">Select category</option>{categories.data?.filter((item) => item.type === "expense").map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
            {showErrors && !form.categoryId && <p className="field-help field-error">Pick an expense category for this budget.</p>}
            <input type="number" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} placeholder="Budget amount" />
            {showErrors && (!form.amount || Number(form.amount) <= 0) && <p className="field-help field-error">Budget amount must be greater than 0.</p>}
            <input type="number" value={form.month} onChange={(e) => setForm({ ...form, month: e.target.value })} placeholder="Month" />
            <input type="number" value={form.year} onChange={(e) => setForm({ ...form, year: e.target.value })} placeholder="Year" />
            <input type="number" value={form.alertThresholdPercent} onChange={(e) => setForm({ ...form, alertThresholdPercent: e.target.value })} placeholder="Alert threshold %" />
            <p className="field-help">Choose a category and amount, then set the month and year you want to track.</p>
            <button type="submit" disabled={save.isPending}>{save.isPending ? "Saving..." : editId ? "Update budget" : "Save budget"}</button>
          </form>
        </Card>
        <Card>
          <h3>Budget list</h3>
          <div className="form-grid compact-form"><input type="number" value={viewMonth} onChange={(e) => setViewMonth(e.target.value)} placeholder="Month" /><input type="number" value={viewYear} onChange={(e) => setViewYear(e.target.value)} placeholder="Year" /></div>
          {budgets.data?.length ? budgets.data.map((item) => <div key={item.id} className="list-row"><span>{item.category.name}</span><div className="row-actions"><strong>{currency(item.amount)}</strong><button className="action-chip" onClick={() => { setEditId(item.id); setForm({ categoryId: item.category.id, month: String(item.month), year: String(item.year), amount: String(item.amount), alertThresholdPercent: String(item.alertThresholdPercent) }); setShowErrors(false); }}>Edit</button><button className="action-chip action-delete" onClick={() => setPendingDelete({ id: item.id, label: item.category.name })}>Delete</button></div></div>) : <EmptyState title="No budgets yet" body="Create a monthly budget to start tracking category limits." />}
        </Card>
      </div>
      <ConfirmDialog open={Boolean(pendingDelete)} title="Delete budget?" body={`This will permanently remove the ${pendingDelete?.label ?? "selected"} budget.`} onCancel={() => setPendingDelete(null)} onConfirm={() => { if (!pendingDelete) return; setMessage(null); remove.mutate(pendingDelete.id); setPendingDelete(null); }} />
    </div>
  );
}

function GoalsPage() {
  const queryClient = useQueryClient();
  const initialForm: GoalForm = { name: "", targetAmount: "", currentAmount: "0", targetDate: "", linkedAccountId: "", color: "#1d4ed8", icon: "TG", status: "active" };
  const [form, setForm] = useState<GoalForm>(initialForm);
  const [editId, setEditId] = useState<string | null>(null);
  const [showErrors, setShowErrors] = useState(false);
  const [message, setMessage] = useState<ToastMessage>(null);
  const [pendingDelete, setPendingDelete] = useState<{ id: string; label: string } | null>(null);
  const [goalAction, setGoalAction] = useState<{ id: string; mode: "contribute" | "withdraw"; amount: string; accountId: string } | null>(null);
  const goals = useQuery({ queryKey: ["goals"], queryFn: async () => (await api.get<Goal[]>("/goals")).data });
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: async () => (await api.get<Account[]>("/accounts")).data });
  const save = useMutation({ mutationFn: async () => { const payload = { ...form, targetAmount: Number(form.targetAmount), currentAmount: Number(form.currentAmount), linkedAccountId: form.linkedAccountId || null, targetDate: form.targetDate || null }; return editId ? api.put(`/goals/${editId}`, payload) : api.post("/goals", payload); }, onSuccess: async () => { setForm(initialForm); setEditId(null); setShowErrors(false); setMessage({ type: "success", text: editId ? "Goal updated successfully." : "Goal saved successfully." }); await queryClient.invalidateQueries({ queryKey: ["goals"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const adjustGoal = useMutation({ mutationFn: async () => { if (!goalAction) return; return api.post(`/goals/${goalAction.id}/${goalAction.mode}`, { amount: Number(goalAction.amount), accountId: goalAction.accountId || null }); }, onSuccess: async () => { setMessage({ type: "success", text: `Goal ${goalAction?.mode === "withdraw" ? "withdrawal" : "contribution"} saved.` }); setGoalAction(null); await queryClient.invalidateQueries({ queryKey: ["goals"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); await queryClient.invalidateQueries({ queryKey: ["accounts"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const remove = useMutation({ mutationFn: async (id: string) => api.delete(`/goals/${id}`), onSuccess: async () => { setMessage({ type: "success", text: "Goal deleted successfully." }); await queryClient.invalidateQueries({ queryKey: ["goals"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const goalInvalid = !form.name.trim() || !form.targetAmount || Number(form.targetAmount) <= 0 || Number(form.currentAmount) < 0;

  return (
    <div className="page-stack">
      <PageHeader title="Goals" subtitle="Create savings goals, update progress, and monitor completion." />
      {message && <FeedbackToast message={message} onClose={() => setMessage(null)} />}
      <div className="two-col">
        <Card>
          <div className="inline-header"><h3>{editId ? "Edit goal" : "Add goal"}</h3>{editId && <button type="button" className="secondary" onClick={() => { setEditId(null); setForm(initialForm); setShowErrors(false); }}>Cancel edit</button>}</div>
          <form className="form-grid" onSubmit={(e) => { e.preventDefault(); setShowErrors(true); if (!goalInvalid) { setMessage(null); save.mutate(); } }}>
            <input placeholder="Goal name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            {showErrors && !form.name.trim() && <p className="field-help field-error">Give the goal a name like Emergency Fund or Vacation.</p>}
            <input type="number" placeholder="Target amount" value={form.targetAmount} onChange={(e) => setForm({ ...form, targetAmount: e.target.value })} />
            {showErrors && (!form.targetAmount || Number(form.targetAmount) <= 0) && <p className="field-help field-error">Target amount must be greater than 0.</p>}
            <input type="number" placeholder="Saved so far" value={form.currentAmount} onChange={(e) => setForm({ ...form, currentAmount: e.target.value })} />
            {showErrors && Number(form.currentAmount) < 0 && <p className="field-help field-error">Saved amount cannot be negative.</p>}
            <input type="date" value={form.targetDate} onChange={(e) => setForm({ ...form, targetDate: e.target.value })} />
            <select value={form.linkedAccountId} onChange={(e) => setForm({ ...form, linkedAccountId: e.target.value })}><option value="">Linked account (optional)</option>{accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</select>
            <p className="field-help">A linked account is optional, but it helps connect savings goals to real balances.</p>
            <button type="submit" disabled={save.isPending}>{save.isPending ? "Saving..." : editId ? "Update goal" : "Save goal"}</button>
          </form>
        </Card>
        <Card>
          <h3>Goal progress</h3>
          {goals.data?.length ? goals.data.map((goal) => <div key={goal.id} className="budget-row"><div><strong>{goal.name}</strong><p className="muted">Due {dateLabel(goal.targetDate)}</p></div><div className="budget-bar"><span style={{ width: `${Math.min((goal.currentAmount / goal.targetAmount) * 100, 100)}%` }} /></div><div className="row-actions"><strong>{currency(goal.currentAmount)} / {currency(goal.targetAmount)}</strong><button className="action-chip" onClick={() => setGoalAction({ id: goal.id, mode: "contribute", amount: "", accountId: goal.linkedAccount?.id ?? "" })}>Add</button><button className="action-chip" onClick={() => setGoalAction({ id: goal.id, mode: "withdraw", amount: "", accountId: goal.linkedAccount?.id ?? "" })}>Withdraw</button><button className="action-chip" onClick={() => { setEditId(goal.id); setForm({ name: goal.name, targetAmount: String(goal.targetAmount), currentAmount: String(goal.currentAmount), targetDate: goal.targetDate ?? "", linkedAccountId: goal.linkedAccount?.id ?? "", color: goal.color ?? "#1d4ed8", icon: goal.icon ?? "TG", status: goal.status ?? "active" }); setShowErrors(false); }}>Edit</button><button className="action-chip action-delete" onClick={() => setPendingDelete({ id: goal.id, label: goal.name })}>Delete</button></div></div>) : <EmptyState title="No goals yet" body="Add a target such as emergency fund, travel, or tuition savings." />}
        </Card>
      </div>
      <ConfirmDialog open={Boolean(pendingDelete)} title="Delete goal?" body={`This will permanently remove the goal ${pendingDelete?.label ?? "selected"}.`} onCancel={() => setPendingDelete(null)} onConfirm={() => { if (!pendingDelete) return; setMessage(null); remove.mutate(pendingDelete.id); setPendingDelete(null); }} />
      {goalAction && <div className="modal-backdrop" role="presentation" onClick={() => setGoalAction(null)}><div className="modal-card" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}><h3>{goalAction.mode === "withdraw" ? "Withdraw from goal" : "Add contribution"}</h3><div className="form-grid compact-form"><input type="number" placeholder="Amount" value={goalAction.amount} onChange={(e) => setGoalAction({ ...goalAction, amount: e.target.value })} /><select value={goalAction.accountId} onChange={(e) => setGoalAction({ ...goalAction, accountId: e.target.value })}><option value="">Account (optional)</option>{accounts.data?.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}</select></div><div className="modal-actions"><button type="button" className="secondary" onClick={() => setGoalAction(null)}>Cancel</button><button type="button" className="action-chip" onClick={() => { if (Number(goalAction.amount) > 0) adjustGoal.mutate(); }}>{adjustGoal.isPending ? "Saving..." : "Save"}</button></div></div></div>}
    </div>
  );
}

function downloadCsv(filename: string, rows: Array<Record<string, string | number>>) {
  if (!rows.length) return;
  const headers = Object.keys(rows[0]);
  const escapeCell = (value: string | number) => `"${String(value ?? "").replace(/"/g, '""')}"`;
  const body = [headers.join(","), ...rows.map((row) => headers.map((header) => escapeCell(row[header] ?? "")).join(","))].join("\n");
  const blob = new Blob([body], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.setAttribute("download", filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function exportPdfReport(title: string, rows: Array<Record<string, string | number>>) {
  if (!rows.length) return;
  const headers = Object.keys(rows[0]);
  const tableRows = rows.map((row) => `<tr>${headers.map((header) => `<td>${String(row[header] ?? "")}</td>`).join("")}</tr>`).join("");
  const popup = window.open("", "_blank", "width=1000,height=700");
  if (!popup) return;
  popup.document.write(`<!doctype html><html><head><title>${title}</title><style>body{font-family:Arial,sans-serif;padding:24px;color:#0f172a}h1{margin-bottom:16px}table{width:100%;border-collapse:collapse}th,td{border:1px solid #cbd5e1;padding:8px;text-align:left;font-size:12px}th{background:#e2e8f0}</style></head><body><h1>${title}</h1><table><thead><tr>${headers.map((header) => `<th>${header}</th>`).join("")}</tr></thead><tbody>${tableRows}</tbody></table></body></html>`);
  popup.document.close();
  popup.focus();
  popup.print();
}

function ReportsPage() {
  const now = new Date();
  const defaultStart = useMemo(() => localStorage.getItem("finance_global_start") ?? new Date(now.getFullYear(), now.getMonth() - 5, 1).toISOString().slice(0, 10), [now]);
  const defaultEnd = useMemo(() => localStorage.getItem("finance_global_end") ?? new Date().toISOString().slice(0, 10), []);
  const [filters, setFilters] = useState({ startDate: defaultStart, endDate: defaultEnd, type: "", accountId: "", categoryId: "" });
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: async () => (await api.get<Account[]>("/accounts")).data });
  const categories = useQuery({ queryKey: ["categories"], queryFn: async () => (await api.get<Category[]>("/categories")).data });
  const queryString = new URLSearchParams(Object.entries(filters).filter(([, value]) => value)).toString();
  const { data } = useQuery({ queryKey: ["reports", filters], queryFn: async () => (await api.get<ReportResponse>(`/reports/category-spend?${queryString}`)).data });

  return (
    <div className="page-stack">
      <PageHeader title="Reports" subtitle="Filter your data range, inspect trends, and export transaction snapshots." action={data ? <div className="row-actions"><button type="button" className="secondary" onClick={() => { api.post("/audit-events", { eventType: "report_exported", format: "csv", startDate: filters.startDate, endDate: filters.endDate }); downloadCsv(`finance-report-${filters.startDate}-to-${filters.endDate}.csv`, data.transactions); }}>Export CSV</button><button type="button" className="secondary" onClick={() => { api.post("/audit-events", { eventType: "report_exported", format: "pdf", startDate: filters.startDate, endDate: filters.endDate }); exportPdfReport(`Finance report ${filters.startDate} to ${filters.endDate}`, data.transactions); }}>Export PDF</button></div> : null} />
      <Card><div className="form-grid compact-form"><input type="date" value={filters.startDate} onChange={(e) => setFilters({ ...filters, startDate: e.target.value })} /><input type="date" value={filters.endDate} onChange={(e) => setFilters({ ...filters, endDate: e.target.value })} /><select value={filters.type} onChange={(e) => setFilters({ ...filters, type: e.target.value, categoryId: "" })}><option value="">All types</option><option value="expense">Expense</option><option value="income">Income</option><option value="transfer">Transfer</option></select><select value={filters.accountId} onChange={(e) => setFilters({ ...filters, accountId: e.target.value })}><option value="">All accounts</option>{accounts.data?.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}</select><select value={filters.categoryId} onChange={(e) => setFilters({ ...filters, categoryId: e.target.value })}><option value="">All categories</option>{categories.data?.filter((category) => !filters.type || category.type === filters.type).map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></div></Card>
      {data ? <><div className="stats-grid"><StatCard label="Income" value={currency(data.summary.income)} tone="success" /><StatCard label="Expense" value={currency(data.summary.expense)} tone="danger" /><StatCard label="Net" value={currency(data.summary.net)} tone="primary" /><StatCard label="Transactions" value={String(data.summary.transactionCount)} tone="warning" /></div><div className="two-col"><Card><h3>Category spend</h3><div className="chart-wrap"><ResponsiveContainer width="100%" height={280}><BarChart data={data.categorySpend}><CartesianGrid stroke="#d7e1f3" strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis /><Tooltip /><Bar dataKey="value" fill="#4f8cff" radius={[8, 8, 0, 0]} /></BarChart></ResponsiveContainer></div></Card><Card><h3>Income vs expense</h3><div className="chart-wrap"><ResponsiveContainer width="100%" height={280}><LineChart data={data.incomeVsExpense}><CartesianGrid stroke="#d7e1f3" strokeDasharray="3 3" /><XAxis dataKey="label" /><YAxis /><Tooltip /><Line type="monotone" dataKey="income" stroke="#16a34a" strokeWidth={3} /><Line type="monotone" dataKey="expense" stroke="#ef4444" strokeWidth={3} /></LineChart></ResponsiveContainer></div></Card></div><div className="two-col"><Card><h3>Account balances</h3><div className="chart-wrap"><ResponsiveContainer width="100%" height={280}><BarChart data={data.accountBalanceTrend}><CartesianGrid stroke="#d7e1f3" strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis /><Tooltip /><Bar dataKey="value" fill="#34d399" radius={[8, 8, 0, 0]} /></BarChart></ResponsiveContainer></div></Card><Card><h3>Top categories</h3>{data.topCategories.length ? data.topCategories.map((category, index) => <div key={category} className="list-row"><span>{index + 1}. {category}</span></div>) : <EmptyState title="No category data" body="Adjust the filters or add more expenses to populate this report." />}</Card></div><Card><h3>Report transactions</h3>{data.transactions.length ? data.transactions.slice(0, 10).map((row, index) => <div key={`${row.date}-${row.merchant}-${index}`} className="table-row"><div><strong>{row.merchant || row.category || row.type}</strong><p className="muted">{row.account} - {row.category || row.type} - {dateLabel(row.date)}</p></div><div className="row-actions"><strong className={row.type === "expense" ? "text-danger" : "text-success"}>{row.type === "expense" ? "-" : "+"}{currency(row.amount)}</strong></div></div>) : <EmptyState title="No transactions in this range" body="Broaden the date range or clear a filter to see more activity." />}</Card></> : null}
    </div>
  );
}
function RecurringPage() {
  const queryClient = useQueryClient();
  const initialForm: RecurringForm = { title: "", type: "expense", amount: "", categoryId: "", accountId: "", frequency: "monthly", startDate: new Date().toISOString().slice(0, 10), endDate: "", nextRunDate: new Date().toISOString().slice(0, 10), autoCreateTransaction: true, paused: false };
  const [form, setForm] = useState<RecurringForm>(initialForm);
  const [editId, setEditId] = useState<string | null>(null);
  const [showErrors, setShowErrors] = useState(false);
  const [message, setMessage] = useState<ToastMessage>(null);
  const [pendingDelete, setPendingDelete] = useState<{ id: string; label: string } | null>(null);
    const recurring = useQuery({ queryKey: ["recurring"], queryFn: async () => (await api.get<RecurringTransaction[]>("/recurring")).data });
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: async () => (await api.get<Account[]>("/accounts")).data });
  const categories = useQuery({ queryKey: ["categories"], queryFn: async () => (await api.get<Category[]>("/categories")).data });
  const save = useMutation({ mutationFn: async () => { const payload = { ...form, amount: Number(form.amount), categoryId: form.categoryId || null, accountId: form.accountId || null, endDate: form.endDate || null, nextRunDate: form.nextRunDate || form.startDate }; return editId ? api.put(`/recurring/${editId}`, payload) : api.post("/recurring", payload); }, onSuccess: async () => { setForm(initialForm); setEditId(null); setShowErrors(false); setMessage({ type: "success", text: editId ? "Recurring item updated successfully." : "Recurring item saved successfully." }); await queryClient.invalidateQueries({ queryKey: ["recurring"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const remove = useMutation({ mutationFn: async (id: string) => api.delete(`/recurring/${id}`), onSuccess: async () => { setMessage({ type: "success", text: "Recurring item deleted successfully." }); await queryClient.invalidateQueries({ queryKey: ["recurring"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const recurringInvalid = !form.title.trim() || !form.amount || Number(form.amount) <= 0 || !form.accountId || !form.categoryId;

  return (
    <div className="page-stack">
      <PageHeader title="Recurring" subtitle="Manage subscriptions, bills, salaries, and scheduled auto-posting." />
      {message && <FeedbackToast message={message} onClose={() => setMessage(null)} />}
      <div className="two-col">
        <Card>
          <div className="inline-header"><h3>{editId ? "Edit recurring item" : "New recurring item"}</h3>{editId && <button type="button" className="secondary" onClick={() => { setEditId(null); setForm(initialForm); setShowErrors(false); }}>Cancel edit</button>}</div>
          <form className="form-grid" onSubmit={(e) => { e.preventDefault(); setShowErrors(true); if (!recurringInvalid) { setMessage(null); save.mutate(); } }}>
            <input placeholder="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            {showErrors && !form.title.trim() && <p className="field-help field-error">Give the recurring item a clear title like Rent or Netflix.</p>}
            <input type="number" placeholder="Amount" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            {showErrors && (!form.amount || Number(form.amount) <= 0) && <p className="field-help field-error">Amount must be greater than 0.</p>}
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value, categoryId: "" })}><option value="expense">Expense</option><option value="income">Income</option></select>
            <select value={form.accountId} onChange={(e) => setForm({ ...form, accountId: e.target.value })}><option value="">Select account</option>{accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</select>
            {showErrors && !form.accountId && <p className="field-help field-error">Select the account that will be affected automatically.</p>}
            <select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}><option value="">Select category</option>{categories.data?.filter((c) => c.type === form.type).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select>
            {showErrors && !form.categoryId && <p className="field-help field-error">Choose a matching category for this recurring item.</p>}
            <select value={form.frequency} onChange={(e) => setForm({ ...form, frequency: e.target.value })}><option value="daily">Daily</option><option value="weekly">Weekly</option><option value="monthly">Monthly</option><option value="yearly">Yearly</option></select>
            <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value, nextRunDate: form.nextRunDate || e.target.value })} />
            <input type="date" value={form.nextRunDate} onChange={(e) => setForm({ ...form, nextRunDate: e.target.value })} />
            <input type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
            <label className="check-row"><input type="checkbox" checked={form.autoCreateTransaction} onChange={(e) => setForm({ ...form, autoCreateTransaction: e.target.checked })} />Auto-create matching transactions</label>
            <label className="check-row"><input type="checkbox" checked={form.paused} onChange={(e) => setForm({ ...form, paused: e.target.checked })} />Pause this schedule</label>
            <p className="field-help">Choose the first date this schedule should run. You can also adjust the next run date manually when editing.</p>
            <button type="submit" disabled={save.isPending}>{save.isPending ? "Saving..." : editId ? "Update recurring item" : "Save recurring item"}</button>
          </form>
        </Card>
        <Card>
          <h3>Recurring schedule</h3>
          {recurring.data?.length ? recurring.data.map((item) => <div key={item.id} className="list-row"><span>{item.title} - {item.frequency}</span><div className="row-actions"><strong>{dateLabel(item.nextRunDate)}</strong><button className="action-chip" onClick={() => { setEditId(item.id); setForm({ title: item.title, type: item.type, amount: String(item.amount), categoryId: item.category?.id ?? "", accountId: item.account?.id ?? "", frequency: item.frequency, startDate: item.startDate, endDate: item.endDate ?? "", nextRunDate: item.nextRunDate, autoCreateTransaction: item.autoCreateTransaction, paused: item.paused }); setShowErrors(false); }}>Edit</button><button className="action-chip action-delete" onClick={() => setPendingDelete({ id: item.id, label: item.title })}>Delete</button></div></div>) : <EmptyState title="No recurring items" body="Add subscriptions, rent, salary, or planned bills." />}
        </Card>
      </div>
      <ConfirmDialog open={Boolean(pendingDelete)} title="Delete recurring item?" body={`This will permanently remove ${pendingDelete?.label ?? "this recurring item"}.`} onCancel={() => setPendingDelete(null)} onConfirm={() => { if (!pendingDelete) return; setMessage(null); remove.mutate(pendingDelete.id); setPendingDelete(null); }} />
    </div>
  );
}

function AccountsPage() {
  const queryClient = useQueryClient();
  const initialForm: AccountForm = { name: "", type: "bank", openingBalance: "0", institutionName: "" };
  const [form, setForm] = useState<AccountForm>(initialForm);
  const [editId, setEditId] = useState<string | null>(null);
  const [showErrors, setShowErrors] = useState(false);
  const [message, setMessage] = useState<ToastMessage>(null);
  const [pendingDelete, setPendingDelete] = useState<{ id: string; label: string } | null>(null);
    const { data } = useQuery({ queryKey: ["accounts"], queryFn: async () => (await api.get<Account[]>("/accounts")).data });
  const save = useMutation({ mutationFn: async () => { const payload = { ...form, openingBalance: Number(form.openingBalance) }; return editId ? api.put(`/accounts/${editId}`, payload) : api.post("/accounts", payload); }, onSuccess: async () => { setForm(initialForm); setEditId(null); setShowErrors(false); setMessage({ type: "success", text: editId ? "Account updated successfully." : "Account saved successfully." }); await queryClient.invalidateQueries({ queryKey: ["accounts"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const remove = useMutation({ mutationFn: async (id: string) => api.delete(`/accounts/${id}`), onSuccess: async () => { setMessage({ type: "success", text: "Account deleted successfully." }); await queryClient.invalidateQueries({ queryKey: ["accounts"] }); await queryClient.invalidateQueries({ queryKey: ["dashboard"] }); }, onError: (error) => setMessage({ type: "error", text: getApiErrorMessage(error) }) });
  const accountInvalid = !form.name.trim() || Number(form.openingBalance) < 0;

  return (
    <div className="page-stack">
      <PageHeader title="Accounts" subtitle="Track balances across bank accounts, cash wallets, savings, and cards." />
      {message && <FeedbackToast message={message} onClose={() => setMessage(null)} />}
      <div className="two-col">
        <Card>
          <div className="inline-header"><h3>{editId ? "Edit account" : "Add account"}</h3>{editId && <button type="button" className="secondary" onClick={() => { setEditId(null); setForm(initialForm); setShowErrors(false); }}>Cancel edit</button>}</div>
          <form className="form-grid" onSubmit={(e) => { e.preventDefault(); setShowErrors(true); if (!accountInvalid) { setMessage(null); save.mutate(); } }}>
            <input placeholder="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            {showErrors && !form.name.trim() && <p className="field-help field-error">Enter an account name like HDFC Bank or Cash Wallet.</p>}
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}><option value="bank">Bank account</option><option value="credit-card">Credit card</option><option value="cash">Cash wallet</option><option value="savings">Savings account</option></select>
            <input type="number" placeholder="Opening balance" value={form.openingBalance} onChange={(e) => setForm({ ...form, openingBalance: e.target.value })} />
            {showErrors && Number(form.openingBalance) < 0 && <p className="field-help field-error">Opening balance cannot be negative.</p>}
            <input placeholder="Institution name" value={form.institutionName} onChange={(e) => setForm({ ...form, institutionName: e.target.value })} />
            <p className="field-help">Institution name is optional, but it helps make the account list easier to scan.</p>
            <button type="submit" disabled={save.isPending}>{save.isPending ? "Saving..." : editId ? "Update account" : "Save account"}</button>
          </form>
        </Card>
        <Card>
          <h3>Balances</h3>
          {data?.length ? data.map((account) => <div key={account.id} className="list-row"><span>{account.name}<span className="muted"> - {formatAccountType(account.type)}</span></span><div className="row-actions"><strong>{currency(account.currentBalance)}</strong><button className="action-chip" onClick={() => { setEditId(account.id); setForm({ name: account.name, type: account.type, openingBalance: String(account.openingBalance), institutionName: account.institutionName ?? "" }); setShowErrors(false); }}>Edit</button><button className="action-chip action-delete" onClick={() => setPendingDelete({ id: account.id, label: account.name })}>Delete</button></div></div>) : <EmptyState title="No accounts yet" body="Create an account first so transactions and goals can attach to it." />}
        </Card>
      </div>
      <ConfirmDialog open={Boolean(pendingDelete)} title="Delete account?" body={`This will permanently remove ${pendingDelete?.label ?? "this account"}.`} onCancel={() => setPendingDelete(null)} onConfirm={() => { if (!pendingDelete) return; setMessage(null); remove.mutate(pendingDelete.id); setPendingDelete(null); }} />
    </div>
  );
}

function SimpleTransactions({ rows }: { rows: Transaction[] }) {
  return rows.length ? rows.map((row) => <div key={row.id} className="list-row"><span>{row.merchant || row.category?.name || row.type}</span><strong className={row.type === "expense" ? "text-danger" : "text-success"}>{row.type === "expense" ? "-" : "+"}{currency(row.amount)}</strong></div>) : <EmptyState title="No transactions" body="Activity will appear here once you start tracking." />;
}

function StatCard({ label, value, tone }: { label: string; value: string; tone: string }) {
  return <Card className={`stat-card ${tone}`}><p>{label}</p><h2>{value}</h2></Card>;
}

function SettingsPage() {
  const user = useAuthStore((state) => state.user);

  return (
    <div className="page-stack">
      <PageHeader title="Settings" subtitle="Review your profile, app readiness, and support options in one place." />
      <div className="two-col">
        <Card>
          <h3>Profile</h3>
          <div className="page-stack compact-gap">
            <div className="list-row"><span>Name</span><strong>{user?.displayName ?? "Finance user"}</strong></div>
            <div className="list-row"><span>Email</span><strong>{user?.email ?? "Not available"}</strong></div>
            <div className="list-row"><span>Theme support</span><strong>Light and dark</strong></div>
            <div className="list-row"><span>Account status</span><strong>Active</strong></div>
          </div>
        </Card>
        <Card>
          <h3>Help</h3>
          <div className="page-stack compact-gap">
            <div className="list-row"><span>Accounts</span><strong>Create balances first</strong></div>
            <div className="list-row"><span>Transactions</span><strong>Add income and expenses next</strong></div>
            <div className="list-row"><span>Budgets and goals</span><strong>Use them to plan ahead</strong></div>
            <div className="list-row"><span>Reports</span><strong>Review trends and exports</strong></div>
          </div>
        </Card>
      </div>
    </div>
  );
}
export default function App() {
  useBootstrap();
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Protected />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/budgets" element={<BudgetsPage />} />
        <Route path="/goals" element={<GoalsPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/recurring" element={<RecurringPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}


























