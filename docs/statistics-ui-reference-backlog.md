# Statistics UI Reference - Implementation Backlog

## Goal

Make `/telegram/dashboard` closely match `docs/statistics-all-screens-period-buttons-fixed.html` as a mobile-first Statistics screen. Keep authenticated analytics APIs, period URLs, and fallback states; this is a presentation/interaction backlog, not an analytics-data redesign.

## Architectural decisions

- `apps/web/src/routes/telegram/dashboard/+page.svelte` is the sole presentation owner. Reuse its page-local state, `TelegramIcon`, `activeTab`, `selectedPeriod`, tooltip, and retry mechanisms; do not add another route, store, resolver, or client aggregation layer.
- `/api/admin/dashboard?period=` and `/api/admin/analytics/trends?period=` remain the only data sources. Preserve canonical `7d`, `30d`, `90d`, `all`, the `30d` fallback, and partial rendering through `Promise.allSettled`.
- Use only values already returned to the page. Missing values keep the localized unavailable/empty state; they must not become fabricated metrics.
- The reference controls visual hierarchy: soft-gray canvas, white rounded surfaces, four-period segment, five graphical primary tabs, and three Activity subtabs. Primary icons stay `TelegramIcon` SVGs rather than emoji.
- Visible text stays in matching `adminMessages.dashboard` locale trees. The existing parity test protects this contract.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | STAT-UI-001 | P1 | - | Shared surface system |
| 2 | STAT-UI-002 | P1 | STAT-UI-001 | Header and period segment |
| 3 | STAT-UI-003 | P1 | STAT-UI-001 | Fixed primary navigation |
| 4 | STAT-UI-004 | P1 | STAT-UI-001 | Overview presentation |
| 5 | STAT-UI-005 | P1 | STAT-UI-001 | Coins presentation |
| 6 | STAT-UI-006 | P1 | STAT-UI-001 | Rewards presentation |
| 7 | STAT-UI-007 | P1 | STAT-UI-001 | Tasks presentation |
| 8 | STAT-UI-008 | P1 | STAT-UI-002, STAT-UI-003 | Activity subtab control |
| 9 | STAT-UI-009 | P1 | STAT-UI-008 | Activation view |
| 10 | STAT-UI-010 | P1 | STAT-UI-008 | Retention view |
| 11 | STAT-UI-011 | P1 | STAT-UI-008 | Needs view |
| 12 | STAT-UI-012 | P2 | STAT-UI-004, STAT-UI-005, STAT-UI-006, STAT-UI-007, STAT-UI-009, STAT-UI-010, STAT-UI-011 | Browser interaction coverage |
| 13 | STAT-UI-013 | P2 | STAT-UI-012 | Visual-regression baselines |

## STAT-UI-001: Define shared Statistics surfaces and spacing

**Status:** DONE
**Priority:** P1  
**Depends on:** -

**Exact scope:**

Replace only shared route CSS: page canvas/container, white bordered surfaces, radii, compact spacing, typography, and space above fixed navigation.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (style selectors `dashboard-container`, `kpi`, `metric-list`, `rows`, `compare`, `funnel`, `trend`, `empty-note`, `empty-state`).

**Goal:**

All panels use the reference's soft-gray canvas and consistent white rounded cards before individual panel composition changes.

### Outcome

Shared blocks have one visual system and final content remains scrollable above fixed navigation.

### Architectural decision

Keep CSS local to the route because these analytics surfaces are not catalog primitives. Do not extract a generic card component or change data bindings.

### Required changes

1. Align canvas, surface border/radius/shadow, text hierarchy, and vertical rhythm with the reference's phone/card/list/chart treatment.
2. Normalize metric, list, chart, funnel, and empty states onto those shared surfaces without altering their conditionals.
3. Reserve bottom space based on the fixed tab bar and safe-area inset.

### Out of scope

- Header, period selector, primary tabs, panel markup, API, or localization changes.

### Acceptance criteria

