# EarnIt Kids Svelte Web Runtime

This workspace serves as the primary SvelteKit web edge/runtime for the project.

## Local Commands

```bash
cd apps/web
npm install
npm run lint
npm test
npm run build
npm run start
npm run test:e2e
```

## Local Topology

- SvelteKit dev: `http://localhost:4173`
- SvelteKit preview and Playwright: `http://localhost:4174`
- Backend: `http://localhost:8080`

## Environment Variables

- `BACKEND_ORIGIN`: backend base URL. Default: `http://localhost:8080`
- `BACKEND_URL`: legacy-compatible alias for the backend base URL used by Docker Compose.
- `SESSION_PATH`: server-side session snapshot endpoint. Default: `/api/page-data/session`
- `WS_PATH`: backend websocket path reserved for the migration seam. Default: `/ws`
- `DEV_PORT`: local dev port for SvelteKit. Default: `4173`
- `PREVIEW_PORT`: local preview port for SvelteKit and Playwright. Default: `4174`

## Current Scope

Completed in this pass:

- SvelteKit 2 workspace with TypeScript and `adapter-node`
- Primary Node edge runtime for `/healthz`, `/api/*`, `/login-child/*`, and `/ws`
- Docker runtime image for the compose `web` service
- Server-side session bootstrap helper for the root route
- Local blog markdown under `data/blog/`
- Static verification endpoints under `static/` and `static/.well-known/`
- Legacy-compatible security headers and gzip handling in the edge runtime
- Service worker registration, install CTA, offline banner, and pull-to-refresh wiring
- `lint`, `build`, `test`, and `test:e2e` commands

Still pending before parity:

- Wider authenticated E2E coverage against live backend data
- Wider production-like smoke coverage for mobile deep-link and platform association endpoints
