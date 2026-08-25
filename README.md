<div align="center">

# 💱 Currency Exposure & Hedging Management Platform

### Monitor FX exposure, optimize hedges, and cut foreign-exchange losses

A treasury-grade platform that gives finance teams a real-time, consolidated view of their foreign-currency risk — and the tools to hedge it intelligently.

<br>

<img src="https://img.shields.io/badge/Status-In_Progress-orange?style=for-the-badge" alt="Status: In Progress">
<img src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
<img src="https://img.shields.io/badge/Spring_Boot-4.0.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4.0.2">
<img src="https://img.shields.io/badge/JavaFX-21-1283C3?style=for-the-badge&logo=openjdk&logoColor=white" alt="JavaFX 21">
<img src="https://img.shields.io/badge/H2_Database-09476B?style=for-the-badge&logo=databricks&logoColor=white" alt="H2 Database">
<img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License: MIT">

</div>

> 🚧 **This project is under active development.** The sections below describe the full product vision and roadmap. See [**Current Status**](#-current-status) for what's already implemented.

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Who It's For](#-who-its-for)
- [Roadmap](#-roadmap)
- [Business Logic Examples](#-business-logic-examples)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Current Status](#-current-status)
- [Getting Started](#-getting-started)
- [Project Structure](#-project-structure)
- [License](#-license)

---

## 🎯 Overview

Companies with international operations carry foreign-currency risk on every receivable, payable, and cash balance they hold abroad. When exchange rates move, unhedged exposure turns into real P&L losses.

**This platform solves that** by giving treasury teams a single place to:

- 📊 **See** every foreign-currency position across all entities, in real time
- 🛡️ **Hedge** that exposure optimally with forward contracts and data-driven hedge ratios
- 📈 **Track** live FX rates, volatility, and threshold alerts
- 🧮 **Analyze** P&L attribution, scenarios, and risk metrics for executives and auditors

---

## 👥 Who It's For

| Role | What they get |
|------|---------------|
| **Treasury teams** | Real-time exposure inventory and hedging tools |
| **CFOs** | Consolidated risk dashboards and scenario analysis |
| **Finance controllers** | Hedge-accounting compliance and audit-ready reporting |

---

## 🗺 Roadmap

### Phase 1 — 📦 Exposure Tracking
- Real-time inventory of all foreign-currency positions (receivables, payables, cash)
- Breakdowns by currency, subsidiary, customer, and supplier
- Historical exposure trends
- Exposure consolidation across entities
- Multi-currency P&L impact analysis

### Phase 2 — 🛡️ Hedging Management
- Calculate optimal hedge ratios (how much to hedge)
- Forward-contract tracking and management
- Hedge effectiveness testing (accounting compliance)
- Rebalancing recommendations
- Cost-benefit analysis of hedging strategies

### Phase 3 — 📈 FX Rate Management
- Real-time FX rate feeds from multiple providers
- Rate monitoring and threshold alerts
- Spot vs. forward rate analysis
- Rate volatility tracking
- Historical rate charting

### Phase 4 — 🔬 Advanced Analytics & Reporting
- FX P&L attribution (which transactions caused losses)
- Hedge-effectiveness reporting (for auditors)
- Scenario analysis (*what if EUR drops 5%?*)
- Risk metrics (Value at Risk, duration)
- Consolidated exposure dashboards for executives
- Tax reporting for hedging transactions

---

## 🧠 Business Logic Examples

```text
• Consolidate a $500K payable in EUR and a $300K receivable in GBP → net exposure
• Hedge with a forward contract at 1.08 → calculate the locked-in FX rate
• Run scenarios: What happens if EUR/USD moves to 1.05, 1.10, or 1.15?
• Recommend a hedge ratio: should we hedge 50%, 75%, or 100%?
• Flag mismatches: receivable in EUR but costs in USD → natural-hedge opportunity
```

---

## 🏗 Architecture

### Current architecture

What runs today is a **single Spring Boot process that also hosts a JavaFX desktop UI**. The UI controllers call the Spring service layer in-process (no HTTP hop), and the same services are also exposed over a small REST API.

```
  JavaFX desktop UI (FXML + CSS)
        │  in-process calls (no HTTP)
        ▼
  Spring service layer ──► Spring Data JPA ──► H2 (embedded file DB)
        ├─► JWT auth (Spring Security)
        └─► ExchangeRateService ──► exchangerate-api.com  (cached ~hourly)
        ▲
  REST API (Spring MVC):  /api/auth/*,  /api/exchange-rates/*
```

### Target architecture (vision)

The full platform aims for the components below. **None of these are implemented yet** — they describe the roadmap, not the current build.

- **FX rate engine** — integrates multiple market-data providers (e.g. Bloomberg, Reuters, OANDA)
- **Position aggregation** — pulls and normalizes positions across multiple source systems
- **Hedge accounting calculations** — IAS 39 / ASC 815 compliance
- **Scenario & sensitivity engine** — stress-tests exposure against rate movements
- **Real-time alerting system** — threshold and volatility triggers
- **Multi-currency consolidation** — entity-level roll-up into a group view

```
  Market data ──► FX Rate Engine ──► Redis (live rates) ──► WebSocket ──► Dashboards
  providers          │                                                       (React +
                     ▼                                                        D3 / Recharts)
  Source systems ──► Position Aggregation ──► PostgreSQL (time-series) ◄── Scenario Engine
                                                   ▲
                                          Kafka (streaming FX data)
```

---

## 🛠 Tech Stack

**In use today:**

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 4.0.2 |
| **Desktop UI** | JavaFX 21 (FXML + CSS) |
| **Database** | H2 (embedded, file-based) |
| **Auth** | JWT-based authentication (jjwt 0.12) + Spring Security |
| **Live rates** | exchangerate-api.com via `RestTemplate`, cached with Spring Cache |

**Planned (roadmap):**

| Layer | Technology |
|-------|-----------|
| **Database** | PostgreSQL (time-series data) |
| **Caching / live rates** | Redis |
| **Streaming** | Apache Kafka (FX data pipelines) |
| **Web frontend** | React with D3 / Recharts |
| **Real-time** | WebSockets for live updates |

> A PostgreSQL driver is already bundled in the build for the eventual server deployment, but the app runs on H2 by default.

---

## ✅ Current Status

The foundational layer is in place. Implemented so far:

- 🔐 **Authentication & accounts** — register, login, JWT access + refresh tokens, logout, change password, email verification, and profile retrieval
- 👤 **User domain** — user entity with KYC status, daily exchange limits, and 2FA fields
- 👛 **Wallet & transaction model** — multi-currency wallets and a transaction ledger (exchange, deposit, withdrawal, transfer) with fee and exchange-rate tracking
- 💸 **Transactions** — a user-scoped transaction API (`/api/transactions`) and a transaction-history view in the dashboard
- 📊 **Portfolio statistics** — net exposure per currency (netting & aggregation) valued in a home currency via live rates, exposed at `/api/statistics/portfolio` with a statistics dashboard tab showing total portfolio value and each currency's share
- 🛡️ **Security layer** — Spring Security with a JWT authentication filter and token provider
- 💱 **Live exchange rates** — external rate-feed integration (exchangerate-api.com) with caching and a `/api/exchange-rates` API
- 📉 **Historical rates** — periodic rate snapshots persisted to a queryable time series (`/api/exchange-rates/{base}/history/{quote}`)
- 🔔 **Rate alerts** — user-defined ABOVE/BELOW thresholds on any pair, evaluated on a schedule against live quotes and fired once until re-armed (`/api/alerts`)
- 🛡️ **Hedging** — forward and option instruments, optionally linked to the exposure they cover, marked to market with unrealised P&L, hedge ratio, and a dollar-offset effectiveness measure (`/api/hedges`)
- 🔬 **Advanced analytics** — scenario analysis, a standard stress-test battery, FX P&L attribution, and historical-simulation Value at Risk (`/api/analytics`)
- 🖥️ **Desktop UI** — a JavaFX front-end (login, register, dashboard with wallets, exposures, hedges, alerts, transactions, statistics, and analytics tabs) that calls the service layer in-process
- 🗄️ **Persistence** — Spring Data JPA repositories over an embedded H2 database (a PostgreSQL driver is bundled for a future server deployment)

Exposure tracking, FX rate integration, hedging management, and the advanced-analytics phase from the [roadmap](#-roadmap) have all landed. Reporting & polish (executive dashboards, PDF/Excel export, compliance reporting) is the next milestone.

> 📝 Note: the current data model began as a wallet/exchange foundation; the entities will evolve toward the treasury-exposure model (positions, hedges, forward contracts) as Phase 1 lands.

### Implemented API — `/api/auth`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/register` | Create a new account |
| `POST` | `/login` | Authenticate and receive tokens |
| `POST` | `/refresh-token` | Refresh an access token |
| `GET` | `/profile` | Get the current user's profile |
| `POST` | `/change-password` | Change password |
| `GET` | `/verify-email` | Verify email via token |
| `POST` | `/logout` | Log out |
| `GET` | `/health` | Service health check |

### Implemented API — `/api/exchange-rates`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/{base}` | Latest exchange rates for a base currency (cached) |

### Implemented API — `/api/transactions`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | List the current user's transactions (optional `?type=` filter) |
| `GET` | `/{id}` | Get one of the current user's transactions |
| `POST` | `/` | Create a transaction |

### Implemented API — `/api/statistics`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/portfolio` | Net exposure per currency and total portfolio value, valued in `?home=` (defaults to USD) |

### Implemented API — `/api/exposures`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | List the caller's exposures (optional `?type=` filter) |
| `GET` | `/{id}` | Get one of the caller's exposures |
| `POST` | `/` | Book a new exposure |
| `PUT` | `/{id}/status` | Update an exposure's lifecycle status (`?status=`) |

### Implemented API — `/api/alerts`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | List the caller's rate alerts |
| `GET` | `/{id}` | Get one of the caller's alerts |
| `POST` | `/` | Create an ABOVE/BELOW threshold alert on a pair |
| `POST` | `/{id}/rearm` | Re-arm a triggered alert |
| `DELETE` | `/{id}` | Delete an alert |

### Implemented API — `/api/hedges`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | List the caller's hedges, each marked to market (optional `?exposureId=`) |
| `GET` | `/{id}` | Get one hedge with its valuation |
| `POST` | `/` | Book a forward or option, optionally linked to an exposure |
| `PUT` | `/{id}/status` | Update a hedge's status (`?status=`) |
| `DELETE` | `/{id}` | Delete a hedge |

### Implemented API — `/api/analytics`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/scenario` | Revalue the portfolio under a supplied set of rate shocks |
| `GET` | `/stress` | Run the standard adverse stress-test battery (`?home=`) |
| `GET` | `/attribution` | FX P&L attribution over `?lookbackDays=` from stored rate history |
| `GET` | `/var` | Historical-simulation Value at Risk (`?home=&confidence=&lookbackDays=`) |

> All `/api/transactions`, `/api/statistics`, `/api/exposures`, `/api/alerts`, `/api/hedges`, and `/api/analytics` endpoints are scoped to the authenticated user — a user can only ever see their own data.

---

## 🚀 Getting Started

### Prerequisites

- **Java 21+** and **Maven**
- No database setup required — the app uses an embedded **H2** file database by default (stored under `~/.fx-monitor/`). PostgreSQL is optional and only needed for a future server deployment.

### Run the desktop app

```bash
git clone https://github.com/Markkndr/fx-monitor.git
cd fx-monitor/fx-monitor

# Launch the JavaFX desktop UI (also boots the Spring context)
mvn javafx:run
```

Before running, set a signing key via the `JWT_SECRET` environment variable (≥ 256 bits for HS512). If unset, a clearly-labelled insecure dev fallback is used so local runs work out of the box:

```bash
export JWT_SECRET="your-long-random-secret-at-least-256-bits"
export FOREX_API_KEY="optional-exchange-rate-api-key"
```

> 🔐 Keep secrets out of version control — always provide `JWT_SECRET` via the environment in any real deployment.

---

## 📂 Project Structure

```
currency-exchange-platform/
├── fx-monitor/
│   └── src/main/
│       ├── java/com/currencyexchange/
│       │   ├── config/         # Security & RestTemplate configuration
│       │   ├── controller/     # REST controllers (Auth, ExchangeRate, Transaction,
│       │   │                   #   Statistics, Exposure, Alert, Hedge, Analytics)
│       │   ├── dto/            # Request/response DTOs (auth, exchange, transactions,
│       │   │                   #   statistics, exposures, alerts, hedges, analytics)
│       │   ├── entity/         # User, Wallet, Transaction, Exposure, RateSnapshot,
│       │   │                   #   RateAlert, Hedge
│       │   ├── exception/      # Domain-specific exceptions
│       │   ├── repository/     # JPA repositories
│       │   ├── security/       # JWT filter & token provider
│       │   ├── service/        # Auth, user, exchange-rate, transaction, portfolio-statistics,
│       │   │                   #   exposure, rate-snapshot, rate-alert, hedge, scenario-analysis
│       │   │                   #   & risk-metrics services
│       │   └── ui/             # JavaFX app, controllers & view helpers
│       └── resources/
│           ├── fxml/           # JavaFX view layouts
│           ├── css/            # UI styles
│           └── application.yml # App configuration
└── LICENSE
```

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

Building safer FX, one hedge at a time. 🛡️📉

</div>
