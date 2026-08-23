# Public Site Access - Implementation Backlog

## Goal

Visitors of the static public site can choose either the configured Telegram Mini App or the browser workspace. The browser option starts the existing server-side Google OAuth flow and, after a successful sign-in, opens `/workspace`. A signed-in browser user can sign out from the workspace and is returned to the static public site.

## Architectural decisions

- `apps/web/static/public/` remains a static marketing surface. Its JavaScript may call same-origin public auth endpoints, but it must not contain Google client credentials, mint sessions, or duplicate OAuth logic.
- `GET /api/auth-config`, `GET /api/login-google/url`, the signed `oauth_state` cookie, `AuthGoogleResource`, and the existing API proxy remain the only Google OAuth startup and callback contract. The requested redirect is the local `/workspace` route; it must continue to be validated and signed by the backend.
- The public Telegram entry continues to use the runtime-generated `window.EARNIT_CONFIG.telegramMiniAppUrl`, produced by `apps/web/scripts/preview.mjs` from the Telegram deployment configuration. Do not replace the `t.me` deep link with a raw `/telegram` URL or place the bot token in browser code.
- `POST /api/logout` and `CookieBuilder.buildLogoutCookies()` remain the sole session-revocation path. A browser-only workspace control invokes that endpoint through the existing CSRF-aware API helper and then performs a full navigation to `/public/index.html`.
- The shared workspace shells are also rendered inside `/telegram`; browser session controls must therefore be explicitly enabled by `/workspace` only. Do not present a browser logout control in the Telegram host or introduce separate parent/child logout implementations.
- Existing password login and child magic-link access remain supported. This work changes the public entry and browser session exit only; no schema, migration, or new backend endpoint is required.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-PSA-001 | P1 | - | Connects the static public surface to the established Telegram and Google OAuth contracts. |
| 2 | TASK-PSA-002 | P1 | TASK-PSA-001 | Adds an explicit, host-safe browser sign-out return path after browser entry exists. |
| 3 | TASK-PSA-003 | P2 | TASK-PSA-001, TASK-PSA-002 | Proves the user-visible public-entry and logout journeys across viewport sizes. |

## TASK-PSA-001: Add Telegram and Google workspace entry controls to the static public site

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Completed:** Public Telegram and browser-workspace entries, OAuth enhancement/fallback handling, validated login continuation, focused tests, and regression coverage are implemented. The Telegram E2E blocker was resolved by the existing working-tree update to the parent invite test, which now passes.

**Exact scope:**

Expose two clear choices on every static public page: open the configured Telegram Mini App, or sign in to the normal browser workspace with Google. Reuse the current runtime Telegram configuration and existing Google OAuth URL endpoint; do not add an OAuth flow to the static bundle.

**Files:**

