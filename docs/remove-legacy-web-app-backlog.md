# Remove Legacy Web App — Implementation Backlog

## Goal

Remove the legacy browser web application from `apps/web` so that the **Telegram Mini App** (`/telegram` routes + `lib/components/telegram/` + `lib/telegram/`) is the only remaining authenticated frontend surface. This deletes the `/app` section shell, the web super-admin, and the print catalog, while preserving every piece of code the Mini App still depends on (auth routes, shared services, shared stores, i18n) and the public static marketing site (`static/public/`).

## Architectural decisions

- **Source of truth for the frontend** is the Telegram Mini App: `apps/web/src/routes/telegram/**`, `apps/web/src/lib/components/telegram/**`, and `apps/web/src/lib/telegram/**`. Everything else in `apps/web/src/routes` and `apps/web/src/lib/components` is legacy web and is removed unless listed below as shared.
- **Shared services kept** (consumed by both the Mini App and, today, the legacy web app): `lib/services/api.ts`, `bootstrap.ts`, `save.ts`, `serverContract.ts`, `confirm.ts`, `taskPayload.ts`, `todayTaskViewModel.ts`, `telegramActivity.ts`, `telegramViewState.ts`, `telegram.ts`, `catalogTelemetry.ts` (referenced by `hooks.client.ts`). Shared stores kept: `lib/stores/app.ts`, `modal.ts`, `toasts.ts`.
- **Web-only services removed**: `cardViewMode.ts`, `catalogItemViewModel.ts`, `catalogViewState.ts`, `groupPrompt.ts`, `printCatalog.ts`, `pwa.ts`, `push.ts`, `websocket.ts`. These are imported only by `lib/components/app/**` (verified via grep) and have no Mini App consumer.
- **Auth routes kept**: `/login`, `/login-child`, `/verify`, `/reset-password`. The Mini App entry (`routes/telegram/+page.svelte`) links to `/login` for the `unlinked` state, and the backend magic-link flow targets `/login-child/:token`.
- **`isAdminRole`** is the only member of `lib/app/routes.ts` still needed after removal (used by `routes/telegram/dashboard/+page.server.ts`). It is moved to `lib/auth/roles.ts` and `lib/app/routes.ts` is deleted, so no web-only section-routing code survives.
- **Root redirect**: authenticated users go to `/telegram` (super-admins to `/telegram/dashboard`); unauthenticated users still go to `/public/index.html` (the public static site). Only the `/app/<section>` redirect is removed.
- **Public static site** (`static/public/*.html` + `resolvePublicRedirect`/`PUBLIC_REDIRECT_MAP`/`LEGACY_ALIAS_MAP` + `/public` in `BYPASS_PREFIXES`) is **kept** — it is the current public marketing site and is out of scope for this removal. The `publicOrigin` config value and the `sitemap.xml`/`robots.txt` entries remain unchanged.
- **Rejected approach**: keeping `lib/components/app/**` "for reference"; duplicating Mini App components instead of deleting the web cluster; deleting shared services that the Mini App still imports.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P0-1 | P0 | - | Remove the web app shell cluster (routes + components + web-only services) as one atomic change so the build stays green. |
| 2 | P0-2 | P0 | P0-1 | Relocate `isAdminRole` and delete `lib/app/routes.ts` after its only web consumers are gone. |
| 3 | P0-3 | P0 | P0-2 | Repoint root and login redirects at `/telegram` (keep `/public/index.html` for unauthenticated). |
| 4 | P0-4 | P0 | P0-3 | Remove web-only i18n domains (keep the `public` domain). |
| 5 | P0-5 | P0 | P0-4 | Remove web-only unit and E2E tests. |
| 6 | P0-6 | P0 | P0-5 | Final dead-code sweep and full verification gates. |
| 7 | P2-1 | P2 | P0-6 | Re-sync the Capacitor mobile wrapper to the Mini App entry. |

## P0-1: Remove the legacy web app shell

**Status:** ✅ Done
**Priority:** P0
**Depends on:** -

### Outcome

`/app/*`, `/super-admin`, and `/print/*` no longer resolve to any UI; the `lib/components/app/**` component cluster and the web-only services are gone. The Mini App (`/telegram`) and auth routes (`/login`, `/login-child`, `/verify`, `/reset-password`) remain fully functional.

### Architectural decision