- At 320px and 1280px, panels are white, lightly bordered, and rounded on a soft-gray page background.
- The last panel item scrolls above fixed navigation; no page-level horizontal overflow appears.
- Existing unavailable and no-data branches remain visible and readable.

### Targeted validation

```bash
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte
git commit -m "style(web): define statistics surface system"
```

## STAT-UI-002: Align Statistics header and period segment

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-001

**Exact scope:**

Refine the existing Back/title/subtitle and four `seg` period buttons to match the reference header and segment proportions.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (`dashboard-header`, `back-btn`, `toolbar`, `segment`, `seg`, `updated`).

**Goal:**

The first viewport has the reference's concise heading and a visually grouped four-option range selector.

### Outcome

The selector remains a URL-backed data reload rather than a local filter.

### Architectural decision

Reuse `changePeriod`, `selectedPeriod`, and localized existing labels. Add no state, query parameter, or time calculation.

### Required changes

1. Match reference title/subtitle spacing, selector padding, segment radius, muted unselected text, white selected cell, and selected shadow.
2. Make four equal cells readable at 320px and position update time so it does not compete with them.
3. Retain Back navigation, visible focus, and `aria-pressed`.

### Out of scope

- Period values/default, loader fetches, or tab navigation.

### Acceptance criteria

- Four localized controls fit in one group at 320px, are each at least 44px high, and do not overflow horizontally.
- Each click preserves exactly `?period=7d|30d|90d|all`; only the selected control has `aria-pressed="true"`.
- The active segment has reference-like white/primary treatment and visible keyboard focus.

### Targeted validation

```bash
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte
git commit -m "style(web): align statistics period selector"
```

## STAT-UI-003: Align fixed five-tab navigation

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-001

**Exact scope:**

Visually align the existing fixed `tabs-wrap` with the reference bottom navigation while retaining its accessible SVG tab implementation.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (`tabs-wrap`, `tabs`, `tab`, `tab-ico`, `tab-label`, focus styles).

**Goal:**

Overview, Coins, Rewards, Tasks, and Activity are compact graphical controls anchored at the bottom of the Mini App.

### Outcome

The reference's selected/unselected hierarchy is achieved without losing `role="tablist"` semantics.

### Architectural decision

Keep the existing `tabs` array, `switchTab`, and `handleTabKeydown`. Do not replace `TelegramIcon` SVGs with emoji.

### Required changes

1. Set reference-like dimensions, gap, border, radius, label wrapping, active fill, translucent fixed background, and safe-area padding.
2. Keep five equal columns and prevent localized labels from escaping their tab.
3. Preserve existing Arrow/Home/End focus and `aria-selected` behaviour.

### Out of scope

- Per-tab routes, label changes, or panel-content changes.

### Acceptance criteria

- Five SVG-icon controls are visible at 320px, each at least 44px high, with no document horizontal overflow.
- Active state is unambiguous; Arrow/Home/End selects the expected panel and moves focus.
- Fixed navigation retains safe-area padding and never covers the final content.

### Targeted validation

```bash
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte
git commit -m "style(web): align statistics tab navigation"
```

## STAT-UI-004: Compose the Overview panel like the reference

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-001

**Exact scope:**

Reorder and style only `panel-overview` into the reference's optional signal, 2×2 KPI grid, and shop-state list using already-loaded overview, coin, and reward values.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (`panel-overview` and a page-local helper only if needed).
- Modify `apps/web/src/lib/i18n/messages/ru/admin.ts` and `apps/web/src/lib/i18n/messages/en/admin.ts` only for missing overview labels.
- Modify `apps/web/tests/unit/adminStatisticsLocalization.test.ts` only if messages change.

**Goal:**

Overview gives the reference's immediate summary: signal, four family/child KPIs, then concise shop state.

### Outcome

Lifetime and selected-period semantics remain explicit while hierarchy improves.

### Architectural decision

Read page data directly; never calculate a backend-like aggregate in the browser. If a source is unavailable, retain the localized fallback rather than invent a partial row.

### Required changes

