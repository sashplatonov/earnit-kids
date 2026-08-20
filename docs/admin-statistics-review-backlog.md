# Admin Statistics - Implementation Backlog

## Goal

Make the administrator-only Statistics screen in the Telegram Mini App reliably
load real data for 7, 30, and 90 days and all time. Its localized, accessible
mobile layout must follow `docs/earnit_kids_admin_dashboard_reference.html`:
compact period controls, SVG-chart tab icons, fixed bottom tab navigation, and
the reference card hierarchy. The screen title must be **Statistics** / **Статистика**;
the reference's **Key UX** block must not be present.

## Review findings and scope

- `apps/web/src/routes/telegram/dashboard/+page.server.ts` sends the selected
  `period` to `/api/admin/dashboard` and `/api/admin/analytics/trends`, but a
  non-OK dashboard response turns every dashboard-owned payload into `null`.
- The period interpretation is duplicated across eight backend analytics
  services. Seven use their `default` branch as `Instant.EPOCH`; no endpoint
  contract distinguishes the supported `all` value from an invalid value.
- `apps/web/src/routes/telegram/dashboard/+page.svelte` already uses the shared
  `TelegramIcon` SVG component for statistics tabs. Preserve it; do not replace
  the SVG icons with the reference HTML's emoji examples.
- Confirmed untranslated/localization-unsafe dashboard UI includes the
  `Дашборд` title, a hard-coded `ru-RU` timestamp formatter, and the tooltip
  close control's English `aria-label="Close"`.
- The current page renders the excluded `sections.keySignals` block.

## Architectural decisions

- Create one backend-owned `AdminAnalyticsPeriod` value type in
  `service/telegram/admin/`; it is the sole parser for `7d`, `30d`, `90d`, and
  `all` and supplies the requested lower bound. Every dashboard and trends
  service must consume it instead of keeping private `switch` expressions.
- The API boundary validates period input once and returns a documented 400 for
  unsupported values. Omitting `period` remains backward compatible and means
  `30d`; `all` explicitly maps to the lifetime lower bound.
- `/api/admin/dashboard` remains the source for its existing aggregate response;
  do not create one API per tab or duplicate dashboard aggregation in Svelte.
  Preserve its response field names while making a failed optional section
  observable and non-destructive according to the accepted loading contract.
- Server load owns request coordination, URL is the source of truth for the
  selected period, and page state owns only the active tab. Continue to reuse
  `TelegramIcon` and the existing i18n runtime; do not introduce a second icon,
  translation, or formatting system.
- Reference HTML controls visual structure only. Keep real backend values,
  existing explanatory tooltips, authorization, and responsive accessibility;
  omit the requested excluded **Key UX** section.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 | P1 | - | Establish a single, testable period contract before changing consumers. |
| 2 | P1-2 | P1 | P1-1 | Prevent a partial backend failure from making all tabs appear unloaded. |
| 3 | P1-3 | P1 | P1-2 | Make every displayed label and control locale-correct. |
| 4 | P1-4 | P1 | P1-2, P1-3 | Apply the reference layout without changing data sources. |
| 5 | P2-1 | P2 | P1-1, P1-2, P1-3, P1-4 | Lock the regression down across API, UI, and mobile geometry. |

## P1-1: Unify and validate the admin statistics period contract

**Status:** ✅ Completed
**Priority:** P1  
**Depends on:** -

### Outcome

Every Statistics request has exactly one of four meanings: `7d`, `30d`, `90d`,
or `all`. All dashboard sections and trends use the same time lower bound;
invalid direct API parameters do not silently return lifetime data.

### Architectural decision

The backend service layer owns period parsing in a new shared value type. The
dashboard and trends resources use that type at their API boundary; existing
dashboard child services receive the normalized period/value rather than
reparsing strings independently. No schema migration is required.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminAnalyticsPeriod.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/admin/AdminDashboardResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/admin/AdminTrendsResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminDashboardService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminAnalyticsService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminCoinEconomyService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminTaskEconomyService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminParentBehaviorService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminChildBehaviorService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminRetentionService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminRewardsService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminTrendsService.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminDashboardServiceTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminAnalyticsServicesTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram/admin/AdminDashboardResourceTest.java`.

### Work

1. Define the four accepted wire values, their deterministic start instants,
   and the default `30d` behavior in `AdminAnalyticsPeriod`; represent all time
   explicitly instead of relying on a catch-all `default` branch.
2. Parse and reject unsupported `period` query values at both relevant resource
   boundaries with the project-standard client-error response, while preserving
   omitted-period compatibility.
3. Replace every private period parser in the listed dashboard/trends services
   with the shared contract so one selected tab period reaches every aggregate
   query unchanged.
4. Add service and resource tests for all four accepted values, defaulting, and
   invalid values; specifically assert that `all` and malformed input have
   different outcomes.

### Acceptance criteria

- Authenticated administrators receive coherent dashboard and trend results for
  each of `7d`, `30d`, `90d`, and `all`; each section uses the selected period.
- `all` includes lifetime data deliberately, while a malformed value returns a
  client error and never expands the query to lifetime data.
- Omitting `period` continues to select 30 days.
- Non-admin callers remain rejected before analytics data is returned.
- Tests do not depend on a moving wall-clock assertion beyond the intended
  period boundaries.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/{resource/telegram/admin/AdminDashboardResource.java,resource/telegram/admin/AdminTrendsResource.java,service/telegram/admin} apps/backend/src/test/java/com/sashplatonov/earnit/kids/{resource/telegram/admin/AdminDashboardResourceTest.java,service/telegram/admin}
git commit -m "fix(backend): Unify statistics periods"
```