- Modify `apps/web/static/public/index.html`.
- Modify `apps/web/static/public/how.html`.
- Modify `apps/web/static/public/tasks.html`.
- Modify `apps/web/static/public/rewards.html`.
- Modify `apps/web/static/public/parents.html`.
- Modify `apps/web/static/public/faq.html`.
- Modify `apps/web/static/public/site.js`.
- Modify `apps/web/static/public/styles.css`.
- Modify `apps/web/src/routes/login/+page.server.ts`.
- Modify `apps/web/tests/unit/googleOAuth.test.ts`.
- Create `apps/web/tests/unit/publicSiteAccess.test.ts` for static-entry enhancement behavior.
- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Search anchor: `serveConfigJs` in `apps/web/scripts/preview.mjs` identifies the runtime source of `window.EARNIT_CONFIG.telegramMiniAppUrl`; retain its `t.me` deep-link semantics.
- Search anchor: `loginGoogleUrl` in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java` identifies the existing signed server-side OAuth startup contract; do not duplicate it.

**Goal:**

The public static site offers an accessible Telegram entry and a Google browser entry. Google sign-in returns a parent to the protected browser workspace instead of the Telegram-only host.

### Outcome

On desktop and mobile, a visitor can distinguish “Open in Telegram” from “Continue with Google”. The Google control starts only the existing same-origin OAuth flow and preserves `/workspace` as the signed post-login target.

### Architectural decision

Progressive enhancement is required: the browser-workspace control has a safe local `/login?continue=/workspace` fallback, while JavaScript checks the existing auth configuration and requests `/api/login-google/url?redirect_to=/workspace` before redirecting to Google. The login page must use the same validated continuation when it receives an already authenticated session, rather than unconditionally redirecting to `/telegram`.

### Required changes

1. Add a consistently named, visually secondary browser-workspace action beside the existing Telegram action in the public header and relevant primary/mobile calls to action. Use a real local fallback link so navigation remains understandable when JavaScript or Google OAuth is unavailable.
2. Extend `site.js` to enhance only those marked controls: request the existing auth configuration, request the existing Google authorization URL with `/workspace` as `redirect_to` when Google is enabled, and navigate to the returned URL. Show a localized-in-page, announced failure state without exposing backend details when startup is unavailable or fails.
3. Keep `data-miniapp-link` behavior unchanged except for any necessary shared layout changes; retain its disabled/error behavior when the runtime Telegram deep link is not configured.
4. Update the authenticated branch of the login server load to redirect to a validated local `continue` value. Preserve locale handling, reject foreign/protocol-relative targets, and retain the current default destination when no continuation is supplied.
5. Add focused tests for the URL request and error handling, plus browser coverage that checks the public page starts the correct same-origin OAuth request, retains the Telegram deep link, and that `/workspace` continuation is not rewritten to another origin.

### Out of scope

- New Google OAuth credentials, OAuth scopes, Google Cloud Console changes, or client-side Google Identity Services.
- Changing Telegram account linking, BotFather configuration, `startapp` token rules, or the `/telegram` authorization gate.
- Rewriting the static site as Svelte routes, changing public-site copy beyond entry-control labels, or removing password and child-link login paths.

### Acceptance criteria

- Every static public page exposes both a Telegram entry and a browser-workspace entry; on a 320px viewport, neither action causes horizontal overflow and each interactive target is at least 44 by 44 CSS pixels.
- With a configured `telegramMiniAppUrl`, every Telegram action points to that runtime `t.me` deep link. With no configured link, the existing disabled, non-navigating behavior remains.
- Activating the browser action with Google enabled calls `GET /api/login-google/url?redirect_to=%2Fworkspace` with same-origin credentials and redirects only to the returned authorization URL.
- If Google OAuth is disabled, unavailable, or the URL request fails, the static page remains usable, exposes an announced human-readable error, and the local login fallback remains available; no secret, OAuth state, or backend stack detail is rendered.
- A successful Google callback for this entry reaches `/workspace`; a signed-in request to `/login?continue=/workspace` also reaches `/workspace`, while `//host` and absolute foreign continuations are ignored.
- Existing Telegram launch parameters at `/` still reach the Russian `/telegram` host before public-page JavaScript runs.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test -- tests/unit/googleOAuth.test.ts tests/unit/publicSiteAccess.test.ts && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-entry.spec.ts tests/e2e/telegram-auth.spec.ts
```

### Commit

```bash
git add apps/web/static/public apps/web/src/routes/login/+page.server.ts apps/web/tests/unit/googleOAuth.test.ts apps/web/tests/unit/publicSiteAccess.test.ts apps/web/tests/e2e/workspace-entry.spec.ts
git commit -m "feat(web): add public workspace entry"
```

## TASK-PSA-002: Sign out of the browser workspace to the static public site

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-PSA-001

**Exact scope:**

Add one reusable sign-out control to the normal `/workspace` host. It clears the current server session through the existing logout API and then returns the browser to `/public/index.html`, without changing Telegram-hosted workspace behavior.

**Files:**

- Create `apps/web/src/lib/features/workspace/WorkspaceSessionActions.svelte`.
- Modify `apps/web/src/lib/features/workspace/WorkspaceRoleResolver.svelte`.
- Modify `apps/web/src/routes/workspace/+page.svelte`.
- Modify `apps/web/src/lib/services/api.ts` only if the existing `logout()` result is insufficient to distinguish success from failure.
- Modify `apps/web/src/lib/i18n/messages/en/app.ts`.
- Modify `apps/web/src/lib/i18n/messages/ru/app.ts`.
- Modify `apps/web/tests/unit/workspaceAccess.test.ts`.
- Create `apps/web/tests/unit/workspaceSessionActions.test.ts`.
- Modify `apps/web/tests/e2e/workspace-access.spec.ts`.
- Search anchor: `logout = () => postBoolean('/api/logout', {})` in `apps/web/src/lib/services/api.ts` identifies the required API contract.
- Search anchor: `buildLogoutCookies` in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/CookieBuilder.java` identifies the server-owned cookie-clear behavior; do not clear auth cookies from JavaScript.

