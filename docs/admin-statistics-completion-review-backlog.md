# Admin Statistics Completion Review - Remediation Backlog

## Goal

Complete the delivered Admin Statistics work so every period-scoped dashboard
section is calculated from the selected period, mobile help controls are
accessible, and the unrelated end-to-end coverage removed while adding the
Statistics spec is restored.

## Review scope and evidence

Reviewed the completed tasks in `docs/admin-statistics-review-backlog.md` and
their implementation commits `c1c91b0e`, `c2139994`, `449c31c5`, `d9b0a25e`,
and `f03c1462`. The current worktree was clean.

Confirmed findings:

- `AdminDashboardService` receives `AdminAnalyticsPeriod`, but invokes
  `AdminActivationFunnelService.getActivationFunnel()` without it. The service
  and repository use lifetime-only queries, so the funnel does not change for
  `7d`, `30d`, or `90d`, violating completed task P1-1's requirement that each
  dashboard section use the selected period.
- Commit `f03c1462` deleted seven established, unrelated E2E specifications
  while extending `admin-dashboard.spec.ts`. This removes regression coverage
  for analytics, app sections, parent access, roles, smoke navigation, and
  task/shop UI; the Statistics task did not authorize their removal.
- `+page.svelte` renders help triggers at 20x20 px (`.info`) and 16x16 px
  (`.mini-info`), below the 44x44 px touch-target requirement. Closing the
  conditionally rendered tooltip only clears `activeTooltip`; it does not
  restore focus to the trigger, leaving keyboard focus on removed content.

## Architectural decisions

- The existing `AdminAnalyticsPeriod` remains the only period contract. The
  activation funnel must receive that value through dashboard service, funnel
  service, and repository; do not create a dashboard-local period parser or a
  second endpoint.
- Define the funnel as a cohort of families registered on or after the
  selected lower bound. Every stage is calculated against that cohort, which
  keeps the stage sequence meaningful; `all` uses the existing lifetime
  lower bound. The response DTO remains backward-compatible.
- Restore the deleted E2E files from the pre-`f03c1462` version and retain the
  new Statistics scenarios as a separate spec. Do not replace independent
  product coverage with assertions about the administrator dashboard.
- Tooltip interaction stays a single Svelte state source. Its controls must
  have real 44x44 px hit targets without hiding labels or creating page
  overflow, and the existing trigger owns focus restoration.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 | P1 | - | Correct the shared backend period contract before testing the page. |
| 2 | P2-1 | P2 | - | Make the compact Statistics interactions accessible without changing data contracts. |
| 3 | P2-2 | P2 | - | Restore independent regression coverage without changing Statistics behavior. |

## P1-1: Scope the activation funnel to the selected Statistics period

**Status:** ✅ Completed
**Priority:** P1  
**Depends on:** -

### Outcome

The activation funnel changes coherently when an administrator switches
between `7d`, `30d`, `90d`, and `all`; it no longer presents lifetime figures
beside period-filtered dashboard sections.

### Architectural decision

`AdminDashboardService` passes the normalized `AdminAnalyticsPeriod` to
`AdminActivationFunnelService`, which passes its start instant to one
repository-owned cohort query path. The cohort is families registered in the
selected period; all funnel stages are counted only for that cohort. This
preserves the existing wire response and makes `all` explicit via
`Instant.EPOCH`.

### Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminDashboardService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminActivationFunnelService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/AdminAnalyticsRepository.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminDashboardServiceTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/repository/AdminAnalyticsRepositoryTest.java`.

### Work

1. Change the funnel service and repository contract to accept the normalized
   period start; pass it from the dashboard composition path.
2. Apply the selected start to the registered-family cohort and every joined
   stage query so later-stage counts cannot include families outside that
   cohort. Retain `all` as the lifetime query path through the same contract.
3. Keep stage order, percentage calculations, labels, and
   `AdminActivationFunnelResponse` compatibility unchanged.
4. Add service and repository regression tests with an older family and a
   recent family, proving the `7d` cohort excludes historical funnel activity
   while `all` includes both.

### Acceptance criteria

- Requests for `7d`, `30d`, and `90d` produce funnel counts for families
  registered on or after the corresponding `AdminAnalyticsPeriod.start()`.
- `all` retains lifetime funnel counts, and no malformed period reaches a
  funnel query.
- No funnel stage counts a family outside its selected registered-family
  cohort; percentages remain derived from those filtered counts.
- The public dashboard response shape and administrator authorization remain
  unchanged.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest=AdminDashboardServiceTest,AdminAnalyticsRepositoryTest,AdminAnalyticsPeriodTest
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/{service/telegram/admin/AdminDashboardService.java,service/telegram/admin/AdminActivationFunnelService.java,repository/AdminAnalyticsRepository.java} apps/backend/src/test/java/com/sashplatonov/earnit/kids/{service/telegram/admin/AdminDashboardServiceTest.java,repository/AdminAnalyticsRepositoryTest.java}
git commit -m "fix(backend): Scope activation funnel periods"
```

## P2-1: Make Statistics help controls touch- and keyboard-accessible

**Status:** ⬜ Not started  
**Priority:** P2  
**Depends on:** -

### Outcome

Every Statistics help control is comfortably tappable on a phone, and closing
its explanatory popover returns keyboard focus to the control that opened it.

### Architectural decision

`activeTooltip` remains the page's only help-popover state. Record the
triggering button when opening a tooltip, focus the close control after the
popover renders, and restore focus after it is dismissed by Close or Escape.
Use actual 44x44 px button boxes and compact layout styling rather than a
pointer-only pseudo-element hit area.

### Files

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte`.
- Modify `apps/web/tests/e2e/admin-dashboard.spec.ts`.

