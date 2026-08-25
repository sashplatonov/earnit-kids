# Repository Readiness - Implementation Backlog

## Goal

Make EarnIt Kids a secure, maintainable, and operationally clear public repository. The codebase must retain its modular-monolith design, keep authorization and family ownership server-side, pass its declared quality gates, and document normal engineering choices without recruitment-oriented copy.

## Review evidence

- `origin/main` still contains commit `75546922c80cb0289678d127db20bd3cee1350ae`, whose reachable tree contains `apps/mobile/earnit-kids.keystore`; deleting the current file did not remove the signing key from Git history.
- `JAVA_HOME="/Users/sash/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp verify` currently fails in `EntityTimestampsTest.replaceHistory_preservesOldCreatedAtWhenAppendingNewEntry`: `HistoryRepository.copyHistoryEntry` copies a zero `delta` and violates `CK_HISTORY_DELTA_AMOUNT` from `V51__add_coin_ledger_invariants.sql`.
- The web lint, unit-test, and production-build command passed locally. `npm audit --omit=dev --json` reported zero production dependency vulnerabilities. These local results do not prove CI, deployed configuration, provider delivery, or browser/Telegram-client behavior.
- The existing README has one recruitment-oriented reference, while the architectural, security, testing, and operator content is otherwise useful and should be retained.
- The web edge and backend set baseline headers, but neither runtime declares a Content Security Policy or Permissions Policy. Browser logging currently contains raw `console.*` calls; `uiLog.ts` can forward arbitrary stringified console arguments to `/api/ui-log` without a schema, length limit, redaction boundary, sampling, or correlation contract.
- The codebase has strong existing foundations: server-side family scoping, Telegram signature/freshness checks, Flyway migrations, transactionally persisted delivery work, structured backend logs, trace correlation, metrics, health checks, and focused tests. Preserve these mechanisms rather than replacing the modular monolith with distributed infrastructure.

## Architectural decisions

- PostgreSQL plus Flyway remain the persistence authority. `children.balance` remains a projection, while `history.delta`, `reason`, and reversal relationships carry the ledger contract; do not introduce a second balance or event store.
- Authentication, authorization, CSRF, Telegram admission, and rate limiting remain backend concerns. The SvelteKit edge may proxy requests and set browser headers but must not become an authorization source of truth.
- Logging is an operational interface, not a data export channel. Log only allowlisted operational fields, propagate the existing trace identifier across the web-to-backend boundary, and use metrics for aggregate behavior. Do not log credentials, cookies, invitation/OAuth tokens, raw request bodies, names, email addresses, or family/child identifiers unless an approved, privacy-reviewed hash is necessary.
- Reuse Quarkus/Micrometer, structured JSON logs, the existing health endpoints, the current GitHub Actions workflow, and Playwright/Vitest/JUnit. Do not add Kafka, Redis, microservices, Kubernetes, a second telemetry vendor, or a second persistence model solely for presentation.
- Each task is one atomic commit. Existing unstaged work is outside this backlog and must be preserved; rebase a task onto it only after confirming that its acceptance criteria are not already satisfied.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-RR-001 | P0 | - | Removes a reachable private signing key before any public release. |
| 2 | TASK-RR-002 | P0 | - | Restores the backend's declared verification gate and ledger invariant. |
| 3 | TASK-RR-003 | P1 | TASK-RR-001 | Makes secret-history protection continuously enforceable after the remediation. |
| 4 | TASK-RR-004 | P1 | TASK-RR-002 | Establishes a tested browser security-header contract. |
| 5 | TASK-RR-005 | P1 | TASK-RR-002 | Replaces uncontrolled logs with correlated, privacy-safe diagnostics. |
| 6 | TASK-RR-006 | P1 | TASK-RR-002 | Exercises the browser-facing critical flows in CI. |
| 7 | TASK-RR-007 | P2 | TASK-RR-002 | Restores a zero-warning static-quality baseline without weakening tools. |
| 8 | TASK-RR-008 | P2 | TASK-RR-006 | Splits oversized dashboard and access-flow components at existing UI boundaries. |
| 9 | TASK-RR-009 | P2 | TASK-RR-005 | Removes stale log-tail code and makes the operator path unambiguous. |
| 10 | TASK-RR-010 | P2 | TASK-RR-001, TASK-RR-002, TASK-RR-004, TASK-RR-005, TASK-RR-006, TASK-RR-007, TASK-RR-008, TASK-RR-009 | Keeps the repository guide accurate and neutral. |