The legacy web app is a self-contained cluster with no Mini App consumers (verified: `lib/components/app/**` is imported only by `routes/app/**`, `routes/super-admin/**`, and internal section files). It is removed as one unit. Shared services (`api.ts`, `bootstrap.ts`, `save.ts`, `serverContract.ts`, `confirm.ts`, `taskPayload.ts`, `todayTaskViewModel.ts`, `catalogTelemetry.ts`) are **not** touched here.

### Files

- Remove `apps/web/src/routes/app/` (entire directory).
- Remove `apps/web/src/routes/super-admin/` (entire directory).
- Remove `apps/web/src/routes/print/` (entire directory).
- Remove `apps/web/src/lib/components/app/` (entire directory).
- Remove `apps/web/src/lib/services/cardViewMode.ts`.
- Remove `apps/web/src/lib/services/catalogItemViewModel.ts`.
- Remove `apps/web/src/lib/services/catalogViewState.ts`.
- Remove `apps/web/src/lib/services/groupPrompt.ts`.
- Remove `apps/web/src/lib/services/printCatalog.ts`.
- Remove `apps/web/src/lib/services/pwa.ts`.
- Remove `apps/web/src/lib/services/push.ts`.
- Remove `apps/web/src/lib/services/websocket.ts`.

### Work

1. Delete the three route directories and the `lib/components/app/` directory.
2. Delete the eight web-only service files listed above.
3. Do **not** modify `lib/app/routes.ts` here (P0-2), `routes/+page.server.ts` (P0-3), or any shared service.
4. Confirm no remaining import references `$lib/components/app/` or the removed services (grep before and after).

### Acceptance criteria

- `grep -r "\$lib/components/app/" apps/web/src` returns no matches.
- `grep -rE "cardViewMode|catalogItemViewModel|catalogViewState|groupPrompt|printCatalog|services/pwa|services/push|services/websocket" apps/web/src` returns no matches outside `lib/app/routes.ts` (handled in P0-2).
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/routes/app apps/web/src/routes/super-admin apps/web/src/routes/print apps/web/src/lib/components/app apps/web/src/lib/services
git commit -m "refactor(web): remove legacy web app shell, super-admin, and print catalog"
```

## P0-2: Relocate `isAdminRole` and delete `lib/app/routes.ts`

**Status:** ✅ Done
**Priority:** P0
**Depends on:** P0-1

### Outcome

`lib/app/routes.ts` is deleted. `isAdminRole` lives in `lib/auth/roles.ts` and `routes/telegram/dashboard/+page.server.ts` imports it from the new location. No section-routing helpers (`APP_SECTIONS`, `toAppPath`, `resolvePreferredAppSection`, `getAppSectionFromPath`, `getDefaultAppSection`, `isSectionAllowed`, `isAppSection`) remain.

### Architectural decision

`isAdminRole` is a role predicate, not a routing concern, so it belongs in `lib/auth/` alongside `googleOAuth.ts`. The section-routing helpers were web-only and are deleted with the file.

### Files

- Create `apps/web/src/lib/auth/roles.ts` (export `isAdminRole`).
- Modify `apps/web/src/routes/telegram/dashboard/+page.server.ts` (import `isAdminRole` from `$lib/auth/roles`).
- Remove `apps/web/src/lib/app/routes.ts`.
- Remove `apps/web/src/lib/app/` (directory, once empty).

### Work

1. Move the `isAdminRole` function body into `lib/auth/roles.ts` unchanged.
2. Update the import in `telegram/dashboard/+page.server.ts`.
3. Delete `lib/app/routes.ts` and the now-empty `lib/app/` directory.

### Acceptance criteria

- `grep -r "lib/app/routes" apps/web/src` returns no matches.
- `isAdminRole` is exported from `$lib/auth/roles` and still returns `true` for `admin`, `parent`, `super_admin`.
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/auth/roles.ts apps/web/src/routes/telegram/dashboard/+page.server.ts apps/web/src/lib/app
git commit -m "refactor(web): relocate isAdminRole to lib/auth and drop section routing"
```

## P0-3: Repoint root and login redirects at the Mini App

**Status:** ✅ Done
**Priority:** P0
**Depends on:** P0-2

### Outcome

The root URL redirects authenticated users to `/telegram` (super-admins to `/telegram/dashboard`) and unauthenticated users still to `/public/index.html`. The `/login` page redirects already-authenticated users to `/telegram` instead of `/app/<section>`.

### Architectural decision

The Mini App is the only authenticated surface. The `LAST_APP_SECTION_COOKIE` and section-preference logic are obsolete and removed from the redirect path. The public static site remains the unauthenticated landing page, so the `/public/index.html` fallback is **kept**.

