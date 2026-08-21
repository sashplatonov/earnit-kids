# Telegram Mini App UI System Refactor - Review Remediation Backlog

## Goal

Restore the two confirmed user-visible regressions found while reviewing the
completed Telegram Mini App UI-system refactor: remove the remaining legacy
`/app` post-authentication contract in favour of the Mini App, and keep the
child Activity sub-navigation compact on mobile.

## Architectural decisions

- **Post-authentication ownership:** `/telegram` is the only current
  authenticated family surface. The login page starts email/password and Google
  flows there; the Google resource maps only the active localized Telegram
  destination back to its localized `/login` chooser when family selection is
  required. Do not restore a compatibility `/app` route.
- **Tab-bar layout ownership:** `TelegramTabBar.svelte` owns the distinction
  between the fixed primary navigation and the inline child Activity tabs. The
  surrounding child workspace already reserves safe-area space for the fixed
  primary navigation, so the inline tab bar must retain normal local spacing.
- **Scope discipline:** the backend CORS/HSTS and Compose changes included in
  the final refactor commit passed their focused checks but are unrelated to
  these two findings; do not modify them in this remediation.
- **Test ownership:** remove only tests whose subject was the deleted legacy
  portal. Keep shared Google OAuth tests, but change their obsolete `/app`
  fixture targets to `/telegram`; add focused post-authentication coverage
  rather than recreating the removed portal E2E cluster.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 | P1 | - | Remove the dangling legacy post-auth contract before it can send users to a 404. |
| 2 | P2-1 | P2 | - | Correct the shared layout variant and protect compact Activity geometry. |
| 3 | P3-1 | P3 | P1-1 | Align the active web architecture document after source and tests are clean. |

## TASK-P1-1: Remove the remaining legacy `/app` authentication contract

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

The legacy portal itself and its portal-only tests were already deleted, but
the registration, password-login, family-choice, and Google-login start paths
still use `/app`. `AuthGoogleResource` and the related Java/TypeScript tests
also encode that removed destination. No `apps/web/src/routes/app/` route
exists, so the stale contract can send users to a 404.

**Files:**

- Modify `apps/web/src/routes/login/+page.svelte`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/auth/AuthGoogleResource.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/auth/AuthResourceTest.java`.
- Modify `apps/web/tests/unit/googleOAuth.test.ts`.
- Create `apps/web/tests/e2e/auth-post-login.spec.ts`.
- Search anchors: `$i18n.href('/app')` in `apps/web/src/routes/login/+page.svelte` and
  `deriveLoginRedirectTarget` in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/auth/AuthGoogleResource.java`.

**Goal:**

After email/password or Google authentication, the browser targets the existing
Telegram Mini App rather than the deleted web portal.

### Outcome

All post-authentication and OAuth state destinations use `/telegram`, while
family-selection returns to the localized login page without retaining a
legacy-route compatibility branch.

### Architectural decision

Use `$i18n.href('/telegram')` in the login UI. Extend the existing backend
redirect normalizer for the active localized Telegram route so a Google user
who must choose a family returns to `/login` or `/<locale>/login`. Do not keep
or add `/app` aliases, redirect routes, tests, or portal components.

### Required changes

1. Replace the registration, password login, family-choice, and Google OAuth
   start targets in `login/+page.svelte` with the localized `/telegram` route.
2. Replace the backend's legacy `/app` path handling with the equivalent
   localized `/telegram` to `/login` chooser mapping; preserve same-origin
   validation and error redirect behavior.
3. Update the existing Google OAuth unit and resource tests to exercise
   `/telegram` targets, including success, failure, and family-choice cases.
4. Add focused browser coverage for the email/password registration/login
   success destination. Remove no shared auth coverage; the already-deleted
   portal-only E2E specs remain deleted.

### Out of scope

- Changing backend authentication/session APIs.
- Reintroducing legacy `/app` pages, aliases, components, or portal-only E2E
  tests.
- Changing Telegram Mini App authentication.

### Acceptance criteria

- A successful password login, registration, and family selection navigate to
  the localized `/telegram` route, never `/app`.
- Google OAuth receives `/telegram` as its state destination; successful and
  error redirects preserve that current destination.
- A Google OAuth family-choice flow returns to the corresponding localized
  `/login` chooser rather than a removed portal URL.
- `apps/web/src`, the Google OAuth resource, and their active auth tests have
  no runtime or fixture reference to the legacy `/app` route.
- Failed login and explicit safe redirect behavior are unchanged.

### Targeted validation

```bash
cd apps/web && npm run test -- googleOAuth
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- auth-post-login.spec.ts
cd apps/web && npm run lint
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest=AuthResourceTest
rg -n "(['\"])\/app(?:[/'\"]|$)" apps/web/src apps/web/tests apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/auth apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/auth
```

### Commit