## TASK-RR-001: Purge and rotate Android signing material

**Status:** DONE
**Priority:** P0  
**Depends on:** -

**Exact scope:**

Remove the Android signing key from every reachable local and remote ref, rotate the corresponding Android signing identity outside Git, and make the rewritten history the only published history. The current blocking evidence is `apps/mobile/earnit-kids.keystore` in `75546922c80cb0289678d127db20bd3cee1350ae`, reachable through `origin/main`.

**Files:**

- Modify `.gitleaks.toml` only if a history-aware rule/test needs a precise adjustment.
- Modify `.github/workflows/publication.yml` and/or `.github/workflows/quality.yml` only to enforce the final history scan at the appropriate release boundary.
- Search anchor: `apps/mobile/earnit-kids.keystore` in `git log --all --name-only`.
- External secret store: create the replacement Android signing-key entry and remove access to the retired key.

**Goal:**

No clone of a published repository ref can recover the retired signing material, and future release candidates fail before publication when a private key is introduced.

### Outcome

The repository contains no signing material in the working tree or reachable history, while the application can still be signed with a newly generated and externally stored key.

### Architectural decision

Git contains only references to deployment/release inputs; signing keys belong in the release secret store. A history rewrite and key rotation are both required because either action alone leaves a usable credential path.

### Required changes

1. Inventory all local branches, tags, and remote branches that contain the key; coordinate a forced update of every affected published ref and invalidate forks/caches according to the hosting-provider procedure.
2. Generate a replacement Android signing key, update the external release secret and CI/release configuration, and revoke/delete the retired key from every secret location.
3. Rewrite reachable history with a history-rewriting tool appropriate for the host, force-push the rewritten refs, and require collaborators to reclone or reset from the rewritten refs.
4. Retain a history-aware Gitleaks scan with `fetch-depth: 0`; add a regression check that proves the private-key file rule catches a fixture without committing a real key.

### Out of scope

- Changing application package identifiers, publishing a mobile app, or modifying unrelated credentials.
- Claiming that local Git inspection proves third-party forks, build caches, or secret-manager deletion.

### Acceptance criteria

- `git log --all --name-only` contains no Android keystore path and `git fsck --no-reflogs --unreachable` has no recoverable copy after the agreed local cleanup window.
- Every branch and tag advertised by `git ls-remote --heads --tags origin` resolves to rewritten history with no key path.
- The retired key cannot sign a release; the replacement key signs a non-production verification artifact through the external release path.
- The complete-history secret scan passes on a fresh clone with full history.

### Targeted validation

```bash
git clone --mirror <published-repository-url> /tmp/earnit-kids-audit.git
git -C /tmp/earnit-kids-audit.git log --all --name-only | rg '(^|/)earnit-kids\.keystore$' && exit 1 || true
docker run --rm -v /tmp/earnit-kids-audit.git:/repo zricethezav/gitleaks:v8.30.1 git --source=/repo --log-opts="--all" --redact --config=/repo/.gitleaks.toml
```

### Commit

```bash
git add .gitleaks.toml .github/workflows/publication.yml .github/workflows/quality.yml
git commit -m "fix(security): purge retired signing material"
```

## TASK-RR-002: Restore the ledger invariant verification gate

**Status:** DONE
**Priority:** P0  
**Depends on:** -

**Exact scope:**

Make history upsert/replacement preserve the `V51` ledger invariant. The failing path is `HistoryRepository.replaceHistory` → `copyHistoryEntry`, where imported `HistoryEntryEntity` values constructed without `delta` overwrite an existing valid delta. Keep the migration's `amount >= 0 AND delta <> 0` constraint intact.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/history/HistoryRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/history/HistoryEntryEntity.java` if normalization belongs to the entity lifecycle boundary.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/EntityTimestampsTest.java`.
- Add focused history repository/entity tests beside the existing test when a separate import/upsert behavior test improves coverage.