### Work

1. Resize `.info`, `.mini-info`, and the tooltip close action to at least
   44x44 CSS pixels, preserving label alignment and the 320px no-overflow
   contract.
2. Keep the opener element when a tooltip is shown; after DOM update, move
   focus to the close action. On Close or Escape, clear the popover and return
   focus to that opener.
3. Ensure the dialog keeps its localized accessible name and does not obscure
   the fixed tab bar or final content at compact widths.
4. Extend the real administrator E2E flow to assert button geometry, focus
   transfer, Escape/close behavior, restored focus, and no horizontal
   document overflow at 320px.

### Acceptance criteria

- All visible information and tooltip-close buttons have bounding boxes of at
  least 44x44 px at 320px and desktop widths.
- Opening a tooltip from the keyboard focuses its close action; Close and
  Escape both dismiss it and restore focus to the same trigger.
- The trigger and popover retain localized names, visible focus, and normal
  pointer interaction.
- The enlarged hit targets do not cover tab labels, create document-level
  horizontal overflow, or hide final dashboard content behind the bottom bar.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- admin-dashboard.spec.ts
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte apps/web/tests/e2e/admin-dashboard.spec.ts
git commit -m "fix(web): Improve Statistics help access"
```

## P2-2: Restore the unrelated E2E suites removed by the Statistics commit

**Status:** ⬜ Not started  
**Priority:** P2  
**Depends on:** -

### Outcome

The project again runs its established browser coverage for non-Statistics
features while retaining the new `admin-dashboard.spec.ts` scenarios.

### Architectural decision

Each product area keeps its own Playwright specification. Recover the exact
pre-`f03c1462` suites, then make only compatibility updates required by the
current UI; do not fold their cases into the administrator-dashboard file or
weaken assertions simply to obtain a passing run.

### Files

- Create `apps/web/tests/e2e/analytics.spec.ts` from its pre-`f03c1462` version.
- Create `apps/web/tests/e2e/app-sections.spec.ts` from its pre-`f03c1462` version.
- Create `apps/web/tests/e2e/parent-access-settings.spec.ts` from its pre-`f03c1462` version.
- Create `apps/web/tests/e2e/roles.spec.ts` from its pre-`f03c1462` version.
- Create `apps/web/tests/e2e/smoke.spec.ts` from its pre-`f03c1462` version.
- Create `apps/web/tests/e2e/tasks-shop-ui.spec.ts` from its pre-`f03c1462` version.

### Work

1. Restore the six deleted product E2E specifications from the parent of
   `f03c1462`; retain their data setup, assertions, and helper use.
2. Run each recovered spec in isolation. If current, intentional UI behavior
   requires an update, make the smallest assertion change that preserves the
   original product guarantee and document it in the commit.
3. Keep `admin-dashboard.spec.ts` and do not delete or skip an unrelated
   suite as part of this remediation.

### Acceptance criteria

- All six recovered spec files are present and discovered by Playwright.
- The project retains separate E2E coverage for child analytics, app sections,
  parent access/settings, roles, smoke navigation, and task/shop UI.
- Each recovered suite passes against the current application, or a concrete
  product defect is reported separately rather than hidden by removing tests.
- The Statistics E2E spec remains present and runnable.

### Verification

```bash
cd apps/web && npm run test:e2e -- analytics.spec.ts app-sections.spec.ts parent-access-settings.spec.ts roles.spec.ts smoke.spec.ts tasks-shop-ui.spec.ts admin-dashboard.spec.ts
```

### Commit

```bash
git add apps/web/tests/e2e/{analytics,app-sections,parent-access-settings,roles,smoke,tasks-shop-ui}.spec.ts
git commit -m "test(web): Restore E2E regression suites"
```

## Rejected observations

- `AdminAnalyticsPeriod` contains `Instant.now()` and therefore is not a
  stable cache key for `@CacheResult`. This is a confirmed cache-efficiency
  concern, but it does not currently violate the Statistics product contract;
  it is intentionally excluded from this remediation backlog.
- Focused unit and service tests pass, but they are not evidence of a running
  browser, production PWA update behavior, remote CI, or deployed-environment
  behavior.

## Review verification

```text
cd apps/web && npm run test -- --run tests/unit/adminDashboardLoad.test.ts tests/unit/adminStatisticsLocalization.test.ts
Result: PASS — 2 files, 5 tests.

cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest=AdminAnalyticsPeriodTest,AdminDashboardResourceTest,AdminDashboardServiceTest,AdminAnalyticsServicesTest
Result: PASS — 13 tests.

git status --short && git diff --check
Result: clean before this documentation-only review edit.
```

No production source code was changed in this review. The new backlog is the
only deliverable and has not been committed.
