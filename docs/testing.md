# EarnIt Kids — Testing Guide

<a name="top"></a>

## 📚 Table of Contents

- [EarnIt Kids — Testing Guide](#earnit-kids--testing-guide)
  - [📚 Table of Contents](#-table-of-contents)
  - [🧭 Overview](#-overview)
  - [⚙️ Backend Testing (Quarkus)](#️-backend-testing-quarkus)
    - [Prerequisites](#prerequisites)
    - [Commands](#commands)
    - [Test Structure](#test-structure)
    - [Test Resources](#test-resources)
    - [Key Patterns](#key-patterns)
  - [⚙️ Web Testing (SvelteKit)](#️-web-testing-sveltekit)
    - [Commands](#commands-1)
    - [Test Structure](#test-structure-1)
    - [Unit Testing with Vitest](#unit-testing-with-vitest)
  - [🧪 E2E Testing (Playwright)](#-e2e-testing-playwright)
    - [Prerequisites](#prerequisites-1)
    - [Commands](#commands-2)
    - [Test Structure](#test-structure-2)
    - [Playwright Configuration](#playwright-configuration)
  - [📊 Coverage Expectations](#-coverage-expectations)
  - [🚦 Pre-Merge Verification Gates](#-pre-merge-verification-gates)
  - [⚠️ Known Failure Modes](#️-known-failure-modes)
  - [📝 Adding Tests](#-adding-tests)
    - [Backend](#backend)
    - [Web — Unit](#web--unit)
    - [Web — E2E](#web--e2e)

---

## 🧭 Overview

The EarnIt Kids monorepo has two testing surfaces:

| App | Framework | Test Types | Runner |
| --- | --- | --- | --- |
| `apps/backend` | JUnit 5 + REST Assured | Unit + Integration | Maven Surefire + Failsafe |
| `apps/web` | Vitest + Playwright | Unit + E2E | Vitest / Playwright |

[↩ Back to toc](#table-of-contents)

## ⚙️ Backend Testing (Quarkus)

### Prerequisites

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/backend
```

### Commands

```bash
# Full build — unit tests + integration tests + static analysis (Checkstyle + SpotBugs)
./mvnw verify

# Unit tests only
./mvnw test

# Integration tests only
./mvnw failsafe:integration-test

# Skip tests for a fast compile check
./mvnw compile -DskipTests
```

### Test Structure

```
src/test/java/com/sashplatonov/earnit/kids/
├── resource/     — REST endpoint tests (JAX-RS + REST Assured)
├── service/      — Service layer unit tests
└── repository/   — Repository / persistence tests
```

### Test Resources

- H2 in-memory database for most tests (configured in `src/test/resources/application.properties`)
- Flyway test migrations in `src/test/resources/db/migration/`
- Test-specific `baseData.json` for seed data

### Key Patterns

- `@QuarkusIntegrationTest` for full-stack integration tests
- `@TestProfile` for environment-specific test configurations
- `@WireMock` or mocked services for external dependency isolation

[↩ Back to toc](#table-of-contents)

## ⚙️ Web Testing (SvelteKit)

### Commands

```bash
cd apps/web

# Lint only
npm run lint

# Unit tests (Vitest)
npm run test

# Unit tests with coverage report
npm run test:coverage

# Build (verifies production compilation)
npm run build

# Preview production build
npm run preview
```

### Test Structure

```
tests/
├── unit/          — Vitest unit tests (stores, services, view models)
└── e2e/           — Playwright E2E tests (browser automation)
```

### Unit Testing with Vitest

Unit tests cover:

- **Stores** — state management logic (`src/lib/stores/`)
- **Services** — API wrappers, formatters, bootstrap logic (`src/lib/services/`)
- **View models** — analytics, request details, history details
- **i18n** — locale resolution, formatters, translation lookups

Example:

```bash
# Run a specific test file
npx vitest run tests/unit/analyticsViewModel.test.ts

# Watch mode for development
npx vitest
```

[↩ Back to toc](#table-of-contents)

## 🧪 E2E Testing (Playwright)

### Prerequisites

```bash
cd apps/web

# Install Playwright browsers (first time only or after update)
npx playwright install chromium
```

### Commands

```bash
# Full E2E run
npm run test:e2e

# Run specific E2E test file
npx playwright test tests/e2e/auth.spec.ts

# Run with UI mode (interactive debug)
npx playwright test --ui

# Run with visible browser
npx playwright test --headed
```

### Test Structure

```
tests/e2e/
├── auth.spec.ts          — Login, registration, logout flows
├── child-shop.spec.ts    — Child shop purchase and request flows
├── analytics.spec.ts     — Analytics section and daily quest
├── app-sections.spec.ts  — Core app sections (tasks, shop, requests, history)
├── helpers.ts            — Shared test utilities and page objects
└── ...
```

### Playwright Configuration

- Config: `apps/web/playwright.config.ts`
- Default: `chromium` only, headed mode on CI, headless locally
- Base URL: `http://localhost:4173` (preview server)
- Web server: auto-starts via `npm run preview` command

[↩ Back to toc](#table-of-contents)

## 📊 Coverage Expectations

| Area | Minimum | Measured By |
| --- | --- | --- |
| Backend unit tests | 80% line coverage | JaCoCo (`target/jacoco.exec`) |
| Backend integration | All public endpoints tested | Failsafe + REST Assured |
| Web unit tests | 80% line coverage | Vitest (`--coverage`) |
| Web lint | Zero errors | ESLint |
| E2E tests | Core flows covered | Playwright |

**Report locations:**

- Backend: `apps/backend/target/site/jacoco/index.html`
- Web: `apps/web/coverage/` (after `npm run test:coverage`)
- E2E: `apps/web/test-results/` (after `npm run test:e2e`)

[↩ Back to toc](#table-of-contents)

## 🚦 Pre-Merge Verification Gates

Minimum required before any merge:

| Gate | Command | Required For |
| --- | --- | --- |
| Backend full build | `./mvnw verify` | ✅ Any change |
| Web lint | `npm run lint` | ✅ Any web change |
| Web unit tests | `npm run test` | ✅ Any web change |
| Web build | `npm run build` | ✅ Any web change |
| Web E2E | `npm run test:e2e` | ⚠️ UI changes only |
| Docker config check | `docker compose config` | ⚠️ Compose changes |

[↩ Back to toc](#table-of-contents)

## ⚠️ Known Failure Modes

| Symptom | Likely Cause | Fix |
| --- | --- | --- |
| `test` passes but `verify` fails | Static analysis (Checkstyle/SpotBugs) only runs in `verify` | Always use `verify` |
| E2E test times out | Preview server not running or port conflict | Kill existing preview: `kill $(lsof -t -i:4173)` |
| E2E test can't find element | UI change without test update | Check `tests/e2e/helpers.ts` for selectors |
| H2 migration mismatch | Test migrations out of sync with PostgreSQL | Update both `src/test/resources/db/migration/` and `src/main/resources/db/migration/` |
| JaCoCo report missing | `quarkus-jacoco` not configured in `pom.xml` | Verify `argLine` uses `@{argLine}` pattern |
| Browser-visible API drift | Normalization layer (`serverContract.ts`) skipped | Re-run E2E after backend DTO changes |
| Preview-only failure | Stale build assets or port conflict | Rebuild: `npm run build && npm run preview` |

[↩ Back to toc](#table-of-contents)

## 📝 Adding Tests

### Backend

1. Place unit tests in the matching package under `src/test/java/...`
2. Use `@QuarkusTest` for CDI-aware tests, `@QuarkusIntegrationTest` for full-stack
3. Follow the existing pattern in the equivalent service/resource test class

### Web — Unit

1. Create test file in `tests/unit/` with `*.test.ts` extension
2. Import from the source file using the project's path aliases
3. Follow the existing patterns in `tests/unit/`

### Web — E2E

1. Create test file in `tests/e2e/` with `*.spec.ts` extension
2. Use helpers from `tests/e2e/helpers.ts` for common page interactions
3. Run with `--headed` for debugging, `--ui` for interactive development

[↑ Back to top](#top)