**Goal:**

`./mvnw verify` passes while every persisted history row has a non-zero signed delta consistent with its type and non-negative displayed amount.

### Outcome

The migration invariant is honored by both newly persisted and updated legacy/import history rows; existing `createdAt` preservation remains verified.

### Architectural decision

The entity/persistence boundary owns invariant normalization. Callers must not need to remember ledger defaults, and repository copy logic must not create a state that the schema rejects. Do not relax the constraint, lower JaCoCo, or exclude the test.

### Required changes

1. Choose one normalization point that applies before both insert and update, preserving an explicitly supplied non-zero signed delta.
2. Ensure `replaceHistory` and `upsertHistoryEntry` cannot overwrite a valid row with zero delta when importing legacy-shaped entries.
3. Characterize earn, spend, explicit delta, and repeated external-id replacement behavior against H2; keep the PostgreSQL-compatible migration constraint unchanged.
4. Run the full lifecycle-bound backend gate because the migration, entity lifecycle, repository behavior, and coverage contract meet there.

### Out of scope

- Replacing the current ledger projection with event sourcing.
- Editing already merged Flyway migrations or weakening the database constraint.

### Acceptance criteria

- `EntityTimestampsTest.replaceHistory_preservesOldCreatedAtWhenAppendingNewEntry` passes.
- Replacing/upserting an earn entry created without delta persists a positive delta; a spend entry persists a negative delta.
- An explicitly supplied non-zero delta is not silently rewritten.
- H2 test migrations apply and the backend `verify` gate passes with the existing 0.80 threshold.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="/Users/sash/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp -Dtest=EntityTimestampsTest test
cd apps/backend && JAVA_HOME="/Users/sash/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/history/HistoryRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/history/HistoryEntryEntity.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/EntityTimestampsTest.java
git commit -m "fix(backend): preserve ledger deltas during history replacement"
```

## TASK-RR-003: Make security scanning release-blocking and auditable

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-RR-001

**Exact scope:**

Harden the existing `Quality` and `Publication security` workflows around their actual coverage: filesystem Trivy scans, changed-commit Gitleaks scanning, and the complete-history release scan. Make security results inspectable without leaking findings to public logs.

**Files:**

- Modify `.github/workflows/quality.yml`.
- Modify `.github/workflows/publication.yml`.
- Modify `.gitleaks.toml` only for a reviewed false-positive/fixture rule.
- Create `.github/dependabot.yml` if no dependency-update policy exists after confirming the repository's preferred update cadence.

**Goal:**

A dependency, misconfiguration, or secret finding with the chosen severity blocks the correct pull-request or release path and leaves a private artifact suitable for remediation.

### Outcome

Security automation covers both fast feedback and the public-release boundary, with version-pinned scanner images/actions and no reliance on a developer's local tool installation.

### Architectural decision

Keep the current Trivy and Gitleaks approach; broaden coverage only where the existing pipeline has a demonstrated blind spot. Dependency updates remain normal Git changes reviewed by the same quality gates, not an unreviewed automatic merge mechanism.

### Required changes

1. Verify the trigger/ref ranges for pushes, pull requests, manual runs, and releases; make the full-history scan a required release check after TASK-RR-001.
2. Scan all shipped artifacts/configuration in scope, including root Compose/Docker configuration where it is currently outside the app-directory scans.
3. Add a dependency update policy or document why the hosting platform's existing policy is authoritative; keep update PRs gated by tests and scanners.
4. Add a workflow-level regression/smoke assertion for scan configuration and documented severity policy.

### Out of scope

- Adding paid security platforms, SBOM attestation infrastructure, or ignoring findings to get a green build.

### Acceptance criteria

- A test fixture or intentionally introduced local test rule demonstrates that secret scanning fails the expected CI job without exposing the secret in logs.
- A HIGH or CRITICAL Trivy finding in a shipped app/config blocks the security job; scanner reports are retained privately for a defined short period.
- The complete-history Gitleaks job runs from a full checkout and is required before release publication.
- Dependency update PRs are created on the documented cadence and run the ordinary quality workflow.

### Targeted validation

```bash
docker compose config --quiet
git diff --check
```

### Commit

```bash
git add .github/workflows/quality.yml .github/workflows/publication.yml .github/dependabot.yml .gitleaks.toml
git commit -m "ci(security): enforce release security scanning"
```

## TASK-RR-004: Establish a strict browser security-header contract

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-RR-002

**Exact scope:**

Define, apply, and browser-test Content Security Policy and Permissions Policy for SvelteKit routes, the custom `scripts/preview.mjs` edge, and the static public site. Retain the existing HTTPS-gated HSTS behavior and baseline anti-sniff/frame/referrer headers.

**Files:**

- Modify `apps/web/src/hooks.server.ts`.
- Modify `apps/web/scripts/preview.mjs`.
- Modify `apps/web/static/public/index.html`, `apps/web/static/public/how.html`, `apps/web/static/public/tasks.html`, `apps/web/static/public/rewards.html`, `apps/web/static/public/parents.html`, and `apps/web/static/public/faq.html` only when their inline resources must be externalized or nonce/hash-compatible.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/security/SecurityHeadersFilter.java` only for API-specific headers that are missing from the shared policy.
- Add header tests beside `apps/web/tests/e2e/` and `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/observability/InfrastructureFiltersTest.java`.