1. Show a data-derived signal banner only when real inputs exist.
2. Retain total/active family and child cards with current tooltip triggers and lifetime/period footnotes.
3. Add shop-state rows only for existing earned/spent/reward values with correct localization and number formatting.

### Out of scope

- Coins, Rewards, Tasks, Activity, API response shape, or analytics formulas.

### Acceptance criteria

- Overview order is signal when supported, two-by-two KPIs, then shop-state rows.
- Totals stay lifetime, active metrics stay selected-period, and null data uses the established placeholder/unavailable state.
- Existing help controls stay 44px, keyboard operable, and restore focus after dialog close.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/adminStatisticsLocalization.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte apps/web/src/lib/i18n/messages/ru/admin.ts apps/web/src/lib/i18n/messages/en/admin.ts apps/web/tests/unit/adminStatisticsLocalization.test.ts
git commit -m "feat(web): compose statistics overview"
```

## STAT-UI-005: Compose the Coins panel like the reference

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-001

**Exact scope:**

Rework only `panel-coins` into earned/spent cards, a health signal, reward-path rows, and a compact existing-trends chart surface.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (`panel-coins`, `buildCoinInsight`, and Coins trend markup).

**Goal:**

Coins makes the earnings-to-spending cycle and path-to-reward values as scannable as the reference economy screen.

### Outcome

Existing data and tooltips receive reference layout; no metric or request is added.

### Architectural decision

Reuse `coinEconomy`, `trends`, `trendsStatus`, `formatValue`, and `buildCoinInsight`. Coin/trend failure remains independently retryable.

### Required changes

1. Render earned/spent as two period-scoped cards and retain spend-rate explanation.
2. Use the existing derived insight only when a real trigger exists.
3. Order median balance, first reward, and earning-not-spending as compact tooltip-enabled rows.
4. Render existing daily earned/spent points as a titled chart only when trends are available.

### Out of scope

- Backend trend changes, interpolation, Rewards redesign, or Activity charts.

### Acceptance criteria

- Coins shows earned/spent cards, conditional real-data signal, reward-path rows, and bounded daily chart in reference order.
- Coin/percent formatting is unchanged; empty data is never represented as a successful chart.
- Coin and trend failures remain localized and retryable; existing info controls are 44px or larger.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/adminDashboardLoad.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte
git commit -m "feat(web): compose statistics coins panel"
```

## STAT-UI-006: Compose the Rewards panel like the reference

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-001

**Exact scope:**

Refine only `panel-rewards` into request/issued KPI cards, a price/conversion list, and a bounded signal/ranking surface.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (`panel-rewards`).
- Modify `apps/web/src/lib/i18n/messages/ru/admin.ts` and `apps/web/src/lib/i18n/messages/en/admin.ts` only for missing visible labels.
- Modify `apps/web/tests/unit/adminStatisticsLocalization.test.ts` only if messages change.

**Goal:**

Reward conversion, prices, and child preferences read like the reference Rewards screen without masking absent rankings.

### Outcome

The tab has a clear card/list rhythm and handles no-ranking responses deliberately.

### Architectural decision

Use existing `rewards.metrics` and `rewards.rankings`; do not infer rates or construct a ranking from unrelated records.

### Required changes

1. Keep request and issued counts as a two-card group with current context.
2. Present median price, selected price, and failed rate as separated rows.
3. Render ranking rows in a contained list or a localized empty surface when rankings are absent.
4. Add a signal only if existing reward/coin fields support a truthful localized statement.

### Out of scope

- New reward fields, ranking logic, Task changes, or Activity work.

### Acceptance criteria

