# EarnIt Kids Web Architecture

<a name="top"></a>

## Table of Contents
- [🧭 Scope](#-scope)
- [🗺️ Routing Structure](#️-routing-structure)
- [🧠 State Management](#-state-management)
- [🔌 API Integration Pattern](#-api-integration-pattern)
- [🧩 Component Conventions](#-component-conventions)
- [🎨 Styling Approach](#-styling-approach)
- [🌱 Environment Variables](#-environment-variables)
- [🧪 Verification](#-verification)

## 🧭 Scope

`apps/web` is the single active web frontend for EarnIt Kids.

- Public marketing and content pages live in SvelteKit routes.
- Authenticated parent and child experiences render in the Telegram Mini App.
- Same-origin proxy endpoints keep browser traffic pointed at the web edge rather than directly at Quarkus.

[↩ Back to toc](#table-of-contents)

## 🗺️ Routing Structure

The routing model is split between public pages, authentication, the Telegram Mini App, and edge endpoints.

- `src/routes/+page.*`: root page and session bootstrap entrypoint
- `src/routes/blog/**`: blog list and article pages sourced from `data/blog/`
- `src/routes/login/**`: email and child authentication entrypoints
- `src/routes/telegram/**`: authenticated Telegram Mini App entrypoint and parent/child workspace
- `src/routes/telegram/dashboard/**`: Telegram admin dashboard
- `src/routes/api/**`: same-origin proxy endpoints to the backend
- `src/routes/healthz/+server.ts`: lightweight container health endpoint
- `src/routes/[...path]/+page.server.ts`: legacy-compatible route fallback handling

Rule of thumb:

- Page composition belongs in `src/routes/`.
- Reusable public UI belongs in `src/lib/components/`; Telegram Mini App UI belongs in `src/lib/components/telegram/`.
- Route-local fetch/redirect logic belongs in `+page.server.ts` or `+layout.server.ts`.

[↩ Back to toc](#table-of-contents)

## 🧠 State Management

Shared stores provide session, catalog, and transient UI state to the Telegram surface.

- `src/lib/stores/app.ts`: canonical family/child runtime and catalog state
- `src/lib/stores/modal.ts`: shared modal host state
- `src/lib/stores/toasts.ts`: transient UI notifications

### Parent Access State

Session state includes membership permission for UI gating:

- `permission`: `viewer`, `editor`, or `family_admin` from active family membership
- `familyChoices`: available families when login returns multiple memberships
- `selectionRequired`: boolean flag when family chooser is needed before app entry

State lifecycle:

1. `initializeFromServer()` loads the backend snapshot.
2. `buildInitialState()` normalizes server payloads into UI-friendly shape.
3. User actions update the store and call the centralized service layer.
4. Server responses are normalized back into the store to keep identifiers and derived state aligned.

[↩ Back to toc](#table-of-contents)

## 🔌 API Integration Pattern

The frontend intentionally uses one fetch wrapper and one normalization seam.

- `src/lib/services/api.ts`: shared fetch wrapper with CSRF handling and same-origin credentials
- `src/lib/services/save.ts`: generic save payload builder and flush path for `/api/data`
- `src/lib/services/bootstrap.ts`: initial load, refresh, and child switch handling
- `src/lib/services/serverContract.ts`: normalization layer between backend DTO names and UI expectations

Current convention:

- Keep browser calls behind the shared service layer.
- Normalize backend DTO shape once instead of spreading field aliases across components.
- Preserve the existing `src/lib/services/` layout unless a repo-wide rename to `src/lib/api/` is done in one pass.
- Keep normal catalog edits on the existing snapshot save path, but model bulk actions and CSV import as explicit command endpoints with server-returned snapshots.
- Use a shared confirm modal for destructive actions instead of the native `confirm(...)` API.

[↩ Back to toc](#table-of-contents)

## 🧩 Component Conventions

The component tree is role-aware and intentionally organized around the active public and Telegram surfaces.

- `src/lib/components/PublicTopNav.svelte`: public navigation
- `src/lib/components/telegram/TelegramParentShell.svelte`: parent Mini App frame
- `src/lib/components/telegram/TelegramChildShell.svelte`: child Mini App frame
- `src/lib/components/telegram/TelegramParentHome.svelte` and `TelegramParentTasks.svelte`: parent workspace surfaces
- `src/lib/components/telegram/TelegramChildTasks.svelte`: child task surface
- `src/lib/components/telegram/ui/TelegramBottomSheet.svelte`: shared Telegram modal surface

Naming rules:

- Use `PascalCase.svelte` for components.
- Keep Telegram Mini App components under `components/telegram/` and shared Telegram primitives under `components/telegram/ui/`.
- Avoid putting long-lived business logic inside page components when it can live in a service or store.

[↩ Back to toc](#table-of-contents)

## 🎨 Styling Approach

Styling is a mix of app-wide CSS tokens and local component styles.

- `src/app.css` owns global tokens, layout primitives, and shared visual rules.
- Component-scoped `<style>` blocks are preferred for one-off layout rules.
- Inline styles should be avoided for persistent UI; promote them into scoped CSS or shared tokens.

Practical guidance:

- Keep touch targets large for child-facing actions.
- Preserve semantic structure and labels for accessibility.
- Let the relevant Telegram shell own layout rhythm; keep workspace components focused on content.

[↩ Back to toc](#table-of-contents)

## 🌱 Environment Variables

Server-side config is resolved through `src/lib/server/config.ts`.

| Variable | Default | Purpose |
| --- | --- | --- |
| `BACKEND_ORIGIN` | `http://localhost:8080` | Preferred backend origin for the edge proxy |
| `BACKEND_URL` | `http://localhost:8080` | Backward-compatible alias used in Compose/runtime wiring |
| `APP_URL` | `http://localhost:3000` | Public origin for the web runtime |
| `FRONTEND_URL` | `http://localhost:3000` | Alias for public origin when injected by backend or proxy workflows |
| `PUBLIC_BASE_URL` | `http://localhost:3000` | Additional alias for public origin |
| `SESSION_PATH` | `/api/page-data/session` | Session snapshot route |
| `WS_PATH` | `/ws` | WebSocket path forwarded by the edge |
| `DEV_PORT` | `4173` | Local SvelteKit dev port |
| `PREVIEW_PORT` | `4174` | Local preview and Playwright port |

[↩ Back to toc](#table-of-contents)

## 🧪 Verification

Use these commands from `apps/web/`:

```bash
npm run lint
npm run test
npm run test:coverage
npm run build
npm run test:e2e
```

Failure modes to watch:

- Browser-visible API drift usually means the normalization layer was skipped.
- Child switching bugs usually mean scoped data was not reloaded from the backend.
- Preview-only failures often come from stale build assets or an already-running local preview process.

[↑ Back to top](#top)