**Goal:**

Browser content is constrained to the exact first-party resources and required Telegram/browser integrations, with no permissive wildcard or blanket `unsafe-inline` policy.

### Outcome

Authenticated workspace, Telegram Mini App, API proxy, and static public pages render and function with the same explicit security posture.

### Architectural decision

The SvelteKit/preview edge owns document CSP because it knows rendered assets; the backend owns API response headers. Use nonces or hashes where inline bootstrap is unavoidable, and share a small policy builder rather than copy divergent header strings into every route.

### Required changes

1. Inventory scripts, styles, images, connections, frame ancestors, workers, and Telegram requirements before selecting directives.
2. Implement the narrow policy in both deployed web entry paths; retain local development behavior without silently shipping a relaxed production policy.
3. Add `Permissions-Policy` that disables unneeded browser capabilities and explicitly permits only required ones.
4. Add browser and unit/infrastructure tests for the resulting headers and a representative login/workspace/Telegram/static-page flow.

### Out of scope

- Replacing Telegram integrations, adding a web application firewall, or changing authentication sessions.

### Acceptance criteria

- Production responses include CSP, Permissions Policy, `X-Content-Type-Options`, `X-Frame-Options`, Referrer Policy, and HTTPS-only HSTS.
- The CSP contains no `*` source and no blanket `unsafe-inline`; deviations are individually documented in code with the integration that requires them.
- At least one static public page, `/workspace`, `/telegram`, and a proxied API response are checked for the applicable headers.
- Login, workspace bootstrapping, service-worker registration, and required Telegram assets work in an authenticated browser test without CSP violations.

### Targeted validation

```bash
cd apps/web && npm run lint && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- --grep "security headers"
cd apps/backend && JAVA_HOME="/Users/sash/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp -Dtest=InfrastructureFiltersTest test
```

### Commit

```bash
git add apps/web/src/hooks.server.ts apps/web/scripts/preview.mjs apps/web/static/public apps/web/tests apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/security/SecurityHeadersFilter.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/observability/InfrastructureFiltersTest.java
git commit -m "fix(security): enforce browser security headers"
```

## TASK-RR-005: Make web-to-backend diagnostics structured and privacy-safe

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-RR-002

**Exact scope:**

Replace raw web `console.*` operational logging and the dormant unrestricted `ui-log` relay with an allowlisted diagnostic contract. Cover SvelteKit `handleError`, session/proxy failures, browser request failures, and the backend receiver/structured log boundary.

**Files:**

