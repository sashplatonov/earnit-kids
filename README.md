# EarnIt Kids

## Table of Contents
- [🧭 Overview](#-overview)
- [🔧 Local Environment](#-local-environment)
- [📁 Repository Structure](#-repository-structure)
- [☕ Backend Commands](#-backend-commands)
- [🌐 Web Commands](#-web-commands)
- [🐳 Docker Compose](#-docker-compose)
- [📚 Project Docs](#-project-docs)

## 🧭 Overview

EarnIt Kids is a split-stack application with:

- `apps/backend/`: a Quarkus 3 backend targeting Java 25
- `apps/web-svelte/`: the active SvelteKit web edge/runtime used by Docker Compose
- `mobile/`: mobile packaging, Capacitor configuration, and platform assets

The backend now expects Java 25. If your shell defaults to another JDK, export `JAVA_HOME` before running Maven commands.

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
```

[↩ Back to toc](#table-of-contents)

## 🔧 Local Environment

The repository root keeps the shared environment files:

- `.env`: safe local defaults for development
- `.env.example`: copy/reference file for local setup
- `.env.production`: deployment-oriented defaults

Local-friendly values live in `BACKEND_URL` and `DATABASE_URL`, while Docker Compose overrides internal service-to-service URLs for containers.

Default local ports:

- Web edge: `3000`
- Backend: `8080`
- PostgreSQL: `5432`

[↩ Back to toc](#table-of-contents)

## 📁 Repository Structure

- `apps/backend/`: Quarkus REST API, persistence layer, Flyway migrations, Maven tests
- `apps/web-svelte/`: SvelteKit app, Node edge server, blog content, Vitest and Playwright checks
- `mobile/`: Capacitor configuration, mobile assets, mobile-specific README files
- `docs/`: architecture and design documentation
- `docker-compose.yml`: local Docker Compose for native mode
- `docker-compose.jvm.yml`: local Docker Compose for JVM mode

[↩ Back to toc](#table-of-contents)

## ☕ Backend Commands

Run from `apps/backend/`.

Install and validate:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/backend
./mvnw test
```

Start local development mode:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/backend
./mvnw quarkus:dev
```

Useful backend URLs in dev mode:

- Health: `http://localhost:8080/q/health`
- OpenAPI: `http://localhost:8080/q/openapi`
- Swagger UI: `http://localhost:8080/q/swagger-ui`

[↩ Back to toc](#table-of-contents)

## 🌐 Web Commands

Run from `apps/web-svelte/`.

```bash
cd apps/web-svelte
npm install
npm run lint
npm test
npm run build
npm run preview -- --host 127.0.0.1 --port 4174
```

[↩ Back to toc](#table-of-contents)

## 🐳 Docker Compose

Run from the repository root.

Local JVM mode:

```bash
docker compose -f docker-compose.jvm.yml up -d --build
```

Local native mode:

```bash
docker compose up -d --build
```

Stop JVM mode:

```bash
docker compose -f docker-compose.jvm.yml down
```

Stop native mode:

```bash
docker compose down
```

> **Note:** the compose `web` service now builds from `apps/web-svelte/Dockerfile`. `docker-compose.yml` still uses `apps/backend/Dockerfile` for native mode, and `docker-compose.jvm.yml` uses `apps/backend/Dockerfile.jvm` for JVM mode.
> Docker Compose reads the root `.env`, then overrides internal URLs such as `BACKEND_URL` and backend `DATABASE_URL` inside containers.
> If `3000` or `5432` are already occupied locally, override `WEB_PORT` or `DB_HOST_PORT` when running compose instead of changing the container-internal ports.

[↩ Back to toc](#table-of-contents)

## 📚 Project Docs

- See [docs/architecture.md](docs/architecture.md) for backend layering, auth/session flow, and request lifecycle notes.
- See [docs/migration-backlog-sveltekit.md](docs/migration-backlog-sveltekit.md) for the archived migration checklist.
- Backend API documentation is generated from Quarkus OpenAPI annotations at runtime.

[↑ Back to top](#earnit-kids)
