# Live Coin Shop Demo Review - Remediation Backlog

## Goal

Make the documented and accepted Playwright command for the public live coin shop demo executable, so the completed LCD-006 browser proof can be repeated exactly in local development and CI.

## Architectural decisions

- `apps/web/playwright.config.ts` owns named Playwright execution environments. The existing default `use` settings remain the single source of browser, locale, preview-server, and network behavior; the named `chromium` project must extend those settings rather than duplicate them.
- Keep the LCD-006 test independent from the authenticated workspace suites and do not weaken its API-isolation assertions. The remediation is test-runner configuration only; the demo route, session state, API, and static-site output are out of scope.
- The review reproduced `npm run test:e2e -- tests/e2e/live-coin-shop-demo.spec.ts` successfully (3 passed), so removing `--project=chromium` from the acceptance command would hide the contract mismatch instead of preserving the repository's stated invocation.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | LCD-R-001 | P2 | - | Restore the accepted browser-verification command before relying on it in local or CI evidence. |

## LCD-R-001: Declare the Chromium Playwright project

**Status:** DONE
**Priority:** P2
**Depends on:** -

**Exact scope:**

Align `apps/web/playwright.config.ts` with the existing LCD-006 targeted-validation and final-quality-gate command, both of which select `--project=chromium`.

**Files:**

- Modify `apps/web/playwright.config.ts` (the `defineConfig` execution-environment declaration).
- Modify `apps/web/tests/e2e/live-coin-shop-demo.spec.ts` only if a named project exposes a browser-specific test assumption that must be corrected; otherwise leave it unchanged.
- Modify `docs/live-coin-shop-demo-backlog.md` only to record the corrected, rerunnable LCD-006 verification evidence after the configuration change.

**Goal:**

The repository accepts the documented Chromium-targeted command and runs the same preview-backed live-demo test suite that currently succeeds only when no project is selected.

### Outcome

`npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts` starts the configured preview/backend pair and completes the three anonymous demo tests without changing their API-traffic assertions.

### Architectural decision

Add one named `chromium` project in the central Playwright configuration, inheriting the existing global `use` contract and overriding only the browser selection required by Playwright. Do not create a second web-server setup, duplicate locale settings, or move demo test configuration into the spec.

### Required changes

1. Define a `chromium` project whose browser is Chromium while retaining the current global base URL, headless settings, service-worker behavior, locale override mechanism, and optional executable-path support.
2. Preserve the current single-worker/serial execution and preview web-server configuration so this task neither changes fixtures nor masks infrastructure failures.
3. Run the exact LCD-006 command with `--project=chromium`; retain the assertions for direct EN/RU access, request/reset/reload behavior, mobile geometry, focus, and no `/api/` traffic.
4. Update the original backlog's execution evidence only after that exact command passes. Do not mark this remediation done without the named-project result.

### Out of scope

- Live demo UI, fixture data, reward-request action port, SvelteKit routes, backend APIs, and persistence.
- Replacing the focused browser test with a unit test, disabling service workers, or removing the `--project=chromium` selector from the accepted command.
- Deployment, CI, Telegram-client, or physical-device validation.

### Acceptance criteria

- `--project=chromium` resolves to exactly the intended Chromium execution environment instead of failing with `Project(s) "chromium" not found`.
- The focused LCD-006 browser suite passes using the named project and remains preview-backed without cookies, Telegram globals, API stubs, or `/api/` requests.
- Existing non-project Playwright invocation remains valid, or any intentional change is documented in the configuration and backlog evidence.
- No production source or generated static artifact changes are introduced by this configuration-only fix.

### Targeted validation

```bash
cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts
cd apps/web && npm run lint
git diff --check
```

### Commit

```bash
git add apps/web/playwright.config.ts docs/live-coin-shop-demo-backlog.md
git commit -m "test(web): declare chromium Playwright project"
```

## Rejected observations

- The focused demo suite itself is not a confirmed functional regression: it passed locally without a project selector (3/3 tests), including its anonymous, reset/reload, Russian-locale, mobile-layout, and API-isolation assertions.
- No backend remediation task is created: the reviewed demo path is intentionally memory-only and the browser proof captured no `/api/` requests.

## Review evidence and limits

- Source review covered the route, demo session, reward-request context port, shared reward UI, i18n, PWA registration boundary, static-site link generation, and focused unit/E2E tests.
- Passed locally: targeted Vitest suite (5 files, 26 tests), `npm run lint`, and focused Playwright invocation without `--project` (3 tests).
- Confirmed failure: `cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts` exits before tests because no Playwright projects are declared.
- LCD-R-001 passed locally: `cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts` completed 3 tests with the preview-backed `chromium` project; `npm run lint` and `git diff --check` also passed.
- These results are local source/browser evidence only; they do not prove CI, deployed infrastructure, Telegram Mini App client, or physical-device behavior.