**Goal:**

A person in the normal browser workspace can explicitly sign out and lands on the public static home page only after the server has accepted the logout request.

### Outcome

`/workspace` includes one accessible sign-out action for both parent and child browser sessions. The same shared product shells remain free of that control when rendered under `/telegram`.

### Architectural decision

`WorkspaceSessionActions` owns only the browser interaction and navigation. `WorkspaceRoleResolver` receives an explicit browser-host/session-control flag from `/workspace`; `/telegram` keeps the default off. The backend remains the authority for cookie invalidation, and the client performs a full-document navigation after a successful response so Svelte state cannot display stale protected data.

### Required changes

1. Implement the reusable control with the existing `logout()` helper, a pending state that prevents duplicate requests, a visible/announced failure state, and keyboard-visible focus treatment.
2. Thread an explicit host-safe flag through the shared workspace composition so the action is rendered for `/workspace` only; do not infer the host from a mutable browser pathname inside product shells.
3. On a successful `POST /api/logout`, navigate to `/public/index.html`; on failure, remain in the workspace, preserve the active UI, and offer retry.
4. Add localized labels and error/pending copy in the existing app message domains. Maintain at least 44 by 44 CSS-pixel touch geometry and ensure the control is reachable on parent and child layouts without covering their navigation.
5. Add focused API-helper/component behavior tests and browser E2E coverage for success, failure, no duplicate POST while pending, and absence of the control in the Telegram host.

### Out of scope

- Backend logout route, cookie names, JWT/session invalidation policy, or Google account revocation.
- A logout action inside the Telegram Mini App, closing Telegram, or unlinking a Telegram account.
- New account settings, confirmation dialogs, persistence changes, or refactoring existing parent/child shells beyond the explicit composition flag.

### Acceptance criteria