- Modify `apps/web/src/hooks.server.ts`.
- Modify `apps/web/src/lib/server/proxy.ts`.
- Modify `apps/web/src/lib/server/session.ts`.
- Modify `apps/web/src/lib/services/bootstrap.ts`.
- Modify or delete `apps/web/src/lib/utils/uiLog.ts`.
- Modify or delete `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/api/UiLogResource.java` and `UiLogMessage.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/AuthFilter.java` and the existing trace/observability classes only when needed for correlation/redaction.
- Add tests beside `apps/web/tests/unit/` and `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/api/`.

**Goal:**

Operators can correlate a browser failure to backend logs without receiving secrets or unrestricted user-controlled text, and routine successful requests do not create noisy logs.

### Outcome

Web and backend diagnostics use a bounded event shape: severity, stable event code, route template, HTTP status/category, trace ID, and safe timing/error classification.

### Architectural decision

The server is authoritative for operational logs. Browser diagnostics are opt-in, sampled, size-limited, authenticated/CSRF-protected when sent, and limited to a typed allowlist; they are not a monkey patch of global console methods. Metrics count aggregate failures, while logs record actionable exceptional events.

### Required changes

1. Define a single typed event schema and a sanitizing logger adapter; do not serialize raw errors, request URLs with query strings, headers, cookies, or arbitrary console arguments.
2. Remove success-path logs that emit family scope/role data and replace them with metrics or debug-only, redacted events only where diagnostically necessary.
3. Propagate and return the existing trace identifier through the web proxy and include it in sanitized server error events.
4. Either remove the unused `ui-log` path or implement the bounded contract end-to-end with authentication, CSRF, body-size limit, event allowlist, sampling/rate limit, and tests for redaction/rejection.
5. Update the monitoring runbook with event taxonomy, alertable failure conditions, retention/PII rules, and the evidence boundary between local logs and deployed telemetry.

### Out of scope

- Recording session replay, analytics identifiers, customer data, full distributed tracing, or a new telemetry platform.

### Acceptance criteria

- A request URL containing an invitation/OAuth token and an exception containing an authorization header cannot appear verbatim in web or backend test-captured log output.
- A sanitized web failure event carries the same trace ID as its proxied backend request.
- Successful session/bootstrap requests do not log family IDs, roles, or payload fields at info level.
- Browser events exceeding the body limit, missing CSRF/authentication, unknown event code, or exceeding sampling/rate limits are rejected without log injection.
- The monitoring runbook names the emitted fields, redaction rules, and alert-worthy failure categories.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test -- --run
cd apps/backend && JAVA_HOME="/Users/sash/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp -Dtest=UiLogResourceTest,TraceFilterTest,AuthFilterTest test
```

### Commit

```bash
git add apps/web/src/hooks.server.ts apps/web/src/lib/server/proxy.ts apps/web/src/lib/server/session.ts apps/web/src/lib/services/bootstrap.ts apps/web/src/lib/utils/uiLog.ts apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/api apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/api apps/web/tests/unit docs/monitoring/newrelic.md
git commit -m "fix(observability): sanitize web diagnostics"
```

## TASK-RR-006: Run deterministic browser critical paths in CI

**Status:** TODO
**Priority:** P1  
**Depends on:** TASK-RR-002

**Exact scope:**

Wire stable browser E2E coverage into `.github/workflows/quality.yml`. Start from existing Playwright suites for authentication, workspace entry/access/invitations, Telegram authentication, and PWA behavior; keep visual-regression baselines separate from deterministic functional blocking checks.

**Files:**

- Modify `.github/workflows/quality.yml`.
- Modify `apps/web/playwright.config.ts` and `apps/web/package.json` if a CI-safe script or preview mode is required.
- Modify only the directly executed specs under `apps/web/tests/e2e/`.
- Modify `apps/web/scripts/preview.mjs` only when the test server must expose a deterministic health/readiness contract.

**Goal:**

Pull requests cannot silently break the browser-to-backend proxy, authentication continuation, workspace authorization, or PWA update lifecycle.

### Outcome

The CI functional E2E job is reproducible from `npm ci`, uses the configured preview/mock boundary, publishes trace/screenshot artifacts only on failure, and does not treat a missing local server as product proof.

### Architectural decision

Use Playwright against the built SvelteKit preview with the project's supported mock backend where SSR needs server data. Keep the actual backend authorization tests in the backend suite; E2E validates integration and visible browser behavior, not a duplicate authorization implementation.

### Required changes

1. Create a stable CI script selecting functional specs and the appropriate `PLAYWRIGHT_USE_PREVIEW=true` server setup.
2. Ensure SSR requests use a persistent mock/backend fixture rather than only `page.route()` interception.
3. Include login continuation, workspace role boundary, invitation flow, core Telegram auth surface, and PWA service-worker/update behavior; isolate screenshot-diff suites from the default blocking job unless their baselines are deterministic.
4. Upload Playwright trace/report/screenshot artifacts on failure with short retention and no credentials in artifacts.

### Out of scope

- Device-lab testing, production Telegram-client proof, or making visual snapshots a required check before baseline stability is demonstrated.

### Acceptance criteria

- The CI job installs browsers/dependencies deterministically and runs the selected functional E2E set on a clean runner.
- The set proves unauthenticated workspace redirection, authenticated workspace entry, one invitation or continuation boundary, and a service-worker lifecycle assertion.
- A deliberate selector/contract failure produces a private trace artifact and fails the job.
- The job has no dependence on a developer's localhost:5001 process.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- --grep "workspace|auth|PWA"
```

