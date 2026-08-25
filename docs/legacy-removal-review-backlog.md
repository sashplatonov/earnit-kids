# Legacy Login Removal - Review Backlog

## Goal

Remove the retired browser login UI without breaking public Google entry, browser workspace logout, multi-family selection, or Telegram Mini App flows.

## Architectural decisions

- `/public/index.html` is public, `/workspace` is browser-authenticated, and `/telegram` is the Mini App. Do not restore `/login` or aliases.
- Replace the removed login page with two new purpose-specific contracts: a redirect-capable `GET /api/login-google/start` endpoint for native public anchors, and a dedicated `/select-family` browser route for a short-lived multi-family OAuth selection.
- OAuth and family selection stay server-owned. The new selection route loads choices from a server-validated short-lived selection context and completes through `AuthResource.selectFamily`; do not reuse the old page, parse a client cookie as a source of truth, or duplicate membership state in the browser.
- `WorkspaceRoleResolver` owns browser-only logout rendering; Telegram must remain excluded.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | LRR-001 | P1 | - | Restore multi-family OAuth completion. |
| 2 | LRR-002 | P1 | - | Keep public Google entry actionable. |
| 3 | LRR-003 | P1 | - | Restore browser logout. |
| 4 | LRR-004 | P0 | LRR-001, LRR-002, LRR-003 | Restore the active E2E gate. |

## LRR-001: Complete multi-family Google OAuth without legacy login

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:** `AuthGoogleResource.loginGoogleCallback` emits `pending_family_chooser`, but the deleted `apps/web/src/routes/login/+page.svelte` was its only UI consumer. Replace it with a purpose-specific `/select-family` route, not another general login screen.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthResource.java` (`/api/select-family`) and add the smallest server-owned read contract for the pending choice context.
- Create `apps/web/src/routes/select-family/+page.server.ts` and `apps/web/src/routes/select-family/+page.svelte`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthResourceTest.java` and focused web tests.

**Goal:** A Google account with several memberships can select an allowed family and enter `/workspace` without visiting `/login`.

### Outcome

The callback no longer redirects a pending chooser cookie to `/public/index.html`, where it is ignored.

### Architectural decision

Use a short-lived, server-validated selection context bound to the OAuth callback and existing `AuthService.selectFamily` authorization. The UI is a compact selector only: no sign-in form, registration, marketing navigation, or email/password fallback.

### Required changes

1. Redirect `selectionRequired` to `/select-family` and issue a selection context that the backend can validate and read; do not redirect it to the public page.
2. Load only validated membership choices at `/select-family`, submit `/api/select-family`, handle blocked/invalid choices, issue normal auth cookies, and clear every short-lived selection cookie/context.
3. Preserve ordinary single-family OAuth continuations.
4. Add backend and browser regression coverage.

### Out of scope

- Password login/registration UI.
- Membership schema or permission changes.

### Acceptance criteria