- Rewards order is KPIs, Prices list, then Signals/preferences surface.
- Empty rankings do not leave a blank bordered container; unavailable rewards retain the current retry state.
- Prices preserve coin notation, failed rate remains percent-formatted, and all new text localizes in both locales.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/adminStatisticsLocalization.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte apps/web/src/lib/i18n/messages/ru/admin.ts apps/web/src/lib/i18n/messages/en/admin.ts apps/web/tests/unit/adminStatisticsLocalization.test.ts
git commit -m "feat(web): compose statistics rewards panel"
```

## STAT-UI-007: Compose the Tasks panel like the reference

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-001

**Exact scope:**

Refine only `panel-tasks` into completion/approval KPIs, a Content list, optional patterns, and a data-derived signal.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (`panel-tasks`).
- Modify `apps/web/src/lib/i18n/messages/ru/admin.ts` and `apps/web/src/lib/i18n/messages/en/admin.ts` only for missing visible labels.
- Modify `apps/web/tests/unit/adminStatisticsLocalization.test.ts` only if messages change.

**Goal:**

Task completion and content adoption follow the reference Tasks screen rather than an undifferentiated list.

### Outcome

Existing task metrics retain their definitions while their hierarchy becomes easy to scan.

### Architectural decision

Use `taskEconomy.taskMetrics` and optional `topPatterns` only. Do not replace a family percentage with configured-count data or add client-side scoring.

### Required changes

1. Keep completion and approval in the two-card group, including approval tooltip.
2. Present catalog usage, custom content, and coins per task as Content rows in reference order.
3. Keep optional patterns bounded and show an informational signal only where existing data truthfully supports it.

### Out of scope

- Task definitions, catalog data, backend calculations, or Activity navigation.

### Acceptance criteria

- Tasks shows KPI cards, Content rows, optional popular patterns, and conditional signal in reference order.
- Approval remains a percentage with its accessible explanation; absent patterns create no blank surface.
- At 320px, labels and values fit without horizontal overflow or hidden bottom content.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/adminStatisticsLocalization.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte apps/web/src/lib/i18n/messages/ru/admin.ts apps/web/src/lib/i18n/messages/en/admin.ts apps/web/tests/unit/adminStatisticsLocalization.test.ts
git commit -m "feat(web): compose statistics tasks panel"
```

## STAT-UI-008: Add accessible Activity subtab navigation

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-002, STAT-UI-003

**Exact scope:**

Add page-local Activity view selection and a three-item reference-style segment inside `panel-activity`; do not move existing Activity content yet.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (Activity state, subtab markup, keyboard handler, and styles).
- Modify `apps/web/src/lib/i18n/messages/ru/admin.ts` and `apps/web/src/lib/i18n/messages/en/admin.ts` (labels and ARIA name).
- Modify `apps/web/tests/unit/adminStatisticsLocalization.test.ts`.

**Goal:**

Activity has the reference's Activation, Retention, and Needs controls with complete accessible-tab behaviour.

### Outcome

Selection is transient state: it neither reloads the page nor alters the period URL.

### Architectural decision

Add typed `activeActivitySubtab` and local roving-keyboard handling analogous to the primary tabs. Do not persist it in a query parameter, store, cookie, or response.

### Required changes

1. Add localized three-item `role="tablist"` visible only while Activity is active.
2. Implement click, Enter/Space, Arrow, Home, End, focus, `aria-selected`, `aria-controls`, and one active panel.
3. Style the selector as a padded soft segment with white selected cell and 44px targets.

### Out of scope

- Moving Activity content, changing data conditions, API calls, or primary-tab behaviour.

### Acceptance criteria