### Commit

```bash
git add .github/workflows/quality.yml apps/web/playwright.config.ts apps/web/package.json apps/web/scripts/preview.mjs apps/web/tests/e2e
git commit -m "test(web): run critical browser flows in ci"
```

## TASK-RR-007: Restore a zero-warning backend static-quality baseline

**Status:** TODO
**Priority:** P2  
**Depends on:** TASK-RR-002

**Exact scope:**

Resolve the currently reported Checkstyle warnings and the concentrated style/complexity debt without changing tool severities, excludes, or coverage thresholds. Start with the affected authentication, Web Push, Telegram, and shared utility classes reported by `mvn validate`; split further commits only if a bounded module cannot remain atomic.

**Files:**

- Modify the exact files reported by `cd apps/backend && ./mvnw -B -ntp validate`, including current anchors `AuthGoogleResource`, `AuthChildAuthService`, `CookieBuilder`, `PublicAuthRateLimitFilter`, `WebPushService`, `TelegramInitDataVerifier`, `TelegramMiniAppAuthService`, `TelegramOutboxProcessor`, `TelegramMenuBuilder`, and `TelegramCopy`.
- Modify focused tests in the corresponding existing test packages when decomposition changes observable behavior.
- Do not modify `apps/backend/config/checkstyle.xml`, `apps/backend/config/spotbugs-exclude.xml`, `apps/backend/config/pmd/backend-srp-ruleset.xml`, or JaCoCo excludes merely to silence findings.

**Goal:**

The backend complies with its checked-in static rules at source level and the most complex control-flow paths have named, testable responsibilities.

### Outcome

Warnings such as unused/star imports, missing braces, line length, parameter count, method count, and cyclomatic complexity are eliminated through code improvements rather than accepted as normal output.

### Architectural decision

Use existing domain/application helpers and small immutable configuration records to reduce parameter and branch count. Preserve public resource contracts and existing service ownership; avoid duplicate orchestration services or an all-purpose utility class.

### Required changes

1. Capture the baseline warning list in the task branch and group fixes by the owning module.
2. Replace wildcard/unused imports and format defects directly; extract cohesive collaborators for actual parameter/method/complexity violations.
3. Add or retain behavioral tests around extracted decision branches, especially authentication, Telegram verification, delivery retries, and Web Push.
4. Leave Checkstyle, PMD, SpotBugs, compiler, JaCoCo configuration and thresholds at least as strict as before.

### Out of scope

- A package-wide rewrite, changing API payloads, or suppressing warnings with annotations/exclusions.

### Acceptance criteria