## P1-2: Make period loading resilient and diagnosable per Statistics section

**Status:** ⬜ Not started  
**Priority:** P1  
**Depends on:** P1-1

### Outcome

Switching any period always gives the administrator an honest result: populated
sections, an explicit section-level unavailable state, or a retryable full-page
failure. A single optional analytics failure no longer masquerades as all data
being absent.

### Architectural decision

`+page.server.ts` continues to coordinate the two existing aggregate endpoints.
`AdminDashboardService` owns composition of dashboard fields; introduce no
parallel frontend calculations. The backend response/loading contract must
make the failing section identifiable without exposing internal exceptions.

### Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminDashboardService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminDashboardResponse.java` only if a backward-compatible section-status contract is required.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminDashboardServiceTest.java`.
- Modify `apps/web/src/routes/telegram/dashboard/+page.server.ts`.
- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte`.
- Create `apps/web/tests/unit/adminDashboardLoad.test.ts`.

### Work

1. Reproduce the reported load matrix with controlled service failures and all
   four period values, distinguishing endpoint errors, JSON parse errors, and
   genuine zero-activity data.
2. Define and implement the smallest backward-compatible dashboard response
   behavior that preserves successful sections when one independent aggregate
   is unavailable; retain a single unavailable page state only when no usable
   dashboard data exists.
3. In server load, preserve the selected period and expose safe, structured
   availability information for dashboard and trends rather than discarding
   all dashboard fields after one non-OK response.
4. In Svelte, render per-panel loading/unavailable/empty states with a retry
   action that preserves `?period=` and the active tab where the router allows;
   never label unavailable data as zero activity.
5. Add focused backend and frontend unit coverage for each failure mode and
   for a successful `7d`/`30d`/`90d`/`all` navigation response.

### Acceptance criteria

- A click on every period control triggers requests carrying its exact period
  and shows data for all available tabs after navigation completes.
- A trends failure leaves dashboard tabs usable and identifies only trends as
  unavailable; an isolated dashboard-section failure does not erase unrelated
  sections.
- A true zero-data period uses the localized empty state, not an error state.
- An unavailable state is keyboard reachable, announces its status, and retry
  keeps the selected period.
- Internal exception text and stack traces are never rendered to an admin.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/{service/telegram/admin/AdminDashboardService.java,dto/response/AdminDashboardResponse.java} apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminDashboardServiceTest.java apps/web/src/routes/telegram/dashboard/+page.{server.ts,svelte} apps/web/tests/unit/adminDashboardLoad.test.ts
git commit -m "fix(web): Resilient statistics loading"
```

## P1-3: Complete Statistics localization and accessible labels

**Status:** ⬜ Not started  
**Priority:** P1  
**Depends on:** P1-2

### Outcome

Every visible Statistics title, description, empty state, control, tooltip, and
assistive label is sourced from the selected locale. Russian calls the page
`Статистика`; English calls it `Statistics`.

### Architectural decision

The existing `admin.dashboard` message domain remains the sole copy source.
Use the current i18n runtime and its active locale for date/time formatting;
do not embed Russian strings or create dashboard-local message objects.

### Files

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte`.
- Modify `apps/web/src/lib/i18n/messages/ru/admin.ts`.
- Modify `apps/web/src/lib/i18n/messages/en/admin.ts`.
- Create or extend the focused i18n/dashboard unit test under `apps/web/tests/unit/`.

### Work

1. Rename dashboard page and tab-list language to Statistics in both locales,
   including document title and screen-reader labels.
2. Replace every dashboard literal and locale-specific formatter, including the
   tooltip close label and refreshed timestamp, with typed message keys and
   the active locale.
3. Audit all message keys consumed by `+page.svelte` in Russian and English;
   add parity coverage so neither locale falls back to an English string or a
   key name for Statistics UI.
4. Preserve internal identifiers (`overview`, `coins`, and period wire values)
   as non-localized domain values.

### Acceptance criteria

- In Russian, page title, tab-list aria label, updated time, tooltip close
  action, and all visible Statistics copy are Russian; in English they are
  English.
- No rendered Statistics copy is a raw i18n key or a fallback-language string.
- The timestamp obeys the active locale, while the API period remains unchanged.
- Tooltip close is announced with a localized accessible name and works by
  keyboard.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte apps/web/src/lib/i18n/messages/{ru,en}/admin.ts apps/web/tests/unit
git commit -m "fix(i18n): Localize statistics screen"
```