- Multi-family OAuth reaches `/select-family`, never the public page or a recreated login page.
- Keyboard and touch selection reaches the selected family workspace.
- Invalid choices are recoverable and do not issue a session.
- `/login`, `/ru/login`, and `/login.html` remain absent.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=AuthResourceTest test
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-entry.spec.ts
```

### Commit

```bash
git commit -m "fix(auth): complete multi-family Google sign-in"
```

### Verification completion

- Signed pending family context, `/select-family` server read contract, browser selector, and backend regression coverage are complete.
- `./mvnw -B -ntp -Dtest=AuthResourceTest test` passes (24/24).
- `npm run lint` passes.
- `PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-entry.spec.ts` passes (4/4).

## LRR-002: Keep public Google entry actionable without the legacy page

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:** `apps/web/static/public/site.js` currently assigns `/public/index.html` on OAuth startup failure. The navigation removes its own status message, and a JavaScript-disabled CTA only reloads the same page. Replace this with the purpose-specific redirect endpoint `GET /api/login-google/start?continue=/workspace`; do not make a new login route.

**Files:**

- Modify `apps/web/static/public/site.js` and all six `apps/web/static/public/*.html` pages.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java` to add `GET /api/login-google/start`, reusing its existing OAuth-state, local-continuation, and Google-configuration logic.
- Modify `apps/web/tests/unit/publicSiteAccess.test.ts` and `apps/web/tests/e2e/workspace-entry.spec.ts`.

**Goal:** The public parent CTA starts validated Google OAuth or leaves a visible, retryable fallback without `/login`.

### Outcome

Public browser access works with JavaScript, failed startup, and a native anchor fallback.

### Architectural decision

Keep authorization URL generation and continuation validation backend-owned. `GET /api/login-google/start` is a redirect endpoint for a native anchor; `/api/login-google/url` remains the JSON enhancement endpoint. Neither is a login page.

### Required changes

1. Point every public parent CTA at `/api/login-google/start?continue=%2Fworkspace` as its native `href`; JavaScript may enhance it through the existing JSON endpoint.
2. Keep an `aria-live` failure visible and retryable instead of immediately navigating away.
3. Preserve `/workspace` continuation and Google host/protocol validation.
4. Cover success, disabled Google, network error, invalid URL, and no-JavaScript behavior.

### Out of scope

- Google credential/scope changes.
- Password authentication UI.

### Acceptance criteria

- No public CTA targets `/login` or `/login.html`.
- A failed startup leaves an actionable retry state visible.
- The native CTA starts OAuth through `/api/login-google/start` or returns to the public page with a clear, actionable error state.
- Mobile public pages retain 44px controls and no overflow.

### Targeted validation

```bash
cd apps/web && npm run test -- publicSiteAccess
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-entry.spec.ts
```

### Commit

```bash
git commit -m "fix(auth): keep public Google entry actionable"
```

## LRR-003: Restore browser logout in workspace

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:** `WorkspaceRoleResolver` supports `WorkspaceSessionActions`, but `apps/web/src/routes/workspace/+page.svelte` omits `showSessionActions`; parent and child workspace pages therefore render no logout button.

**Files:**

- Modify `apps/web/src/routes/workspace/+page.svelte`.
- Modify `apps/web/tests/e2e/workspace-access.spec.ts`.

**Goal:** Browser parent and child workspaces expose logout; `/telegram` does not.

### Outcome

Successful logout sends one CSRF-aware request and navigates to `/public/index.html`; failure stays in place with retry feedback.

### Architectural decision

Pass the existing resolver flag from the browser host only. Do not duplicate logout behavior in shells or add it to Mini App UI.

### Required changes

1. Enable `showSessionActions` at `/workspace`.
2. Prove parent/child logout, one pending request, retry after failure, and Telegram absence.

### Out of scope

- Browser push controls.
- Telegram logout.

### Acceptance criteria

- Parent and child browser workspaces show an accessible logout button.
- A double click sends one request.
- Failure is announced and retryable.
- Telegram never renders the browser control.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-access.spec.ts
```

### Commit

```bash
git commit -m "fix(web): restore workspace sign out"
```

## LRR-004: Repair public, workspace, and Mini App E2E coverage

**Status:** DONE
**Priority:** P0
**Depends on:** LRR-001, LRR-002, LRR-003

**Exact scope:** Production-preview E2E has eight current failures. Public tests search for removed “Продолжить с Google” text; workspace tests hardcode non-localized continuation values and cannot prove missing logout; the parent-invite test uses a stale first-step interaction.

**Files:**

- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Modify `apps/web/tests/e2e/workspace-access.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-auth.spec.ts`.
- Modify `apps/web/tests/e2e/e2eBackend.mjs` only if a fixture is proven missing.

**Goal:** The production-build suite proves current public, browser, and Mini App contracts and fails if legacy login returns.

### Outcome

The active E2E quality gate passes without skips, selector weakening, or direct HTTP replacements for browser behavior.

### Architectural decision

Retain preview SSR and service-worker setup. Update tests only for intended LRR-001–003 contracts, including localized continuation paths and the current name-first invite flow.

### Required changes

1. Target current public CTA labels and the new `/api/login-google/start` plus enhanced JSON OAuth behavior.
2. Assert local, locale-aware workspace continuations.
3. Prove parent/child logout after LRR-003.
4. Complete the current Telegram parent-invite flow and preserve payload/320px assertions.
5. Add negative legacy-route rendering coverage.

### Out of scope

- Product changes solely to satisfy an old selector.
- Skips, longer timeouts, or removed Mini App coverage.

### Acceptance criteria

- `PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e` passes.
- Public, browser parent/child, and Telegram Mini App flows stay covered.
- The suite detects a restored legacy login page, a replacement full login/registration UI, or Telegram logout leakage.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e
```

### Commit

```bash
git commit -m "test(web): cover legacy login removal flows"
```

## Rejected observations

- No confirmed Telegram bot command, callback, persistence, or webhook regression appears in this diff. The bot-adjacent invalid child-link redirect is covered by `ChildMagicLinkResourceTest`.
- The known full backend `verify` failure in `EntityTimestampsTest` is an unrelated H2 history-constraint failure, not evidence against the legacy-removal paths.