### Files

- Modify `apps/web/src/routes/+page.server.ts` (replace the section redirect with `/telegram`/`/telegram/dashboard`; keep the `/public/index.html` fallback).
- Modify `apps/web/src/routes/login/+page.server.ts` (redirect authenticated users to `/telegram`).

### Work

1. In `+page.server.ts`, drop the `LAST_APP_SECTION_COOKIE`/`resolvePreferredAppSection`/`toAppPath` imports and redirect authenticated users to `/telegram` (or `/telegram/dashboard` for `super_admin`).
2. Keep the unauthenticated fallback `redirect(302, '/public/index.html')` unchanged.
3. In `login/+page.server.ts`, redirect authenticated users to `/telegram`.

### Acceptance criteria

- An authenticated non-admin request to `/` redirects to `/telegram`.
- An authenticated `super_admin` request to `/` redirects to `/telegram/dashboard`.
- An unauthenticated request to `/` still redirects to `/public/index.html`.
- An authenticated request to `/login` redirects to `/telegram`.
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build && npm run test
```

### Commit

```bash
git add apps/web/src/routes/+page.server.ts apps/web/src/routes/login/+page.server.ts
git commit -m "refactor(web): repoint root and login redirects at the Mini App"
```

## P0-4: Remove web-only i18n domains

**Status:** ✅ Done
**Priority:** P0
**Depends on:** P0-3

### Outcome

i18n domains that are no longer referenced by any surviving route or component (`superadmin`, and `analytics` if unused) are removed from the domain registry and message catalogs. The `public` domain is **kept** (the public static site still uses it). The `MessageDomain` union and `resolveDomainsForPath` no longer reference the removed domains.

### Architectural decision

The Mini App uses `app`, `admin`, `history`, `tasks`, `common`, `auth`, and `errors` domains. The public static site uses `public`. `superadmin` is web-only. `analytics` is web-only unless the Mini App dashboard references it (verify before removing). Domain removal is driven by grep evidence, not assumption; the `public` domain and its `/public` `BYPASS_PREFIXES` entry are **not** removed.

### Files

- Modify `apps/web/src/lib/i18n/config.ts` (remove `superadmin`/`analytics` from `MessageDomain` and `resolveDomainsForPath`; keep `public`).
- Modify `apps/web/src/lib/i18n/index.ts` and `apps/web/src/lib/i18n/messages/{en,ru}/` (remove the corresponding domain files and registrations; keep `public`).

### Work

1. Grep for `superadmin.` and `analytics.` message keys across `apps/web/src` to confirm no surviving consumer.
2. Remove the confirmed-unused domains from the registry and catalogs.
3. Keep the `public` domain and any domain still referenced by the Mini App.

### Acceptance criteria

- `grep -rE "superadmin\.|analytics\." apps/web/src` returns no matches for the removed domains.
- The `public` domain and `/public` `BYPASS_PREFIXES` entry remain intact.
- `npm run lint`, `npm run build`, and `npm run test` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build && npm run test
```

### Commit

```bash
git add apps/web/src/lib/i18n
git commit -m "refactor(web): remove web-only i18n domains"
```

## P0-5: Remove web-only tests

**Status:** ✅ Done
**Priority:** P0
**Depends on:** P0-4

### Outcome

Unit and E2E tests that target removed web surfaces are deleted or updated so `npm run test` and `npm run test:e2e` pass against the Mini App-only codebase.

### Architectural decision

Tests are removed only when their subject was deleted in P0-1…P0-4. Tests for shared services (`api`, `bootstrap`, `save`, `serverContract`, `confirm`, `taskPayload`, `todayTaskViewModel`, `catalogTelemetry`, `catalogFilter`, `groupOrder`, `csvImport`, `shopPayload`, `readyCatalogTelemetry`) are kept.

### Files