```bash
git add apps/web/src/routes/login/+page.svelte apps/web/tests/unit/googleOAuth.test.ts apps/web/tests/e2e/auth-post-login.spec.ts apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/auth/AuthGoogleResource.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/auth/AuthResourceTest.java
git commit -m "fix(auth): Remove legacy post-login route"
```

## TASK-P2-1: Keep child Activity sub-navigation compact on mobile

**Status:** DONE
**Priority:** P2
**Depends on:** -

**Exact scope:**

At widths of 700 px and below, `TelegramTabBar.svelte` assigns
`margin-bottom: 5.75rem` to `.tabs--inline`. The child Activity "History /
Requests" bar is the inline variant, so it receives a large blank gap before
its content even though only the fixed primary tab bar requires bottom-safe-area
reservation.

**Files:**

- Modify `apps/web/src/lib/components/telegram/ui/TelegramTabBar.svelte`.
- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.
- Search anchor: `.tabs--inline` in `apps/web/src/lib/components/telegram/ui/TelegramTabBar.svelte`.

**Goal:**

On a compact mobile viewport, child Activity sub-tabs remain adjacent to their
history or request list while the fixed primary tab bar remains safe-area-aware
and does not cover workspace content.

### Outcome

The shared component gives bottom-reservation geometry only to the fixed
variant; the inline variant retains its normal local tab-to-panel spacing.

### Architectural decision

Use `fixedOnMobile` as the single layout discriminator in `TelegramTabBar`.
The primary child workspace keeps its existing bottom padding; do not add
feature-specific margin overrides in `TelegramChildShell`.

### Required changes

1. Remove or correct the mobile-only inline margin so it does not reserve the
   fixed-navigation height.
2. Preserve the fixed primary bar's viewport anchoring, safe-area padding, and
   panel reachability.
3. Extend the child Activity browser test at 320 px to assert the inline
   tablist's computed bottom margin stays at the normal compact value and the
   following list starts without the retired large gap.

### Out of scope

- Changing tab IDs, deep-link context parsing, keyboard tab behavior, or
  activity loading/pagination.
- Redesigning the primary bottom navigation.

### Acceptance criteria

- At 320 px, the Activity `History / Requests` tablist does not reserve 5.75rem
  of blank space below itself.
- The selected Activity list remains visible directly below its inline tabs,
  without horizontal overflow.
- The primary parent and child tab bars remain fixed at the bottom with their
  existing safe-area behavior.
- Arrow/Home/End keyboard selection and ARIA tab relationships remain intact.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-layout.spec.ts telegram-child.spec.ts
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/ui/TelegramTabBar.svelte apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "fix(web): Compact inline Telegram tabs"
```

## TASK-P3-1: Remove legacy portal claims from active web architecture docs

**Status:** DONE
**Priority:** P3
**Depends on:** P1-1

**Exact scope:**

`apps/web/docs/ARCHITECTURE.md` still describes the deleted authenticated app
shell, `/super-admin` routes, `components/app/**`, and app-section conventions.
It conflicts with the current source tree and the completed
`remove-legacy-web-app-backlog.md`.

**Files:**

- Modify `apps/web/docs/ARCHITECTURE.md`.
- Search anchors: `AppShell.svelte`, `components/app/`, and `routes/super-admin`.

**Goal:**

The active web architecture document names the real route and component
boundaries: public/static entry points, auth, `/telegram`, and the Telegram
admin dashboard.

### Outcome

Future changes do not revive deleted portal concepts because the primary web
architecture documentation matches the source tree.

### Architectural decision

Document only live source-of-truth surfaces. Keep historical removal rationale
in `docs/remove-legacy-web-app-backlog.md`; do not duplicate that history in
the active architecture document.

### Required changes

1. Replace removed `/super-admin` and app-shell route/component descriptions
   with the current `/telegram` and `/telegram/dashboard` structure.
2. Remove obsolete app-section naming guidance and retain only conventions
   supported by existing components.
3. Preserve the documented public-site, auth, edge-proxy, shared-store, and
   verification guidance where it is still correct.

### Out of scope

- Altering runtime routes, components, translations, or tests.
- Rewriting archived backlog documents.

### Acceptance criteria

- The architecture document contains no claim that `components/app/**`,
  `/super-admin`, or the authenticated app shell exists.
- Every route/component path newly documented exists in `apps/web/src`.
- The document distinguishes the public static site from the Telegram Mini App
  and its dashboard.

### Targeted validation

```bash
rg -n "AppShell\.svelte|components/app/|routes/super-admin" apps/web/docs/ARCHITECTURE.md
git diff --check
```

### Commit

```bash
git add apps/web/docs/ARCHITECTURE.md
git commit -m "docs(web): Remove legacy portal architecture"
```

## Rejected observations

- The reviewed request, list-surface, bottom-sheet, focus, deep-link, and
  child cancellation flows passed the focused local browser suite; no confirmed
  remediation task is created for them.
- The added backend security-header/CORS code passed
  `InfrastructureFiltersTest`, and `docker compose config` passed. These are
  local checks only, not deployed-environment proof.
