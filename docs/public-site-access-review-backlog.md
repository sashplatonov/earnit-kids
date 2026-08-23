# Public Site Access - Review Remediation Backlog

## Goal

Close confirmed security, progressive-enhancement, and regression-proof gaps found while reviewing completed TASK-PSA-001..003. Google OAuth must return only to this application, unavailable OAuth must leave a usable browser-login path, and local browser coverage must exercise authenticated logout instead of skipping it.

## Architectural decisions

- redirect_to is untrusted public API input. AuthGoogleResource must sign only a validated application-local continuation; PublicOriginResolver may apply the configured origin but must not make a caller-provided absolute origin valid.
- Static-public HTML remains the fallback source of truth. JavaScript may replace its local login link only after it has obtained a valid OAuth URL, and must not turn that fallback into an intercepted no-op.
- Logout continues to use server-side cookie revocation and full-document navigation. The necessary change is deterministic authenticated browser proof, not another logout implementation.
- Rejected observation: at 320px the reviewed public pages have no horizontal overflow, and the sign-out action is absent on the Telegram host. No responsive remediation task is warranted.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-PSA-R-001 | P0 | - | Removes the unsafe OAuth return target before expanding browser coverage. |
| 2 | TASK-PSA-R-002 | P1 | TASK-PSA-R-001 | Restores the promised local login fallback when Google startup fails. |
| 3 | TASK-PSA-R-003 | P2 | TASK-PSA-R-001, TASK-PSA-R-002 | Makes the repaired journeys executable in browser tests. |

## TASK-PSA-R-001: Reject cross-origin OAuth continuations before signing state

**Status:** DONE
**Priority:** P0
**Depends on:** -

**Exact scope:**

Harden the OAuth startup/callback return path used by public browser entry. An absolute redirect_to currently passes through PublicOriginResolver.toAbsoluteRedirect(), is signed in state by AuthGoogleResource.loginGoogleUrl(), and becomes a foreign post-authentication callback redirect.

**Files:**

- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/util/PublicOriginResolver.java only if a local-continuation validator belongs there.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthResourceTest.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/util/PublicOriginResolverTest.java when the validator is extracted there.
- Search anchor: loginGoogleUrl in apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java.

**Goal:**

Accept supported local application continuations, but never sign or redirect to a caller-controlled external origin.

### Outcome

GET /api/login-google/url?redirect_to=/workspace keeps its existing same-application flow. Absolute, protocol-relative, encoded bypass, malformed, and control-character continuations fail safely without a usable foreign return URL.

### Architectural decision

Root cause: normalizeRedirect() explicitly preserves http:// and https:// values, while loginGoogleUrl() signs that result without an ownership check. This violates the completed public-entry requirement that /workspace be a validated signed post-login target and permits an OAuth-assisted open redirect. Validate a local continuation before signing; resolve the configured origin only after that validation, retaining supported local paths such as /en/telegram.

### Required changes

1. Define one reusable local-continuation rule: one leading slash, no protocol-relative form, scheme, authority, unsafe controls, or encoded bypass.
2. Reject invalid redirect_to with a stable client-safe 4xx error (or documented local default) before minting OAuth state; never retain a foreign target in state.
3. Ensure callback success and failure redirects use only the validated stored continuation and append error parameters without corrupting an existing query.
4. Add focused resource tests for /workspace, existing local /en/telegram, https://attacker.example, //attacker.example, and encoded protocol-relative variants.

### Out of scope

- Google credentials, scopes, consent-screen configuration, and client-side Google Identity Services.
- Replacing oauth_state, its expiry, or parent-invitation continuation semantics beyond the same origin rule.
- Generic refactoring of unrelated PublicOriginResolver callers.

### Acceptance criteria

- /workspace remains a valid same-application OAuth target.
- No invalid continuation appears in signed state or a callback Location with a foreign origin.
- Existing local /en/telegram behavior remains compatible.
- Callback failures preserve a valid local query string.

### Targeted validation

```bash
    cd apps/backend && JAVA_HOME=/Users/sash/.sdkman/candidates/java/25.0.2-amzn ./mvnw test -Dtest=AuthResourceTest,PublicOriginResolverTest
```

### Commit

```bash
    git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/util/PublicOriginResolver.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthResourceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/util/PublicOriginResolverTest.java
    git commit -m "fix(backend): constrain OAuth continuations to app origin"
```

## TASK-PSA-R-002: Keep the browser-login fallback actionable when OAuth startup fails

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-PSA-R-001

**Exact scope:**

Restore the local browser-login fallback after static JavaScript initializes. Each data-browser-workspace-link click currently calls preventDefault() before OAuth availability is known; disabled Google or either failed request only shows a message, making its /login?continue=%2Fworkspace fallback unreachable.

**Files:**

- Modify apps/web/static/public/site.js.
- Modify apps/web/static/public/site.d.ts if its exported contract changes.
- Modify apps/web/tests/unit/publicSiteAccess.test.ts.
- Modify apps/web/tests/e2e/workspace-entry.spec.ts.
- Search anchor: data-browser-workspace-link in apps/web/static/public/site.js.

**Goal:**

A visitor can use the local login route when Google is disabled, unavailable, or returns an invalid authorization URL.

### Outcome

