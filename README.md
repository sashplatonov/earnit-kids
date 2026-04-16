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
- `docker-compose.yml`: local Docker Compose for native mode
- `docker-compose.jvm.yml`: local Docker Compose for JVM mode
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
> **Note:** `docker-compose.yml` uses `backend/Dockerfile` for native mode. `docker-compose.jvm.yml` uses `backend/Dockerfile.jvm` for JVM mode.

[↩ Back to toc](#table-of-contents)

## 📚 Project Docs

- See `ARCHITECTURE.md` for backend layering, auth/session flow, and request lifecycle notes.
- Backend API documentation is generated from Quarkus OpenAPI annotations at runtime.

[↑ Back to top](#earnit-kids)
