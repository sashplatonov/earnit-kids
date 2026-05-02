# EarnIt Kids

<a name="top"></a>

## Table of Contents
- [🧭 Overview](#-overview)
- [⚙️ Tech Stack](#️-tech-stack)
- [🚀 Quick Start](#-quick-start)
- [📁 Project Structure](#-project-structure)
- [🔐 Environment Variables](#-environment-variables)
- [🛠️ Local Commands](#️-local-commands)
- [🐳 Docker Modes](#-docker-modes)
- [📚 Detailed Docs](#-detailed-docs)

## 🧭 Overview

EarnIt Kids is a full-stack family coin shop for parents and children.

- `apps/web/` serves the SvelteKit web experience, public pages, same-origin proxy endpoints, and health checks.
- `apps/backend/` serves the Quarkus API, authentication, family data flows, admin tooling, and Flyway migrations.
- `mobile/` contains Capacitor packaging and mobile platform assets.

The current production path is SvelteKit + Quarkus. Legacy pre-SvelteKit frontend code is no longer part of the active runtime.

[↩ Back to toc](#table-of-contents)

## ⚙️ Tech Stack

- Frontend: SvelteKit 2, Vite, TypeScript, Vitest, Playwright, adapter-node
- Backend: Quarkus 3, Java 25, JAX-RS, Hibernate ORM, Flyway, SmallRye OpenAPI
- Database: PostgreSQL 18 for local Docker, H2 for selected tests
- Delivery: Docker Compose with separate JVM and native backend modes

[↩ Back to toc](#table-of-contents)

## 🚀 Quick Start

Run the JVM-based local stack from the repository root:

```bash
docker compose --profile db up -d --build
```

Run the native-image local stack from the repository root:

```bash
docker compose -f docker-compose.native.yml --profile db up -d --build
```

Stop the stacks:

```bash
docker compose down
docker compose -f docker-compose.native.yml down
```

Assumptions:

- Root `.env` contains local-safe defaults.
- If `3000` or `5432` are already taken, override `WEB_PORT` or `DB_HOST_PORT` at launch time.
- Default `docker-compose.yml` is the JVM stack. `docker-compose.native.yml` is the native stack.

[↩ Back to toc](#table-of-contents)

## 📁 Project Structure

```text
.
├── apps/
│   ├── backend/
│   │   ├── config/
│   │   ├── scripts/
│   │   └── src/
│   └── web/
│       ├── data/
│       ├── scripts/
│       ├── src/
│       ├── static/
│       └── tests/
├── docs/
├── mobile/
├── docker-compose.yml
└── docker-compose.native.yml
```

- `apps/backend/src/main/java/`: REST resources, services, repositories, entities, DTOs, config
- `apps/backend/src/main/resources/db/migration/`: Flyway migrations for PostgreSQL
- `apps/web/src/routes/`: SvelteKit routes and edge endpoints
- `apps/web/src/lib/`: components, stores, services, server helpers, shared types
- `apps/web/tests/`: unit and E2E test coverage

[↩ Back to toc](#table-of-contents)

## 🔐 Environment Variables

The full reference lives in root `.env.example`. The table below lists the variables most developers touch first.

| Variable | Scope | Example | Purpose |
| --- | --- | --- | --- |
| `APP_URL` | Web + Compose | `http://localhost:3000` | Public origin used by the web edge and backend CORS |
| `WEB_PORT` | Compose | `3000` | Host port published for the web service |
| `WEB_INTERNAL_PORT` | Web + Compose | `3000` | Internal Node port inside the web container |
| `BACKEND_INTERNAL_PORT` | Backend + Compose | `8080` | Internal Quarkus HTTP port |
| `JWT_SECRET` | Backend | `local-dev-secret-change-in-prod` | Compatibility JWT signing secret |
| `DB_HOST_PORT` | Compose | `5432` | Host port published for PostgreSQL |
| `DB_NAME` | Backend + DB | `earnit_kids` | Database name |
| `DB_USER` | Backend + DB | `postgres` | Database username |
| `DB_PASSWORD` | Backend + DB | `change-me` | Database password |
| `DATABASE_URL` | Backend | `jdbc:postgresql://localhost:5432/earnit_kids` | Direct JDBC URL for non-Compose backend runs |
| `SUPER_ADMIN_EMAIL` | Backend bootstrap | `admin@example.com` | Optional super-admin bootstrap account |
| `SUPER_ADMIN_PASSWORD` | Backend bootstrap | `change-me` | Optional super-admin bootstrap password |
| `ENABLE_EMAIL_VERIFICATION` | Backend feature flag | `false` | Toggle email verification flow |
| `ENABLE_PASSWORD_RECOVERY` | Backend feature flag | `false` | Toggle forgot/reset password flow |
| `JAVA_XMX` | Native Docker build | `2500m` | Native image builder memory cap |

Telegram backup credentials and schedule are configured from the super-admin panel, not from the env examples.

[↩ Back to toc](#table-of-contents)

## 🛠️ Local Commands

Backend validation:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/backend
./mvnw verify
```

Backend dev mode:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/backend
./mvnw quarkus:dev
```

Web validation:

```bash
cd apps/web
npm install
npm run lint
npm run test
npm run test:coverage
npm run build
```

Useful local URLs:

- Web: `http://localhost:3000`
- Backend health: `http://localhost:8080/q/health`
- Backend OpenAPI: `http://localhost:8080/api/openapi.yaml`
- Backend Swagger UI: `http://localhost:8080/q/swagger-ui`

[↩ Back to toc](#table-of-contents)

## 🐳 Docker Modes

`docker-compose.yml`

- Web: `apps/web/Dockerfile`
- Backend: `apps/backend/Dockerfile.jvm`
- Best for fast local iteration and debugging

`docker-compose.native.yml`

- Web: `apps/web/Dockerfile`
- Backend: `apps/backend/Dockerfile`
- Best for validating native-image packaging

Verification tip:

- After Compose changes, run `docker compose config` or `docker compose -f docker-compose.native.yml config` before rebuilding.

[↩ Back to toc](#table-of-contents)

## 📚 Detailed Docs

- Root system view: [docs/architecture.md](docs/architecture.md)
- Docker operations: [docs/docker-ops.md](docs/docker-ops.md)
- Testing guide: [docs/testing.md](docs/testing.md)
- I18n contract: [docs/i18n-contract.md](docs/i18n-contract.md)
- I18n string inventory: [docs/i18n-string-inventory.md](docs/i18n-string-inventory.md)
- I18n backlog: [docs/i18n-backlog.md](docs/i18n-backlog.md)
- SvelteKit migration backlog: [docs/migration-backlog-sveltekit.md](docs/migration-backlog-sveltekit.md)
- Analytics daily quest backlog: [docs/analytics-daily-quest-backlog.md](docs/analytics-daily-quest-backlog.md)
- Frontend architecture: [apps/web/docs/ARCHITECTURE.md](apps/web/docs/ARCHITECTURE.md)
- Backend architecture: [apps/backend/docs/ARCHITECTURE.md](apps/backend/docs/ARCHITECTURE.md)

[↑ Back to top](#top)
