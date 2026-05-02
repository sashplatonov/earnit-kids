<!-- Inherits global rules from /Users/sash/.ai-rules/AGENTS.md -->
# EarnIt Kids — Repository Guidelines

<a name="top"></a>

## 📚 Table of Contents

- [🧭 Project Structure](#-project-structure)
- [⚙️ Build, Test & Dev Commands](#️-build-test--dev-commands)
- [🎨 Coding Style & Naming](#-coding-style--naming)
- [🧪 Testing Guidelines](#-testing-guidelines)
- [📝 Commit & PR Guidelines](#-commit--pr-guidelines)
- [🐳 Docker Workflow](#-docker-workflow)
- [📖 Key Files & Paths](#-key-files--paths)

---

## 🧭 Project Structure

The EarnIt Kids monorepo has three active application directories:

```
apps/
├── backend/   — Quarkus 3 + Java 25 REST API (JAX-RS, Hibernate, Flyway)
├── web/       — SvelteKit 2 + TypeScript frontend (adapter-node)
mobile/        — Capacitor packaging around the web runtime
docs/          — Architecture, i18n, migration, and ops docs
```

**Module boundaries:**

| App | Role | Key Directories |
| --- | --- | --- |
| `apps/backend` | REST API, auth, business logic, DB migrations | `resource/`, `service/`, `repository/`, `domain/model/`, `dto/`, `config/` |
| `apps/web` | SSR frontend, public pages, app shell, edge proxy | `src/routes/`, `src/lib/components/`, `src/lib/services/`, `src/lib/stores/` |
| `mobile` | Capacitor iOS/Android packaging | `apps-mobile/mobile/`, `assets/` |

[↩ Back to toc](#table-of-contents)

## ⚙️ Build, Test & Dev Commands

### Backend (Quarkus)

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/backend

./mvnw verify          # Full build + tests + static analysis
./mvnw quarkus:dev     # Dev mode with hot reload
./mvnw test            # Unit tests only (verify also runs integration)
```

### Web (SvelteKit)

```bash
cd apps/web

npm install            # Install dependencies
npm run dev            # Dev server
npm run build          # Production build
npm run preview        # Preview production build
npm run lint           # ESLint
npm run test           # Vitest unit tests
npm run test:coverage  # Unit tests with coverage
npm run test:e2e       # Playwright E2E tests
```

### Full Stack (Docker Compose)

```bash
docker compose --profile db up -d --build    # JVM backend
docker compose -f docker-compose.native.yml --profile db up -d --build  # Native backend
docker compose down                          # Stop
```

[↩ Back to toc](#table-of-contents)

## 🎨 Coding Style & Naming

### TypeScript / Svelte

- Files: `PascalCase.svelte`, `camelCase.ts`, `kebab-case.css`
- Components: PascalCase, one component per file
- Stores: `camelCase`, exports named store functions
- Services: `camelCase.ts` in `src/lib/services/`

### Java

- **No nested classes or interfaces** — each top-level type in its own file
- **No Javadoc** (`/** ... */`) — use `EXPLAIN:` or `FIXME:` prefix for inline comments only
- No `.bak` files — add `*.bak` to `.gitignore`
- Layer order: `resource/` → `service/` → `repository/` → `domain/model/`
- DTO records for request/response payloads
- Checkstyle and SpotBugs run in `verify` phase

### Database / SQL

- Columns: `snake_case` (`family_id`, `child_id`)
- Migration files: `NNN_description.sql` (sequential, never edit merged)
- Parameterized queries only — no string interpolation
- All queries filter by `family_id` from JWT (data isolation)

### Shell / Docker

- 4-space indentation in shell scripts
- Compose vars from `.env` — never hardcode ports or secrets

[↩ Back to toc](#table-of-contents)

## 🧪 Testing Guidelines

Minimum verification gates before any merge:

| Gate | Command | Required |
| --- | --- | --- |
| Backend unit + integration | `./mvnw verify` | ✅ Always |
| Web lint | `npm run lint` | ✅ Always |
| Web unit tests | `npm run test` | ✅ Always |
| Web build | `npm run build` | ✅ Always |
| Web E2E (Playwright) | `npm run test:e2e` | ⚠️ When UI changes |
| Docker config check | `docker compose config` | ⚠️ When compose changes |

**Failure modes to watch:**

- `test` passing without `verify` is insufficient — static analysis runs later.
- Browser-visible API drift usually means the normalization layer was skipped in `serverContract.ts`.
- Child switching bugs usually mean scoped data was not reloaded from the backend.
- After migration work, validate both PostgreSQL migrations and the H2 test baseline.
- Docker rebuild drift: run `docker compose config` before rebuilding.

[↩ Back to toc](#table-of-contents)

## 📝 Commit & PR Guidelines

### Commit Format

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>

<optional body — what and why, not how>
```

**Types:** `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`, `perf`

**Scopes:** `backend`, `web`, `mobile`, `docker`, `i18n`, `docs`

**Examples:**

```
feat(backend): add daily quest analytics endpoint
fix(web): restore child-switch store reload
docs(i18n): update contract with failure modes
```

### PR Checklist

- [ ] `mvnw verify` passes (backend)
- [ ] `npm run lint && npm run test && npm run build` passes (web)
- [ ] E2E tests pass if UI changed
- [ ] No `.bak` or debug files committed
- [ ] Migration files are sequential
- [ ] New env vars documented in `.env.example`

[↩ Back to toc](#table-of-contents)

## 🐳 Docker Workflow

Two Compose files are maintained:

| File | Backend Mode | Use Case |
| --- | --- | --- |
| `docker-compose.yml` | JVM (`Dockerfile.jvm`) | Day-to-day local development |
| `docker-compose.native.yml` | Native (`Dockerfile`) | Packaging validation |

**Key networking:** Three networks — `edge` (web ↔ backend), `backend` (backend ↔ db), `dokploy-ipv6` (external, for Dokploy).

**Verification tip:** Run `docker compose config` or `docker compose -f docker-compose.native.yml config` before rebuilding to catch env drift.

[↩ Back to toc](#table-of-contents)

## 📖 Key Files & Paths

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

[↑ Back to top](#top)
