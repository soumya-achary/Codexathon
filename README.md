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

## Azure Deployment Guide

Recommended Azure setup:
- Frontend: Azure Static Web Apps
- Backend: Azure App Service (Java 21)
- Database: Azure Database for PostgreSQL Flexible Server

### 1. Create Azure PostgreSQL
Create a PostgreSQL Flexible Server and a database named `Finance`.

Collect these values:
- Hostname
- Database name
- Username
- Password
- SSL mode requirements from Azure

Recommended JDBC URL:

```text
jdbc:postgresql://YOUR_SERVER.postgres.database.azure.com:5432/Finance?sslmode=require
```

### 2. Deploy the Backend to Azure App Service
Create a Linux Web App for Java 21 and deploy the `backend` module.

From `backend` build the jar:

```powershell
mvn clean package
```

In Azure App Service, configure these application settings:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_SERVER.postgres.database.azure.com:5432/Finance?sslmode=require
SPRING_DATASOURCE_USERNAME=YOUR_USERNAME
SPRING_DATASOURCE_PASSWORD=YOUR_PASSWORD
APP_FRONTEND_BASE_URL=https://YOUR_FRONTEND_DOMAIN
APP_JWT_SECRET=replace-with-a-very-long-random-secret
APP_SECURITY_REQUIRE_HTTPS=true
APP_SECURITY_ALLOWED_ORIGINS=https://YOUR_FRONTEND_DOMAIN
```

If you use a custom frontend domain, put that exact HTTPS origin in both:
- `APP_FRONTEND_BASE_URL`
- `APP_SECURITY_ALLOWED_ORIGINS`

### 3. Deploy the Frontend to Azure Static Web Apps
Deploy the `frontend` folder and set this environment variable for the build:

```text
VITE_API_BASE_URL=https://YOUR_BACKEND_APP.azurewebsites.net/api
```

Build settings:
- App location: `frontend`
- Output location: `dist`

Important:
- Azure Static Web Apps does not automatically proxy `/api` to an external App Service.
- If `VITE_API_BASE_URL` is missing, the frontend falls back to relative `/api`, which causes requests like `POST /api/auth/register` to hit the static site instead of your Spring backend.
- That misconfiguration commonly shows up as `405 Method Not Allowed` with `allow: GET, HEAD, OPTIONS` on the `azurestaticapps.net` domain.

### 4. Verify the Connection
After both apps are live:
- Open the frontend URL
- Register a user
- Confirm requests go to `https://YOUR_BACKEND_APP.azurewebsites.net/api/...`
- Confirm password reset links point back to your frontend domain

### 5. Important Notes
- The app now supports environment-based API and CORS configuration for Azure deployment.
- Do not keep the default JWT secret in production.
- Do not use the local PostgreSQL credentials from this README in production.
- The backup PowerShell script is Windows-oriented and is not suitable as-is for a Linux App Service deployment.

## Notes
- Password reset works through a generated reset link in local development.
- The recurring transaction scheduler runs hourly and creates due transactions automatically.
- Backend tests are included for auth reset and delete safety; frontend structure can still be split further later if you want a cleaner codebase.
