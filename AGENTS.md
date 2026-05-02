<!-- Inherits global rules from /Users/sash/.ai-rules/AGENTS.md -->
# EarnIt Kids — Repository Guidelines

## Project Structure

Monorepo with three active app directories:

```
apps/
├── backend/   — Quarkus 3 + Java 25 REST API (JAX-RS, Hibernate, Flyway)
├── web/       — SvelteKit 2 + TypeScript frontend (adapter-node)
mobile/        — Capacitor packaging around the web runtime
docs/          — Architecture, i18n, migration, and ops docs
```

| App | Role | Key Directories |
| --- | --- | --- |
| `apps/backend` | REST API, auth, business logic, DB migrations | `resource/`, `service/`, `repository/`, `domain/model/`, `dto/`, `config/` |
| `apps/web` | SSR frontend, public pages, app shell, edge proxy | `src/routes/`, `src/lib/components/`, `src/lib/services/`, `src/lib/stores/` |
| `mobile` | Capacitor iOS/Android packaging | `apps-mobile/mobile/`, `assets/` |

## Build, Test & Dev Commands

### Backend (Quarkus)
```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify   # full build + tests + static analysis
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw quarkus:dev  # dev mode hot reload
./mvnw test           # unit tests only
```

### Web (SvelteKit)
```bash
npm run dev           # dev server
npm run build         # production build
npm run preview       # preview production build
npm run lint          # ESLint
npm run test          # Vitest unit tests
npm run test:coverage # unit tests with coverage
npm run test:e2e      # Playwright E2E tests
```

### Full Stack (Docker Compose)
```bash
docker compose --profile db up -d --build                # JVM backend
docker compose -f docker-compose.native.yml --profile db up -d --build  # native backend
docker compose down
```

## Coding Style & Naming

- **TS/Svelte**: `PascalCase.svelte`, `camelCase.ts`, `kebab-case.css`. One component per file. Stores export named functions.
- **Java**: no nested classes/interfaces (each top-level type in own file). No Javadoc — use `EXPLAIN:`/`FIXME:` prefix. No `.bak` files. Layer order: `resource/` → `service/` → `repository/` → `domain/model/`. DTO records for payloads. Checkstyle + SpotBugs in `verify`.
- **DB/SQL**: `snake_case` columns. Migrations: `NNN_description.sql`, never edit merged files. Parameterized queries only. All queries filter by `family_id` from JWT.
- **Shell/Docker**: 4-space indent. Compose vars from `.env`, never hardcode.

## Testing Gates

| Gate | Required |
| --- | --- |
| Backend `./mvnw verify` | ✅ Always |
| Web `npm run lint` | ✅ Always |
| Web `npm run test` | ✅ Always |
| Web `npm run build` | ✅ Always |
| Web E2E (`npm run test:e2e`) | ⚠️ When UI changes |
| `docker compose config` | ⚠️ When compose changes |

**Failure modes:** `test` without `verify` misses static analysis. API drift → `serverContract.ts` normalization skipped. Child switch bugs → scoped data not reloaded. After migrations, validate both PostgreSQL and H2 test baseline. Docker rebuild drift → run `docker compose config` first.

## Commit & PR

**Format:** [Conventional Commits](https://www.conventionalcommits.org/):
```
<type>(<scope>): <short description>
```
Types: `feat|fix|refactor|test|docs|chore|style|perf`. Scopes: `backend|web|mobile|docker|i18n|docs`.

**PR checklist:** `mvnw verify` passes, `npm run lint && npm run test && npm run build` passes, E2E if UI changed, no `.bak`/debug files, migrations sequential, new env vars in `.env.example`.

## Docker Workflow

| File | Backend Mode | Use Case |
| --- | --- | --- |
| `docker-compose.yml` | JVM (`Dockerfile.jvm`) | Day-to-day local dev |
| `docker-compose.native.yml` | Native (`Dockerfile`) | Packaging validation |

Networks: `edge` (web↔backend), `backend` (backend↔db), `dokploy-ipv6` (external). Run `docker compose config` before rebuild to catch env drift.

## Key Paths

| Purpose | Path |
| --- | --- |
| Root env template | `.env.example` |
| Backend source | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/` |
| Backend migrations | `apps/backend/src/main/resources/db/migration/` |
| Web routes | `apps/web/src/routes/` |
| Web components | `apps/web/src/lib/components/` |
| Web services | `apps/web/src/lib/services/` |
| Web i18n | `apps/web/src/lib/i18n/` |
| Architecture docs | `docs/architecture.md` |
| Backend ARCHITECTURE | `apps/backend/docs/ARCHITECTURE.md` |
| Web ARCHITECTURE | `apps/web/docs/ARCHITECTURE.md` |