- An authenticated parent or child at `/workspace` can find and activate a clearly labelled sign-out action using mouse, touch, and keyboard; focus is visible and the target is at least 44 by 44 CSS pixels.
- The action sends exactly one CSRF-aware `POST /api/logout`; while pending it cannot submit another request.
- After a successful response, the browser performs a full navigation to `/public/index.html`; a subsequent protected `/workspace` request follows the existing unauthenticated login continuation behavior.
- If logout returns an error or the request fails, the user stays on the current workspace, receives an announced localized error, and can retry without losing unrelated current UI state.
- The sign-out action is absent when the same resolver/shell is rendered by `/telegram`; Telegram init-data exchange and host behavior are unchanged.
- No JavaScript code attempts to delete HttpOnly session cookies or creates a second logout endpoint.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test -- tests/unit/workspaceAccess.test.ts tests/unit/workspaceSessionActions.test.ts && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-access.spec.ts tests/e2e/telegram-auth.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/WorkspaceSessionActions.svelte apps/web/src/lib/features/workspace/WorkspaceRoleResolver.svelte apps/web/src/routes/workspace/+page.svelte apps/web/src/lib/services/api.ts apps/web/src/lib/i18n/messages/en/app.ts apps/web/src/lib/i18n/messages/ru/app.ts apps/web/tests/unit/workspaceAccess.test.ts apps/web/tests/unit/workspaceSessionActions.test.ts apps/web/tests/e2e/workspace-access.spec.ts
git commit -m "feat(web): add workspace sign out"
```

## TASK-PSA-003: Verify public entry and browser sign-out journeys

**Status:** DONE
**Priority:** P2
**Depends on:** TASK-PSA-001, TASK-PSA-002

**Completed:** Added deterministic public-entry and browser-workspace E2E coverage across the static pages, compact-mobile layout, same-origin Google startup, Telegram root handoff, and safe OAuth failure behavior. Added authenticated-boundary logout failure coverage and documented the deployment/operator checks and verification limits.

**Exact scope:**

Complete the cross-boundary browser tests and operational handoff for the two public entry points and the browser-only logout journey. This task validates integration; it does not introduce new product behavior.

**Files:**

- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Modify `apps/web/tests/e2e/workspace-access.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-auth.spec.ts` only for regression coverage of the public Telegram root handoff.
- Modify `docs/operations/web-miniapp-access.md`.
- Search anchor: `serveConfigJs` in `apps/web/scripts/preview.mjs` identifies the runtime public configuration response to exercise.
- Search anchor: `Verification boundaries` in `docs/operations/web-miniapp-access.md` identifies where local, deployed Google, and official Telegram-client evidence must remain distinct.

**Goal:**

The repository has repeatable evidence for both public entry choices and browser logout, plus an operator checklist that distinguishes local source checks from third-party OAuth and official Telegram-client proof.

### Outcome

Automated tests verify redirect/cookie-safe contracts and responsive controls, while the runbook tells operators exactly what must be confirmed after deployment without claiming local test proof for Google or Telegram services.

### Architectural decision

Browser E2E may stub the API and OAuth URL but must never attempt to automate a real Google account or treat a browser simulation as official Telegram-client proof. Operations documentation reuses the existing `APP_URL`, Google callback, and BotFather configuration rather than adding duplicate environment variables.

### Required changes

1. Cover enabled and disabled Google startup, the `/workspace` continuation, configured/unconfigured Telegram links, successful/failing logout, and 320px horizontal-overflow assertions using accessible selectors.
2. Keep tests deterministic: intercept only the same-origin API proxy/OAuth startup response, do not mock the browser's authorization rules or embed credentials in fixtures.
3. Update the runbook's deployment checklist to test both public controls after deployment: exact Google callback/consent configuration, session creation and logout cookie clearing, and launch via the official Telegram client/BotFather URL.
4. Run the web quality gates touched by this cross-layer UI behavior and record their limits accurately; do not add lint suppression, test exclusions, or arbitrary browser waits.

### Out of scope

- Remote CI changes, deployment, BotFather updates, Google Cloud Console updates, secrets, or production data mutations.
- Visual redesign beyond the controls introduced in the prior tasks.

### Acceptance criteria

- Focused E2E passes at the configured desktop and compact-mobile projects, with `document.documentElement.scrollWidth <= window.innerWidth` on the static entry and workspace logout views at 320px.
- Tests prove a static public Google click never passes a foreign redirect target and that a failed logout never navigates away.
- Tests retain the Telegram root start-parameter handoff and assert that the browser sign-out control is not displayed inside the Telegram host.
- The runbook distinguishes local lint/unit/E2E/build evidence from deployed Google OAuth, cookies at the public edge, and an official Telegram client launch.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && npm run build && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-entry.spec.ts tests/e2e/workspace-access.spec.ts tests/e2e/telegram-auth.spec.ts && cd ../.. && git diff --check
```

### Commit

```bash
git add apps/web/tests/e2e/workspace-entry.spec.ts apps/web/tests/e2e/workspace-access.spec.ts apps/web/tests/e2e/telegram-auth.spec.ts docs/operations/web-miniapp-access.md
git commit -m "test(web): verify public access journeys"
```