- `mvn validate` prints no project Checkstyle warnings.
- The full backend `verify` gate passes with unchanged or stricter analysis/coverage configuration.
- Extracted code has behavior-level tests for every previously complex decision path.
- No `@SuppressWarnings`, tool exclusion, `eslint-disable`, or coverage-threshold reduction is introduced.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="/Users/sash/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp validate
cd apps/backend && JAVA_HOME="/Users/sash/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp verify
```

### Commit

```bash
git add apps/backend/src/main/java apps/backend/src/test/java
git commit -m "refactor(backend): remove static quality violations"
```

## TASK-RR-008: Split oversized Svelte dashboard and access-flow responsibilities

**Status:** TODO
**Priority:** P2  
**Depends on:** TASK-RR-006

**Exact scope:**

Decompose `telegram/dashboard/+page.svelte` (1,848 lines) and `ParentInvitationFlow.svelte` (681 lines) at their existing rendering and state boundaries. Preserve route data contracts, i18n ownership, keyboard behavior, responsive layout, and server-side authorization.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte`.
- Create cohesive components under `apps/web/src/lib/features/telegram/dashboard/` for overview metrics, period control, tabs/activity, charts, and accessible tooltip behavior as evidenced by the existing route.
- Modify `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`.
- Create cohesive components under `apps/web/src/lib/features/workspace/access/` for invitation creation, acceptance, membership rendering, and transfer actions; keep API/access orchestration in one explicit feature-level controller or service.
- Modify/add unit and E2E tests under `apps/web/tests/` for extracted behavior.

**Goal:**

The dashboard route and access-flow coordinator become readable owners of their data/navigation boundary, while components own a single visual/interaction concern with explicit props/events.

### Outcome

The rendered UI remains equivalent at desktop and mobile sizes, but future changes no longer require editing a large mixed state/style/markup file.

### Architectural decision

Route loaders and server authorization remain unchanged. Move presentation and local interaction state downward only when it has one owner; do not create a duplicate dashboard/access store or copy route data into independent client state.

### Required changes

1. Characterize the current tab, period navigation, tooltip focus return, error/retry, invitation creation/acceptance, membership, and transfer behaviors before extracting components.
2. Extract components around existing semantic boundaries with typed props and event callbacks rather than boolean-prop matrices.
3. Preserve keyboard navigation, visible focus, screen-reader labels, 44px touch targets, loading/empty/error/retry states, and `scrollWidth <= innerWidth` at mobile widths.
4. Remove obsolete styles/state from the route after each extraction and keep i18n keys in their current shared catalogs.

### Out of scope

- Visual redesign, new analytics features, changing invitation/API contracts, or rewriting all Telegram components.

### Acceptance criteria

- Dashboard tab navigation, period change/retry, tooltip keyboard flow, and invitation/membership transfer behavior remain covered by focused tests.
- At 320px and desktop widths, the routes have no horizontal overflow and primary controls remain keyboard- and touch-reachable.
- Direct `/telegram/dashboard` access still relies on server-provided authorization data; a non-admin is not granted dashboard content by client state.
- `npm run lint`, unit tests, production build, and the affected Playwright specs pass.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test -- --run && npm run build
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- --grep "admin dashboard|workspace invitations|telegram"
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte apps/web/src/lib/features/telegram/dashboard apps/web/src/lib/features/workspace/access apps/web/tests
git commit -m "refactor(web): split dashboard and access flow"
```

## TASK-RR-009: Remove stale local log-tail behavior

**Status:** TODO
**Priority:** P2  
**Depends on:** TASK-RR-005

**Exact scope:**

Remove or replace the unreferenced `ApplicationLogService` path that scans hard-coded local files (`data/logs/app.log`, `logs/app.log`, `backend/data/logs/app.log`) while runtime logging is structured to stdout and optional external monitoring. Retain only a real, authenticated operator path.

**Files:**

- Delete `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/observability/ApplicationLogService.java` and `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/application/observability/ApplicationLogServiceTest.java` when no consumer exists.
- Delete `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ApplicationLogsResponse.java` if it becomes unused.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/ModuleBoundaryTest.java` to remove the deleted type from its explicit platform contract.
- Modify `docs/monitoring/newrelic.md` to name the supported log retrieval path and retention responsibility.