- Remove `apps/web/tests/unit/appRoutes.test.ts`.
- Modify `apps/web/tests/unit/appRedirects.test.ts` (assert `/telegram`/`/public/index.html` redirects instead of `/app/<section>`).
- Remove `apps/web/tests/unit/catalogViewState.test.ts`.
- Remove `apps/web/tests/unit/printCatalog.test.ts`.
- Remove `apps/web/tests/unit/pwa.test.ts`.
- Remove `apps/web/tests/unit/websocket.test.ts`.
- Remove `apps/web/tests/unit/historyDetails.test.ts`, `requestDetails.test.ts`, `historyGroups.test.ts`, `historyStats.test.ts`, `analyticsViewModel.test.ts`, `analyticsRecommendations.test.ts` (subjects were in `lib/components/app/sections/`).
- Remove web-only E2E specs: `analytics.spec.ts`, `app-sections.spec.ts`, `child-shop.spec.ts`, `parent-access-settings.spec.ts`, `roles.spec.ts`, `shop-filters.spec.ts`, `tasks-shop-ui.spec.ts`, and `smoke.spec.ts` if it targets `/app`.

### Work

1. Delete the unit test files whose subjects were removed.
2. Update `appRedirects.test.ts` to assert the new redirect targets.
3. Delete the web-only E2E specs; keep `telegram-*.spec.ts`, `visual-regression-miniapp.spec.ts`, `screenshots-miniapp.spec.ts`, and `auth.spec.ts` (verify `auth.spec.ts` still targets surviving routes).

### Acceptance criteria

- `npm run test` passes with no references to removed modules.
- `npm run test:e2e` passes (or the remaining specs are green).
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build && npm run test
cd apps/web && npm run test:e2e
```

### Commit

```bash
git add apps/web/tests
git commit -m "test(web): remove web-only unit and E2E tests"
```

## P0-6: Final dead-code sweep and verification

**Status:** ✅ Done
**Priority:** P0
**Depends on:** P0-5

### Outcome

No dead code, orphaned imports, or stale references remain. The full quality gate passes: lint, typecheck, unit tests, build, and E2E.

### Architectural decision

This is a verification-only task. It confirms the removal left no dangling references (e.g. `catalogTelemetry.ts` still referenced by `hooks.client.ts`, `LocaleSwitcher`/`PublicTopNav` still used by `/login`), and that the public static site wiring (`resolvePublicRedirect`, `/public` `BYPASS_PREFIXES`, `sitemap.xml`) is untouched.

### Files

- Any file surfaced by the sweep (grep for removed symbols, `svelte-check`, ESLint unused-import rules).

### Work

1. Grep for removed symbols (`ShopSection`, `AppShell`, `AppNav`, `ChildSwitcher`, `cardViewMode`, `printCatalog`, `pwa`, `push`, `websocket`, `toAppPath`, etc.).
2. Run `svelte-check` and fix any remaining type errors.
3. Confirm `lib/components/` contains only `LocaleSwitcher.svelte`, `PublicTopNav.svelte`, and `telegram/`.
4. Confirm `lib/services/` contains only the shared services listed in the architectural decisions.
5. Confirm `static/public/` and its redirect/alias wiring are still present and functional.

### Acceptance criteria

- No grep matches for removed symbols.
- `static/public/` and `resolvePublicRedirect` remain intact.
- `npm run lint`, `npm run build`, `npm run test`, and `npm run test:e2e` all pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build && npm run test && npm run test:e2e
```

### Commit

```bash
git add apps/web
git commit -m "chore(web): final dead-code sweep after legacy web removal"
```

## P2-1: Re-sync the Capacitor mobile wrapper to the Mini App

**Status:** ✅ Done
**Priority:** P2
**Depends on:** P0-6

### Outcome

The Capacitor wrapper (`apps/mobile`) points at the Mini App entry (`/telegram`) instead of the removed `/app` shell, and its bundled `public/` assets are regenerated from the current build.

### Architectural decision

The mobile wrapper is a packaging layer around the web runtime. After the legacy web removal, its stale `public/` build (which still references `/app`, `/about`, `/blog`, `/faq`, `/features`) must be re-synced. This is a packaging concern, not a source-of-truth change.

### Files

- Modify `apps/mobile/capacitor.config.*.json` (point the start URL at `/telegram`).
- Regenerate `apps/mobile/apps-mobile/mobile/*/public/` from the current `apps/web` build.

### Work

1. Update the Capacitor start URL to `/telegram`.
2. Re-run the mobile sync (`npm run sync` / `npm run sync:local`) to refresh bundled assets.
3. Verify the wrapper opens the Mini App.

### Acceptance criteria

- The mobile wrapper launches the Mini App, not the removed `/app` shell.
- No bundled asset references `/app/<section>`.

### Verification

```bash
cd apps/mobile && npm run sync:local
```

### Commit

```bash
git add apps/mobile
git commit -m "chore(mobile): re-sync Capacitor wrapper to the Mini App entry"
```
