# EarnIt Kids Svelte Migration Workspace

This workspace implements the first migration foundation slice for the SvelteKit backlog.

## Local Commands

```bash
cd apps/web-svelte
npm install
npm run lint
npm test
npm run build
npm run test:e2e
```

## Local Topology

- Legacy web: `http://localhost:3000`
- SvelteKit dev: `http://localhost:4173`
- SvelteKit preview and Playwright: `http://localhost:4174`
- Backend: `http://localhost:8080`

## Environment Variables

- `BACKEND_ORIGIN`: backend base URL. Default: `http://localhost:8080`
- `SESSION_PATH`: server-side session snapshot endpoint. Default: `/api/page-data/session`
- `WS_PATH`: backend websocket path reserved for the migration seam. Default: `/ws`
- `LEGACY_WEB_ORIGIN`: legacy web app origin for side-by-side parity work. Default: `http://localhost:3000`
- `DEV_PORT`: local dev port for SvelteKit. Default: `4173`
- `PREVIEW_PORT`: local preview port for SvelteKit and Playwright. Default: `4174`

## Current Scope

Completed in this pass:

- Independent SvelteKit 2 workspace with TypeScript and `adapter-node`
- Independent `lint`, `build`, `test`, and `test:e2e` commands
- Server-side session bootstrap helper for the root route
- `healthz` endpoint for deployment probes
- Backend proxy routes for `/api/*` and `/login-child/*`
- Public route placeholders for the current URL matrix foundation

Still pending before parity:

- Legacy public content migration
- Authenticated family shell parity
- WebSocket proxy/cutover strategy
- Super-admin console migration
- Full UI parity validation