**Goal:**

The repository has one truthful operational log path and no dead code that implies application logs are safely available from arbitrary local files.

### Outcome

Operators use structured stdout/log aggregation and metrics/health endpoints as documented; the backend does not silently return an empty or partially sanitized pseudo-log feed.

### Architectural decision

Log storage and access control belong to the deployment logging platform, not a Quarkus process reading a filesystem convention it does not own. If an admin log view is a real product requirement, define a separately authorized, paginated, audited API in a later feature task.

### Required changes

1. Confirm all production and test consumers with `rg` before deletion.
2. Remove dead DTO/service/tests or replace them only with a documented supported integration.
3. Update monitoring documentation with the actual log source, correlation fields, retention, and access boundary established by TASK-RR-005.

### Out of scope

- Building an in-app log viewer, changing log retention, or exposing operational logs through the family API.

### Acceptance criteria

- No main/test code references the removed log-tail types.
- The monitoring runbook contains no path suggesting application-owned `app.log` files.
- Backend compilation and full `verify` pass.

### Targeted validation

```bash
rg -n 'ApplicationLogService|ApplicationLogsResponse|data/logs/app\.log|logs/app\.log' apps/backend/src || true
cd apps/backend && JAVA_HOME="/Users/sash/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/observability apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/application/observability apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/ModuleBoundaryTest.java docs/monitoring/newrelic.md
git commit -m "refactor(backend): remove stale log tail service"
```

## TASK-RR-010: Keep repository guidance accurate and neutral

**Status:** TODO
**Priority:** P2  
**Depends on:** TASK-RR-001, TASK-RR-002, TASK-RR-004, TASK-RR-005, TASK-RR-006, TASK-RR-007, TASK-RR-008, TASK-RR-009

**Exact scope:**

Update repository documentation only after the implemented contracts are verified. Remove the current self-promotional README sentence and preserve clear product, architecture, security, local-start, verification, and operations guidance.

**Files:**

- Modify `README.md`.
- Modify `docs/monitoring/newrelic.md`, `docs/operations/web-miniapp-access.md`, `apps/backend/docs/ARCHITECTURE.md`, and `apps/web/docs/ARCHITECTURE.md` only where an implemented task changed their stated contract.
- Create `docs/adr/` records only if an implementation introduces a durable decision not already explained in the architecture documents.

**Goal:**

Readers can run, understand, secure, and operate the project using factual documentation that describes the product and its engineering decisions without self-promotional framing.

### Outcome

Documentation matches tested behavior, has no stale references to removed logging/key material, and contains no recruitment-oriented wording.

### Architectural decision

README remains the concise entrypoint; architecture and operations documents own detailed contracts. Do not duplicate implementation history or create process documentation that is not maintained by a real workflow.

### Required changes

1. Replace the recruitment-oriented README sentence with a neutral explanation of the system's engineering boundaries.
2. Reconcile commands, environment variables, security headers, logging/monitoring, CI/E2E, and release-secret guidance with the completed tasks.
3. Check every internal Markdown link and avoid claims that local checks prove remote CI, deployed providers, Telegram-client behavior, or device behavior.
4. Ensure documentation contains no references to the removed signing file, local log-tail implementation, or recruitment framing.

### Out of scope

- Adding a marketing site, badges for unverified services, a job history, or personal biographical material.

### Acceptance criteria

- `README.md` gives a clean local start, verification commands, architecture summary, and security/operations boundaries that match current files.
- The repository documentation has no occurrence of the prohibited recruitment term.
- Markdown links checked by the repository's available link checker (or a documented local script) resolve; `git diff --check` passes.
- No documentation claims a deployment, remote CI run, provider delivery, or Telegram/device proof that was not actually obtained.

### Targeted validation

```bash
! rg -n -i 'port'"folio" README.md docs apps/backend/docs apps/web/README.md
git diff --check
```

### Commit

```bash
git add README.md docs apps/backend/docs apps/web/docs apps/web/README.md
git commit -m "docs: clarify repository operation"
```
