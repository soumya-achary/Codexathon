import { useMemo, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useAuthStore } from "../store/auth";
import { useThemeMode } from "../hooks/useThemeMode";
import { api } from "../api/client";
import type { AuditEvent, Dashboard } from "../types";

const links = [
  ["/", "Dashboard"],
  ["/categories", "Categories"],
  ["/transactions", "Transactions"],
  ["/budgets", "Budgets"],
  ["/goals", "Goals"],
  ["/reports", "Reports"],
  ["/insights", "Insights"],
  ["/forecast", "Forecast"],
  ["/rules", "Rules"],
  ["/shared-accounts", "Shared"],
  ["/recurring", "Recurring"],
  ["/accounts", "Accounts"],
  ["/settings", "Settings"],
] as const;

function ThemeIcon({ theme }: { theme: "light" | "dark" }) {
  return theme === "light" ? (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2.5M12 19.5V22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M2 12h2.5M19.5 12H22M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8" />
    </svg>
  ) : (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z" />
    </svg>
  );
}

function BellIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M15 17h5l-1.4-1.4a2 2 0 01-.6-1.4V11a6 6 0 10-12 0v3.2a2 2 0 01-.6 1.4L4 17h5" />
      <path d="M10 17a2 2 0 004 0" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6" />
      <path d="M20 20l-4.2-4.2" />
    </svg>
  );
}

export function Shell() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useThemeMode();
  const [search, setSearch] = useState("");
  const [showNotifications, setShowNotifications] = useState(false);
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [startDate, setStartDate] = useState(localStorage.getItem("finance_global_start") ?? "");
  const [endDate, setEndDate] = useState(localStorage.getItem("finance_global_end") ?? "");

  const dashboard = useQuery({ queryKey: ["shell-dashboard"], queryFn: async () => (await api.get<Dashboard>("/dashboard")).data });
  const auditEvents = useQuery({ queryKey: ["audit-events"], queryFn: async () => (await api.get<AuditEvent[]>("/audit-events")).data });

  const notifications = useMemo(() => {
    const alerts = dashboard.data?.budgetAlerts ?? [];
    const events = (auditEvents.data ?? []).slice(0, 5).map((event) => ({
      level: "info",
      title: event.eventType.replace(/_/g, " "),
      body: new Date(event.createdAt).toLocaleString(),
    }));
    return [...alerts, ...events].slice(0, 8);
  }, [dashboard.data, auditEvents.data]);

  const submitSearch = () => {
    const next = search.trim();
    navigate(next ? `/transactions?search=${encodeURIComponent(next)}` : "/transactions");
  };

  const applyDateRange = () => {
    if (startDate) localStorage.setItem("finance_global_start", startDate);
    else localStorage.removeItem("finance_global_start");
    if (endDate) localStorage.setItem("finance_global_end", endDate);
    else localStorage.removeItem("finance_global_end");
    navigate("/reports");
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-panel">
          <div className="sidebar-top">
            <div className="brand-mark">F</div>
            <div>
              <div className="brand">Finance</div>
              <p className="muted sidebar-copy">Personal tracker</p>
            </div>
          </div>
          <nav className="sidebar-nav">
            {links.map(([to, label]) => (
              <NavLink key={to} to={to} end={to === "/"} className="nav-link">
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="sidebar-foot">
            <div className="profile-card">
              <div>
                <strong>{user?.displayName ?? "Finance user"}</strong>
                <p className="muted sidebar-copy">{user?.email}</p>
              </div>
              <button
                type="button"
                className="theme-switch icon-only"
                onClick={toggleTheme}
                aria-label={theme === "light" ? "Switch to dark mode" : "Switch to light mode"}
                title={theme === "light" ? "Switch to dark mode" : "Switch to light mode"}
              >
                <ThemeIcon theme={theme} />
              </button>
            </div>
            <button className="secondary" onClick={() => { logout(); navigate("/login"); }}>
              Log out
            </button>
          </div>
        </div>
      </aside>
      <main className="content">
        <div className="content-inner">
          <div className="toolbar-shell">
            <div className="toolbar-header">
              <div>
                <p className="eyebrow">Finance workspace</p>
                <h2>Stay on top of money movement</h2>
              </div>
              <div className="toolbar-actions">
                <button type="button" className="toolbar-primary" onClick={() => navigate("/transactions?modal=new")}>
                  Add transaction
                </button>
              </div>
            </div>
            <div className="toolbar-lower">
              <div className="toolbar-search">
                <SearchIcon />
                <input
                  aria-label="Global transaction search"
                  placeholder="Search transactions by merchant or note"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      submitSearch();
                    }
                  }}
                />
                <button type="button" className="action-chip" onClick={submitSearch}>Search</button>
              </div>
              <div className="toolbar-range-card">
                <span className="toolbar-range-label">Date range</span>
                <div className="toolbar-range">
                  <input aria-label="Global start date" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
                  <input aria-label="Global end date" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
                  <button type="button" className="action-chip" onClick={applyDateRange}>Apply</button>
                </div>
              </div>
              <div className="toolbar-user">
                <div style={{ position: "relative" }}>
                  <button type="button" className="toolbar-icon-button" aria-label="Open notifications" onClick={() => { setShowNotifications((current) => !current); setShowProfileMenu(false); }}>
                    <BellIcon />
                    {notifications.length ? <span>{Math.min(notifications.length, 9)}</span> : null}
                  </button>
                  {showNotifications && (
                    <div className="topbar-panel">
                      <div className="page-stack compact-gap">
                        <strong>Notifications</strong>
                        {notifications.length ? notifications.map((item, index) => <div key={`${item.title}-${index}`} className={`banner ${item.level}`}><strong>{item.title}</strong><p>{item.body}</p></div>) : <p className="muted">No alerts right now.</p>}
                      </div>
                    </div>
                  )}
                </div>
                <div style={{ position: "relative" }}>
                  <button type="button" className="toolbar-user-button" onClick={() => { setShowProfileMenu((current) => !current); setShowNotifications(false); }}>
                    <span className="toolbar-user-name">{user?.displayName ?? "Finance user"}</span>
                    <span className="toolbar-user-email">{user?.email ?? "Signed in"}</span>
                  </button>
                  {showProfileMenu && (
                    <div className="topbar-panel profile-menu">
                      <button type="button" className="secondary" onClick={() => { navigate("/settings"); setShowProfileMenu(false); }}>Open settings</button>
                      <button type="button" className="secondary" onClick={() => { toggleTheme(); setShowProfileMenu(false); }}>Switch to {theme === "light" ? "dark" : "light"} mode</button>
                      <button type="button" onClick={() => { logout(); navigate("/login"); }}>Log out</button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
          <Outlet />
          <button type="button" className="mobile-fab" onClick={() => navigate("/transactions?modal=new")}>
            Add transaction
          </button>
        </div>
      </main>
    </div>
  );
}





