# EarnIt Kids

EarnIt Kids is a split-stack application with a Quarkus backend and a Node.js web edge.

## Repository Structure

- `backend/` — Quarkus backend (Maven project)
- `backend/legacy/` — legacy Node.js API code kept for compatibility during migration
- `web/` — web edge service, static assets, frontend build scripts, and UI tests
- `.env` / `.env.example` — environment configuration
- `docker-compose.yml` — local multi-service compose stack

## Backend (Quarkus)

Run from `backend/`:

```bash
mvn clean compile
mvn test
mvn quarkus:dev
```

When running in dev mode:

- Health: `http://localhost:8080/q/health`
- OpenAPI: `http://localhost:8080/q/openapi`

## Frontend/Web

Run from `web/`:

```bash
npm install
npm run build
npm run lint
```

Optional local run:

```bash
npm start
```

## Docker Compose

From repository root:

```bash
docker compose up -d --build
```

Stop:

```bash
docker compose down
```