The public browser action uses the server-returned authorization URL only after successful same-origin startup. On failure it announces a generic localized error and keeps a working keyboard, mouse, and touch path to the local login route.

### Architectural decision

Root cause: the handler unconditionally consumes the anchor click, and its catch only writes an aria-live paragraph. This violates TASK-PSA-001 progressive enhancement and its requirement that the local fallback remain available after disabled or failed Google startup. Keep the static anchor as the source of truth and preserve native navigation on failure or expose an equivalent real fallback anchor in the failure state; do not duplicate OAuth in the static bundle.

### Required changes

1. Make disabled configuration, unavailable configuration, failed OAuth URL request, and malformed or empty URL responses leave an actionable local fallback.
2. Retain generic localized status feedback without exposing response bodies, OAuth state, or backend details.
3. Preserve successful startup: same-origin credentials, redirect_to=/workspace, and navigation only to a usable server-returned authorization URL.
4. Add unit and browser coverage for disabled, network-failed, and invalid/non-OK URL cases, asserting that the user can follow a real /login?continue=%2Fworkspace fallback.

### Out of scope

- New OAuth endpoints, credentials, framework migration, or Telegram-link contract changes.
- Changes to password login or child magic-link access.

### Acceptance criteria

- With JavaScript enabled and startup unavailable, an announced localized error and a reachable local fallback are both present.
- Valid enabled startup requests only /api/login-google/url?redirect_to=%2Fworkspace with same-origin credentials and navigates to its returned authorization URL.
- Invalid URL responses neither redirect to an untrusted target nor remove the fallback.
- Telegram Mini App behavior stays unchanged.

### Targeted validation

```bash
    cd apps/web && npm run lint && npm run test -- tests/unit/publicSiteAccess.test.ts && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-entry.spec.ts
```

### Commit

```bash
    git add apps/web/static/public/site.js apps/web/static/public/site.d.ts apps/web/tests/unit/publicSiteAccess.test.ts apps/web/tests/e2e/workspace-entry.spec.ts
    git commit -m "fix(web): retain public browser-login fallback"
```

## TASK-PSA-R-003: Exercise authenticated browser sign-out without conditional skips

**Status:** DONE
**Priority:** P2
**Depends on:** TASK-PSA-R-001, TASK-PSA-R-002

**Exact scope:**

Replace the conditionally skipped logout browser test with a deterministic authenticated workspace fixture or equivalent supported setup. The test currently opens guest /workspace, finds no sign-out button, then skips; it cannot prove the success path, duplicate-submission lock, or child-session placement required by TASK-PSA-002 and TASK-PSA-003.

**Files:**

- Modify apps/web/tests/e2e/workspace-access.spec.ts.
- Modify apps/web/tests/unit/workspaceSessionActions.test.ts if focused pending/error coverage needs extension.
- Modify apps/web/playwright.config.ts or an existing E2E fixture only when required for an authenticated test boundary.
- Search anchor: requires an authenticated browser workspace session in apps/web/tests/e2e/workspace-access.spec.ts.

**Goal:**

Provide repeatable local browser evidence for successful and failed sign-out in supported browser workspace roles, without live Google or deployed sessions.

### Outcome

E2E no longer skips sign-out: it verifies exactly one CSRF-aware POST while pending, full navigation to /public/index.html only on success, failure/retry in place, parent/child browser visibility, and Telegram-host absence.

### Architectural decision

Root cause: preview E2E starts without an authenticated session and dynamically skips rather than provisioning one. This violates the completed cross-layer test task's success, failure, duplicate-POST, and parent/child coverage requirements. Use the existing session/proxy boundary or a supported backend fixture for deterministic test-only authentication; do not forge production cookies in browser code, automate Google, or weaken the auth filter.

### Required changes

1. Add a repeatable test-only authenticated parent and child session boundary with a valid CSRF setup for intercepted logout requests.
2. Replace the skip with success, single-POST-pending, failure/retry, and child visibility assertions.
3. Retain the Telegram-host absence assertion and mock providers only at same-origin boundaries.
4. Ensure the fixture is impossible to activate through normal production configuration or to use as an alternate auth path.

### Out of scope

- Production auth routes, cookie formats, CSRF policy, Google credentials, and third-party OAuth automation.
- Telegram-hosted logout or unrelated E2E refactoring.

### Acceptance criteria

- The selected E2E file has no conditional skip for browser sign-out and runs deterministic parent and child cases.
- Successful sign-out sends exactly one CSRF-aware POST and performs a full navigation to /public/index.html.
- Failed sign-out stays in the workspace, announces a localized error, and can retry without a concurrent duplicate POST.
- /telegram still omits browser sign-out.
- No live Google account, deployed session, or production cookie value is needed.

### Targeted validation

```bash
    cd apps/web && npm run lint && npm run test -- tests/unit/workspaceSessionActions.test.ts && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-access.spec.ts tests/e2e/workspace-entry.spec.ts tests/e2e/telegram-auth.spec.ts
```

### Commit

```bash
    git add apps/web/tests/e2e/workspace-access.spec.ts apps/web/tests/unit/workspaceSessionActions.test.ts apps/web/playwright.config.ts
    git commit -m "test(web): cover authenticated workspace sign out"
```
