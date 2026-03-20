# Personal Finance Tracker

Full-stack implementation based on the provided product spec.

## Stack
- Frontend: React + TypeScript + Vite + TanStack Query + Zustand + Recharts
- Backend: Spring Boot 3 + Maven + Spring Security + Spring Data JPA
- Database: PostgreSQL

## Database
Create the PostgreSQL database first:

```sql
CREATE DATABASE "Finance";
```

Configured credentials:
- Database: `Finance`
- Username: `postgres`
- Password: `1234`

Backend config lives in `backend/src/main/resources/application.yml`.

## Run Backend
From `backend`:

```powershell
..\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run
```

Backend will start on `http://localhost:8080`.

## Run Frontend
From `frontend`:

```powershell
npm install
npm run dev
```

Frontend will start on `http://localhost:5173`.

## Implemented Modules
- JWT auth with register, login, refresh, forgot-password, and reset-password flow
- Dashboard summary with cards, charts, budget alert banners, recurring sections, and onboarding hints
- Accounts CRUD + transfers
- Categories CRUD with linked-data delete protection
- Transactions CRUD, filters, search, and modal add/edit flow
- Budgets with duplicate-last-month support and threshold alerting at 80%, 100%, and 120%
- Goals with contribute and withdraw flows
- Recurring transactions + scheduler
- Reports with filters plus CSV and PDF export
- Settings page, global topbar search, global date range, notifications area, and profile menu
- Audit events / telemetry for major user actions
- Responsive web UI with dark and light mode
- Login rate limiting and optional HTTPS-only enforcement

## Operational Features
- Daily backup script: `scripts/backup-finance.ps1`
- HTTPS enforcement toggle: set `app.security.require-https: true` in `backend/src/main/resources/application.yml`
- Backup script path is documented under `app.backup.script-path`

To schedule the backup daily on Windows, create a Task Scheduler job that runs:

```powershell
powershell -ExecutionPolicy Bypass -File D:\New folder (2)\scripts\backup-finance.ps1
```

Make sure `pg_dump` is available on your PATH.

## Build Verification
- Backend: `mvn -DskipTests package` passed
- Frontend: `npm run build` passed

## Notes
- Password reset works through a generated reset link in local development.
- The recurring transaction scheduler runs hourly and creates due transactions automatically.
- Backend tests are included for auth reset and delete safety; frontend structure can still be split further later if you want a cleaner codebase.