- Exactly three localized internal tabs appear only under Activity; Activation is initially selected.
- Keyboard navigation wraps, focuses the selected tab, and leaves exactly one `aria-selected="true"`.
- Switching does not navigate, refetch, or change `?period=`.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/adminStatisticsLocalization.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte apps/web/src/lib/i18n/messages/ru/admin.ts apps/web/src/lib/i18n/messages/en/admin.ts apps/web/tests/unit/adminStatisticsLocalization.test.ts
git commit -m "feat(web): add statistics activity navigation"
```

## STAT-UI-009: Isolate the Activation Activity view

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-008

**Exact scope:**

Move the existing activation funnel and its unavailable/no-data/retry branches into the Activation subtab panel and style it like the reference funnel screen.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (activation portion of `panel-activity`).

**Goal:**

Activation opens to one focused funnel instead of an all-Activity scroll.

### Outcome

Funnel stages, percentages, and no-data semantics are unchanged while layout matches the reference.

### Architectural decision

Reuse `activationFunnel`, `sectionUnavailable('activation')`, and `retry`. Add no calculation, transformation, or request.

### Required changes

1. Guard the existing funnel branch with the Activation subtab panel.
2. Present stages as compact label/count/percentage/track rows.
3. Keep localized unavailable and no-data states bounded and retryable in this panel.

### Out of scope

- Retention/Needs content, funnel calculations, or new activation events.

### Acceptance criteria

- The funnel appears only in selected Activation and exposes a semantically selected panel.
- Counts and widths remain response-driven; no-data and unavailable states are distinct and retryable.
- At 320px, labels and values stay inside the surface without horizontal overflow.

### Targeted validation

```bash
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte
git commit -m "feat(web): compose statistics activation view"
```

## STAT-UI-010: Isolate the Retention Activity view

**Status:** DONE
**Priority:** P1  
**Depends on:** STAT-UI-008

**Exact scope:**

Move existing retention metrics and current trend cards into the Retention subtab panel, matching the reference retention/daily-activity composition.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (retention and trends portions of `panel-activity`).

**Goal:**

Retention shows its measures first and daily activity below as a separate concise view.

### Outcome

Retention stays visible when trends fail.

### Architectural decision

Reuse `retention`, `trends`, `trendsStatus`, `sectionUnavailable('retention')`, bar helpers, and `retry`; do not merge availability states.

### Required changes

1. Guard retention rows with Retention and retain their current unavailable branch.
2. Move active-family and earned/spent charts below them in titled compact surfaces.
3. Preserve independent trend unavailable/no-point states below successful retention content.

### Out of scope

- New trend endpoints, non-Statistics charts, or Activation/Needs work.

### Acceptance criteria

- Retention selected view orders metrics before daily activity charts.
- A failed trends response leaves successful retention rows readable and displays a localized retryable chart state.
- Bar labels remain within the 320px viewport without page-level horizontal overflow.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/adminDashboardLoad.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte
git commit -m "feat(web): compose statistics retention view"
```

## STAT-UI-011: Isolate the Needs Activity view

**Status:** TODO  
**Priority:** P1  
**Depends on:** STAT-UI-008

**Exact scope:**

Move parent-needs, parent-cycle, and child-needs groups into the Needs subtab panel and apply the reference parent/child list grouping.

**Files:**

- Modify `apps/web/src/routes/telegram/dashboard/+page.svelte` (parent and child portions of `panel-activity`).

**Goal:**

Needs provides a focused reference-like Parent section followed by a Child section.

### Outcome

Parent and child sources continue to degrade independently and keep current tooltips.

### Architectural decision

Reuse `parentBehavior`, `childBehavior`, their `sectionUnavailable(...)` checks, `formatValue`, tooltip keys, and retry. Do not combine their availability.

### Required changes

1. Guard existing parent groups and child group with Needs.
2. Form reference-like labelled lists under localized Parent and Child headings without dropping a metric or help button.
3. Keep parent and child unavailable branches local to their groups.

### Out of scope

- Parent/child analytics, tooltip wording, subtab mechanics, or backend contracts.

### Acceptance criteria