## P1-4: Align the Statistics mobile UI with the supplied reference

**Status:** ⬜ Not started  
**Priority:** P1  
**Depends on:** P1-2, P1-3

### Outcome

The authenticated Mini App Statistics screen visually and structurally matches
the supplied compact reference at phone widths while retaining real data,
SVG-icon tabs, tooltips, and all current analytics sections. It omits the
reference's **Key UX** block.

### Architectural decision

The existing single Svelte route remains the presentation owner. Reuse its
`TelegramIcon` tabs and CSS rather than importing reference HTML or creating a
second dashboard component; only reshape markup/styles where needed.

### Files

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramIcon.svelte` only if a referenced SVG icon is absent from the shared set.

### Work

1. Match the reference shell: compact header and period segment, white metric
   cards with borders/radii/typography, reference spacing, empty card, metric
   rows, funnel tracks, and safe-area bottom clearance.
2. Turn the five analytics controls into the reference-style fixed bottom tab
   navigation on the Mini App surface, retaining `role=tab`, selected state,
   SVG chart icons, visible focus, and non-clipped labels.
3. Keep desktop usable without the phone mockup; at 320px and 350px retain
   five reachable controls, no horizontal page overflow, and content above the
   bottom bar remains reachable.
4. Remove the `sections.keySignals` heading and rows from the overview and
   remove now-unused page-only styles/message keys only when no other consumer
   uses them. Do not add a **Key UX** replacement.
5. Preserve every other real-data section in the order and visual treatment of
   the reference; use existing localized empty/unavailable states where data is
   missing.

### Acceptance criteria

- The screen heading is Statistics, period selector sits above the content, and
  the five tabs have graphical SVG icons rather than emoji.
- On a 320px viewport, all four period controls and five bottom tabs are visible
  or horizontally reachable without clipping labels or causing page overflow;
  each interactive control is at least 44x44px.
- On phone viewports, the fixed tab bar respects `safe-area-inset-bottom` and
  does not cover the final card or tooltip controls.
- Selected tab has visible selected and focus states, supports keyboard
  navigation, and updates the matching tab panel.
- The overview has no **Key UX** / `Ключевые сигналы` section; all other
  reference-required dashboard sections retain real API data.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte apps/web/src/lib/components/telegram/TelegramIcon.svelte
git commit -m "feat(web): Match statistics reference UI"
```

## P2-1: Add end-to-end Statistics period, locale, and responsive regression coverage

**Status:** ⬜ Not started  
**Priority:** P2  
**Depends on:** P1-1, P1-2, P1-3, P1-4

### Outcome

Automated browser coverage prevents regressions where only some period tabs
load, text falls back to the wrong locale, or compact UI hides a control.

### Architectural decision

Extend the existing Playwright admin-session conventions from
`apps/web/tests/e2e/roles.spec.ts`; test the real `/telegram/dashboard` route
instead of duplicating API behavior in a mocked visual test. Backend service
tests remain the source for period math and authorization.

### Files

- Create `apps/web/tests/e2e/admin-dashboard.spec.ts`.
- Modify `apps/web/tests/e2e/roles.spec.ts` only if its existing admin login
  helper must be extracted for reuse.
- Modify focused backend tests from P1-1/P1-2 if coverage gaps remain.

### Work

1. Add an administrator flow that visits Statistics and switches 7/30/90/all,
   asserting URL, selected control, successful section content, and no raw
   fallback keys.
2. Cover a localized tooltip and an unavailable/retry scenario using the
   project-supported deterministic test seam; do not rely on production data.
3. Run at compact mobile and desktop viewports, asserting bottom navigation
   bounds, 44px target sizes, no horizontal overflow, keyboard tab behavior,
   and unobscured final content.
4. Retain the non-admin redirect assertion so the visual change does not weaken
   the existing server-side authorization boundary.

### Acceptance criteria

- The E2E suite proves all four period selections load their matching state on
  an authenticated admin route.
- The suite proves SVG tab controls, localized accessible labels, and absence
  of the excluded Key UX section.
- At 320px and desktop widths, required controls are within the viewport or
  intentionally scrollable containers, with no document-level horizontal
  overflow.
- A non-admin cannot use Statistics data through the Mini App route.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e -- admin-dashboard.spec.ts
```

### Commit

```bash
git add apps/backend/src/test apps/web/tests/e2e/admin-dashboard.spec.ts apps/web/tests/e2e/roles.spec.ts
git commit -m "test(web): Cover admin statistics tabs"
```

## Final quality gate

Run the full affected checks after all task commits; inspect the rendered
Mini App locally at 320px, 350px, and a desktop viewport. Report browser
evidence separately from build and test evidence.

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e -- admin-dashboard.spec.ts
```
