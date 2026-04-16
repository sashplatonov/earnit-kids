# EarnIt Kids

## Table of Contents
- [🧭 Overview](#-overview)
- [📁 Repository Structure](#-repository-structure)
- [☕ Backend Commands](#-backend-commands)
- [🌐 Web Commands](#-web-commands)
- [🐳 Docker Compose](#-docker-compose)
- [📚 Project Docs](#-project-docs)

## 🧭 Overview

EarnIt Kids is a split-stack application with:

- `backend/`: a Quarkus 3 backend targeting Java 25
- `web/`: a Node.js web edge, static assets, and UI tests
- `mobile/`: mobile packaging and Capacitor-related assets

The backend now expects Java 25. If your shell defaults to another JDK, export `JAVA_HOME` before running Maven commands.

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
```

[↩ Back to toc](#table-of-contents)

## 📁 Repository Structure

- `backend/`: Quarkus REST API, persistence layer, Flyway migrations, Maven tests
- `web/`: Node.js server, frontend assets, Playwright/UI/integration tests
- `mobile/`: Capacitor configuration, mobile assets, mobile-specific README files
- `docker-compose.yml`: local multi-service orchestration
- `ARCHITECTURE.md`: high-level system and backend module design

[↩ Back to toc](#table-of-contents)

## ☕ Backend Commands

Run from `backend/`.

Install and validate:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw test
```

Start local development mode:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw quarkus:dev
```

Useful backend URLs in dev mode:

- Health: `http://localhost:8080/q/health`
- OpenAPI: `http://localhost:8080/q/openapi`
- Swagger UI: `http://localhost:8080/q/swagger-ui`

[↩ Back to toc](#table-of-contents)

## 🌐 Web Commands

Run from `web/`.

```bash
npm install
npm run lint
npm run build
```

Optional local start:

```bash
npm start
```

[↩ Back to toc](#table-of-contents)

## 🐳 Docker Compose

Run from the repository root.

Start the web and backend services (app-only mode — bring your own external PostgreSQL):

```bash
docker compose up -d --build
```

Start with a bundled local PostgreSQL:

```bash
docker compose --profile db up -d --build
```

Stop services:

```bash
docker compose down
```

> **Note:** The `dokploy-ipv6` network is expected to exist as an external network managed by the hosting platform. The `db` service is only started when the `db` profile is active.

[↩ Back to toc](#table-of-contents)

## 📚 Project Docs

- See `ARCHITECTURE.md` for backend layering, auth/session flow, and request lifecycle notes.
- Backend API documentation is generated from Quarkus OpenAPI annotations at runtime.

[↑ Back to top](#earnit-kids)