- Needs alone displays distinct localized Parent and Child list sections.
- Parent failure does not hide child values and child failure does not hide parent values; both retain retry states.
- Existing help controls are at least 44px, keyboard operable, and restore focus after dialog close.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/adminStatisticsLocalization.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/telegram/dashboard/+page.svelte
git commit -m "feat(web): compose statistics needs view"
```

## STAT-UI-012: Cover reference-critical Statistics interactions

**Status:** TODO  
**Priority:** P2  
**Depends on:** STAT-UI-004, STAT-UI-005, STAT-UI-006, STAT-UI-007, STAT-UI-009, STAT-UI-010, STAT-UI-011

**Exact scope:**

Extend the existing Playwright spec with deterministic fixture data and assertions for primary tabs, Activity subtabs, period navigation, dialog focus, mobile geometry, and desktop localization.

**Files:**

- Modify `apps/web/tests/e2e/admin-dashboard.spec.ts`.
- Modify `apps/web/tests/e2e/e2eBackend.mjs` only if the existing fixture cannot render a tested non-empty state.

**Goal:**

Browser tests prove reference alignment remains interactive and reachable, not merely compilable.

### Outcome

The existing admin-dashboard spec is the single behavioural Statistics UI contract.

### Architectural decision

Extend current fixture/spec rather than adding a route or live analytics dependency. This is local preview evidence, not deployed Telegram proof.

### Required changes

1. At 320px assert five primary tabs, three Activity subtabs, selected panels, 44px controls, fixed nav, and `scrollWidth <= clientWidth`.
2. Verify mouse and Arrow/Home/End keyboard selection at both tab levels; an Activity selection does not navigate.
3. Retain four-period URL/`aria-pressed` checks and exercise tooltip Enter, close, Escape, and focus restoration.
4. At desktop width assert localized Russian content and no i18n key fragments.

### Out of scope

- Screenshot baselines, remote deployment, Telegram cache proof, or unrelated Mini App E2E.

### Acceptance criteria

- Preview E2E proves all primary and Activity interactions at 320px.
- All tested controls meet 44px, selected panels are visible, and no horizontal overflow occurs.
- Desktop proves Russian localization and existing tooltip-focus behaviour.
- Fixture data is deterministic and never calls a live analytics service.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/admin-dashboard.spec.ts
```

### Commit

```bash
git add apps/web/tests/e2e/admin-dashboard.spec.ts apps/web/tests/e2e/e2eBackend.mjs
git commit -m "test(web): cover statistics reference interactions"
```

## STAT-UI-013: Add reviewed Statistics visual baselines

**Status:** TODO  
**Priority:** P2  
**Depends on:** STAT-UI-012

**Exact scope:**

Add focused deterministic Playwright screenshot assertions for Overview, Coins, Rewards, Tasks, Activity/Activation, Activity/Retention, and Activity/Needs at the reference mobile viewport.

**Files:**

- Modify `apps/web/tests/e2e/admin-dashboard.spec.ts`.
- Create `apps/web/tests/e2e/admin-dashboard.spec.ts-snapshots/` for approved Playwright image baselines.

**Goal:**

Catch visual drift in reference hierarchy, surfaces, segmented controls, and fixed graphical navigation.

### Outcome

Approved images provide reviewable local visual evidence alongside interaction tests.

### Architectural decision

Use focused locator screenshots or clipped regions that exclude browser chrome and mask/make deterministic update time. Baselines are reviewed assets, never blindly accepted output.

### Required changes

1. Capture seven stable states at fixed 320px after fixture data and fonts settle.
2. Mask volatile time or make fixture time deterministic; avoid broad full-page images with unrelated differences.
3. Name snapshots after primary tab and Activity subtab, and review image diffs before baseline approval.

### Out of scope

- Relaxed screenshot thresholds, production snapshots, or desktop visual variants.

### Acceptance criteria

- Assertions pass for the seven mobile states and visibly include their reference hierarchy.
- Baselines are deterministic in preview E2E and exclude volatile time/browser chrome.
- STAT-UI-012 interaction coverage stays green; images are reported as local browser evidence, not Telegram deployment proof.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/admin-dashboard.spec.ts
```

### Commit

```bash
git add apps/web/tests/e2e/admin-dashboard.spec.ts apps/web/tests/e2e/admin-dashboard.spec.ts-snapshots
git commit -m "test(web): add statistics visual baselines"
```

## Final quality gate

After every task is `DONE` and committed, run the complete web gate. This is local source/build/browser evidence, not remote CI, deployment, or Telegram WebView proof.

```bash
cd apps/web && npm run test && npm run lint && npm run build && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/admin-dashboard.spec.ts
```
