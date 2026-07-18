<a id="top"></a>
# Tasks and Shop UI Gamification Backlog

## Table of contents

- [Goal and success measures](#goal)
- [Current-state findings](#current-state)
- [Scope and non-goals](#scope)
- [Research-backed motivation principles](#research)
- [Architecture decisions](#architecture)
- [Priority summary](#priorities)
- [Execution backlog](#backlog)
- [File impact map](#files)
- [Execution order](#order)
- [Verification matrix](#verification)
- [Risks, rollout, and rollback](#risks)

## Implementation status (2026-07-18)

- TSU-001: completed; geometry and chip-readability assertions are reused by the role-based Playwright scenario.
- TSU-002–005: completed in the visual-structure pass with shared catalog header, group navigation, card, progress, URL-state, responsive action layout, and semantic tokens.
- TSU-006: completed (server-owned period progress and timezone-safe windows).
- TSU-007: completed for both roles; the parent now sees the same Today and selected-child progress surface as the child while retaining parent actions.
- TSU-008: completed (pending guard, live feedback, retry, reduced-motion-safe acknowledgment).
- TSU-009: completed for both roles (server-backed goal, Shop/Today progress, missing/ready/empty/stale states); selection and clearing remain child-only actions.
- TSU-010: completed (optional if–then cue, migration, API compatibility, modal/card display).
- TSU-011: implementation complete; moderated sessions remain a manual release gate. See [`tasks-shop-ui-usability-protocol.md`](tasks-shop-ui-usability-protocol.md).
- TSU-012: web code gates and the focused parent/child desktop/mobile Playwright scenario pass; the full Playwright suite and moderated usability session remain release gates.

<a id="goal"></a>
## 🎯 Goal and success measures

Create one coherent, modern, mobile-first experience across the Tasks and Shop screens, while making the child flow more motivating for today's habits without adding coercive or shame-based mechanics.

The target loop is:

`See today's achievable tasks → choose and act → receive immediate informative feedback → see progress → understand which chosen reward is closer`

Success is reached when:

- Tasks and Shop use the same layout grid, header rhythm, filter treatment, card anatomy, control heights, icon language, and responsive breakpoints.
- The primary action aligns to the same card column in both grid and row views; secondary/admin actions remain visually subordinate.
- Card regions never overlap at supported viewports or text scales, and every visible chip remains legible without clipped or colliding text.
- All interactive targets are at least `44 × 44 px` on touch devices, with at least `8 px` between adjacent targets.
- The child sees a truthful, server-derived compact "Today" summary; cards stay focused on task content and actions without embedded progress bars.
- Completion feedback is visible within `100 ms`, the request state remains explicit until the server responds, and no layout shift is introduced.
- The interface works at `320`, `375`, `390`, `768`, `1024`, and `1440 px`, in portrait and mobile landscape, with no horizontal page scroll.
- Keyboard navigation, screen-reader labels/live regions, `200%` text zoom, and `prefers-reduced-motion` are covered by acceptance tests.
- Child-facing copy celebrates action and recovery. It never uses lost-streak warnings, countdown pressure, randomized rewards, leaderboards, or comparisons between children.

[↑ Back to top](#top)

<a id="current-state"></a>
## 🔎 Current-state findings

- `TasksSection.svelte` and `ShopSection.svelte` already share `SectionHeaderControls`, `CardHeader`, and `BulkActionToolbar`, but duplicate their card/list layout, selection, skeleton, action-column, and mobile CSS.
- Both sections have grid and row modes, group filters, admin bulk mode, loading skeletons, and role-specific actions. The redesign must preserve all of these behaviors.
- Mobile row actions currently use very small typography and can split a `3.15rem` action column into two controls; individual controls are not guaranteed to meet the `44 px` target.
- `SectionHeaderControls.svelte` reduces icon controls to `2.22rem` on mobile, below the repository's existing `--touch-target-height` token.
- Global design tokens already exist in `static/css/partials/tokens.css`, including spacing, safe-area, transition, age-theme, and touch-target values. New component-local raw color and spacing systems are unnecessary.
- Shop group selection is URL-backed; Tasks group selection is component-local. Cross-screen behavior is therefore inconsistent.
- `TaskDto` exposes `lastCompletedAt`, but not the current frequency-window count, remaining completions, availability, or reset time. A truthful "Today" progress UI requires a backend contract extension.
- Frequency-window boundaries currently use `ZoneId.systemDefault()`. There is no family/user timezone contract, so calendar-day behavior can vary with deployment location.
- Analytics already contains daily-quest view-model logic. It may inform naming and presentation, but it must not become a second source of truth for task availability.

[↑ Back to top](#top)

<a id="scope"></a>
## 📦 Scope and non-goals

### In scope

- Child and parent/admin variants of `/app/tasks` and `/app/shop`.
- Shared catalog controls, group navigation, cards, empty/loading/error states, and feedback states.
- Mobile-first layout, safe areas, touch behavior, keyboard behavior, accessibility, and reduced motion.
- Server-owned current-period progress for task frequency limits.
- A child-selected reward goal that connects today's effort with a meaningful Shop outcome.
- English and Russian UI strings.
- Unit, backend, and Playwright coverage plus a small set of stable visual/layout assertions.

### Non-goals

- Rebuilding the app shell or changing primary navigation.
- Replacing the existing coin economy, approval flow, permissions, group ordering, or bulk actions.
- Adding public leaderboards, child-to-child comparisons, loot boxes, variable-ratio rewards, streak freezes sold for coins, or punitive streak resets.
- Giving children direct task/shop mutation permissions.
- Inferring timezone from the browser on every request. The server contract must own period boundaries.
- A new UI framework or component dependency. Use the existing Svelte and CSS token stack.
- Redesigning Analytics, History, Requests, print catalog, or public feature pages in this delivery; shared primitives must not regress them.

[↑ Back to top](#top)

<a id="research"></a>
## 🧠 Research-backed motivation principles

These findings guide product hypotheses; they do not guarantee a psychological outcome for every child. Validate comprehension and emotional response with children and parents before treating engagement metrics as success.

| Research handle | Product implication | Guardrail |
| --- | --- | --- |
| Progress monitoring improves goal attainment, especially when progress is recorded and physically visible ([Harkin et al., 2016](https://pubmed.ncbi.nlm.nih.gov/26479070/)). | Show one small, concrete Today summary after an approved completion. | Progress must come from server truth and must not inflate individual cards. |
| Immediate rewards are more strongly associated with persistence than delayed rewards ([Woolley & Fishbach, 2017](https://journals.sagepub.com/doi/abs/10.1177/0146167216676480)). | Give immediate acknowledgment, state change, and visible movement toward a chosen reward after the action/request. | Do not fabricate coin credit before approval; distinguish "request sent" from "coins earned". |
| If-then implementation intentions have a medium-to-large positive effect on goal attainment across 94 tests ([Gollwitzer & Sheeran, 2006](https://www.socmot.uni-konstanz.de/publications/implementation-intentions-and-goal-achievement-meta-analysis-effects-and-processes)). | Let the child/parent phrase an optional cue as "After/when …, I will …" and surface it on the task card. | Optional, editable, private to the family, and never required to complete a task. |
| Expected tangible rewards can undermine intrinsic motivation, with stronger concerns for children, while positive feedback can help ([Deci, Koestner, & Ryan, 1999](https://pubmed.ncbi.nlm.nih.gov/10589297/)). | Emphasize competence, choice, and concrete positive feedback; treat coins as one feedback channel, not the identity of the habit. | No controlling copy, shame, competition, reward inflation, or celebration proportional only to coin value. |

Derived design rules:

1. Prefer "You completed 1 of 3 today" over "Do not lose your streak."
2. Praise the action specifically (for example, "Backpack packed — request sent") rather than the child globally.
3. Show one achievable next action, while preserving access to the full task list and group filters.
4. Make recovery normal: a new day starts neutral, with no red failure state for yesterday.
5. Keep celebration brief (`150–300 ms`), interruptible, non-blocking, and absent under reduced motion.
6. Let the child choose a reward goal; do not automatically steer them toward the cheapest item.

[↑ Back to top](#top)

<a id="architecture"></a>
## 🏗️ Architecture decisions

### AD-01 — Shared presentation contract, separate domain actions

Create shared catalog presentation primitives for header controls, group tabs, card shell, progress, and feedback. Keep task completion/request logic in `TasksSection.svelte` and purchase/request logic in `ShopSection.svelte`.

- Do not build a single component with task/shop/admin/child boolean branches.
- Use typed view models to normalize presentation fields: title, description, amount, status, metadata, progress, primary action, and secondary actions.
- Preserve domain-specific API calls and permission checks at section level.

### AD-02 — Tokens are the only visual source of truth

Extend semantic tokens in `static/css/partials/tokens.css` and reusable component styles in `components.css`. Component styles may define layout, but must not introduce raw product colors, random radii, or independent breakpoint systems.

Required semantic token groups:

- control heights and icon sizes;
- task, reward, available, pending, completed, and locked states;
- catalog grid/row gaps and action-column widths;
- progress track/fill/text pairs;
- motion duration/easing;
- surface/elevation levels for normal, selected, and celebratory states.

### AD-03 — Server owns calendar windows and progress

Extend the dashboard task contract with a nested, additive period-progress object rather than making the web client scan `history`:

```json
{
  "periodProgress": {
    "period": "day",
    "completed": 1,
    "limit": 2,
    "remaining": 1,
    "available": true,
    "windowStart": "2026-07-18T00:00:00Z",
    "resetAt": "2026-07-19T00:00:00Z"
  }
}
```

- `available` must use the same rule as completion/request validation.
- The server calculates counts with one grouped query for all visible task IDs; no N+1 query per card.
- Existing clients remain compatible because the field is additive and nullable.
- Introduce an explicit family timezone before labeling a server window as "today". Default existing families through one documented migration/backfill policy; never silently switch based on container timezone.

### AD-04 — One child loop, distinct parent workspace

- Child mode leads with Today progress, a recommended next task, and the chosen reward goal.
- Parent/admin mode leads with management controls and compact operational status; motivational content is a preview, not the dominant workspace.
- Both roles use the same visual primitives and responsive grid, but information priority differs by role.

### AD-05 — Explicit async state machine

Each primary action uses `idle → submitting → submitted/approved → error` states.

- Disable repeated submission while pending and preserve button width.
- A child task request says "Request sent" rather than showing earned coins before approval.
- Announce success/error through the existing toast path plus an `aria-live="polite"` local status where card context matters.
- Reconcile all counters from `applyDataSnapshot`; do not mutate optimistic balances.

### AD-06 — URL-backed view state, ephemeral feedback

- Align group/view filters through URL query parameters so reload/back navigation preserves context on both screens.
- Do not put transient animation or toast state in the URL or persistent store.
- Existing local view-mode preference may remain as fallback, but an explicit URL value wins.

### AD-07 — Reward goal persistence is server-owned

Store one optional selected reward goal per child, scoped by family and validated against an active Shop item owned by that child.

- Persist the item ID, not duplicated price/title.
- Clear the goal automatically when the item is deleted; retain it but show its state when blocked.
- Calculate `current balance / price` from server-returned values; never store the percentage.
- Use a new Flyway migration; never rewrite migration history.

### AD-08 — Ethical measurement

Do not optimize solely for taps, time-in-app, coin issuance, or streak length. If product analytics is added, aggregate these outcome-oriented measures without child-entered notes or task text:

- share of days with at least one voluntarily initiated task request;
- task-request completion/approval funnel;
- time from screen load to first meaningful action;
- reward-goal selection and attainment rate;
- error/retry rate and rage-click proxy;
- parent/child qualitative feedback and opt-out rate.

[↑ Back to top](#top)

<a id="priorities"></a>
## 🚦 Priority summary

| ID | Priority | Deliverable | Depends on |
| --- | --- | --- | --- |
| TSU-001 | P0 | Baseline behavior and visual contract | — |
| TSU-002 | P0 | Semantic catalog tokens and shared primitives | TSU-001 |
| TSU-003 | P0 | Unified header, filters, controls, and URL state | TSU-002 |
| TSU-004 | P0 | Unified Tasks and Shop card anatomy | TSU-002 |
| TSU-005 | P0 | Mobile, accessibility, loading, and error hardening | TSU-003, TSU-004 |
| TSU-006 | P1 | Timezone and period-progress backend contract | TSU-001 |
| TSU-007 | P1 | Child-facing Today summary and next-action hierarchy | TSU-004, TSU-006 |
| TSU-008 | P1 | Honest immediate feedback and micro-celebration | TSU-005, TSU-006 |
| TSU-009 | P1 | Child-selected Shop reward goal | TSU-004, TSU-006 |
| TSU-010 | P2 | Optional if-then habit cue | TSU-007 |
| TSU-011 | P2 | Ethical analytics and usability validation | TSU-007, TSU-008, TSU-009 |
| TSU-012 | P0 release gate | Cross-role regression and rollout | All selected release tasks |

[↑ Back to top](#top)

<a id="backlog"></a>
## 🧩 Execution backlog

### TSU-001 — Capture the current behavior and visual contract

**Priority:** P0
**Outcome:** implementation starts from protected behavior, not screenshots alone.

**Change:**

- Add Playwright coverage for Tasks group filtering, URL/back behavior, grid/row mode, admin bulk selection, child request, disabled task, and loading/empty states.
- Extend Shop coverage for the same shared behaviors.
- Add stable layout assertions for header/control alignment, action-column alignment, touch-target size, and horizontal overflow at `375 × 812` and `768 × 1024`.
- Add a reusable DOM-geometry assertion that compares the bounding boxes of card title, amount, chips, description, progress/status, and action regions; fail when unrelated regions intersect or when a child escapes the card bounds.
- Add chip readability assertions for intrinsic text width, wrapping/truncation policy, line height, contrast, and spacing between adjacent chips. When truncation is intentional, require the full value through an accessible name or disclosure.
- Record reference screenshots for child/admin Tasks and Shop in both view modes only if the repository chooses to version Playwright snapshots consistently.

**Files:**

- `apps/web/tests/e2e/app-sections.spec.ts`
- `apps/web/tests/e2e/shop-filters.spec.ts`
- `apps/web/tests/e2e/child-shop.spec.ts`
- `apps/web/tests/e2e/tasks-shop-ui.spec.ts` (new)
- `apps/web/tests/e2e/cardLayoutAssertions.ts` (new shared geometry/readability helper)
- `apps/web/playwright.config.ts` (only if projects/viewports need central configuration)

**Acceptance criteria:**

- Tests prove all existing role permissions and request/approval flows still work.
- Layout assertions use bounding boxes/semantic roles, not brittle full DOM snapshots.
- Geometry checks cover grid and row cards on both screens, parent and child roles, loading/final states, long Russian content, `200%` zoom, and `320`, `375`, `390`, `768`, and `1024 px` widths.
- No title, amount, chip, description, progress/status, checkbox, or action bounding box intersects an unrelated card region; all regions remain inside the card padding box.
- Every chip has readable text, visible boundaries, at least `4 px` internal vertical padding, at least `8 px` separation from another interactive target, and no mid-glyph clipping.
- The baseline fails if a mobile control is below `44 × 44 px` or the document scroll width exceeds viewport width.

### TSU-002 — Establish catalog tokens and shared primitives

**Priority:** P0
**Outcome:** both screens compose the same visual language without a task/shop mega-component.

**Change:**

- Add semantic catalog tokens described in AD-02.
- Create shared `CatalogSectionHeader`, `CatalogGroupNav`, `CatalogCard`, and `CatalogActionFeedback` primitives.
- Convert `CardHeader` into a slot/prop-driven part of the catalog card or narrow it to title/amount only; remove cross-feature `:global(.task-card--list)` selector coupling.
- Keep SVG icons decorative with `aria-hidden`; interactive icon buttons retain text labels or accessible names.

**Files:**

- `apps/web/static/css/partials/tokens.css`
- `apps/web/static/css/partials/design-system.css`
- `apps/web/static/css/partials/components.css`
- `apps/web/src/lib/components/app/CardHeader.svelte`
- `apps/web/src/lib/components/app/catalog/CatalogSectionHeader.svelte` (new)
- `apps/web/src/lib/components/app/catalog/CatalogGroupNav.svelte` (new)
- `apps/web/src/lib/components/app/catalog/CatalogCard.svelte` (new)
- `apps/web/src/lib/components/app/catalog/CatalogActionFeedback.svelte` (new)

**Acceptance criteria:**

- Shared primitives have no API calls and no direct `appStore` dependency.
- Task and Shop status colors use semantic tokens and include text/icon meaning.
- No new structural emoji icons are introduced.
- Component focus, hover, pressed, disabled, selected, and pending states are visually distinct in both age themes.

### TSU-003 — Unify section header, controls, filters, and state preservation

**Priority:** P0
**Outcome:** Tasks and Shop controls line up and behave identically.

**Change:**

- Use the shared header primitive with a fixed title/subtitle zone and one control row.
- Make Add the only primary admin CTA. View, order, and bulk controls are secondary/segmented actions.
- Raise all compact controls and group tabs to the shared touch-target token.
- Give Tasks the same URL-backed `group` behavior as Shop and add a typed `view=grid|list` query contract to both.
- Preserve filters and scroll position on back navigation; clear bulk selection when role, child, group, or view scope changes.
- Keep group chips horizontally scrollable on small screens, with visible affordance and keyboard scrolling; do not create page-level horizontal overflow.

**Files:**

- `apps/web/src/lib/components/app/SectionHeaderControls.svelte`
- `apps/web/src/lib/components/app/BulkActionToolbar.svelte`
- `apps/web/src/lib/components/app/catalog/CatalogSectionHeader.svelte`
- `apps/web/src/lib/components/app/catalog/CatalogGroupNav.svelte`
- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/sections/ShopSection.svelte`
- `apps/web/src/lib/services/catalogViewState.ts` (new)
- `apps/web/tests/unit/catalogViewState.test.ts` (new)
- `apps/web/tests/e2e/tasks-shop-ui.spec.ts`

**Acceptance criteria:**

- Headers and primary actions share the same left/right alignment at every target viewport.
- Reload, browser Back, and child switching preserve or reset state according to AD-06 without stale selections.
- Controls remain labeled at `200%` zoom and do not overlap.

### TSU-004 — Align Tasks and Shop card anatomy

**Priority:** P0
**Outcome:** cards feel like two states of one product loop.

**Change:**

- Normalize card regions: status/group → title/description → progress/context → amount → actions.
- Use the same grid min width, padding, vertical rhythm, title wrapping policy, badge height, and action-column width.
- Keep one primary action per child card. Put admin Edit after the primary action as a secondary control; bulk selection remains outside the action hierarchy.
- In row mode, allow two title lines before truncation and keep the full label accessible; do not reduce body/action text below `12 px`.
- Add typed `TaskCatalogItemViewModel` and `ShopCatalogItemViewModel` builders so markup does not repeat formatting and status decisions.
- Keep affordability visually meaningful in Shop without dimming text below contrast requirements.

**Files:**

- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/sections/ShopSection.svelte`
- `apps/web/src/lib/components/app/CardHeader.svelte`
- `apps/web/src/lib/components/app/catalog/CatalogCard.svelte`
- `apps/web/src/lib/services/catalogItemViewModel.ts` (new)
- `apps/web/static/css/partials/components.css`
- `apps/web/static/css/partials/responsive.css`
- `apps/web/tests/unit/catalogItemViewModel.test.ts` (new)
- `apps/web/tests/e2e/tasks-shop-ui.spec.ts`

**Acceptance criteria:**

- Matching cards have equal header, content, and action baselines when displayed side by side.
- Long Russian titles, long group names, `99999` coin values, and two admin actions do not overlap at `320 px`.
- Long group/frequency/history/status chips wrap as whole chips or move to the next row; chip text never overlaps an adjacent chip, amount, title, or action and remains readable at `200%` zoom.
- Grid and row modes contain the same essential facts and actions; density changes, meaning does not.

### TSU-005 — Harden mobile, accessibility, and system states

**Priority:** P0
**Outcome:** the modernized layout is usable beyond the happy path.

**Change:**

- Ensure all controls use semantic buttons/links and meet `44 × 44 px`; checkboxes receive an expanded label hit area.
- Add visible focus styles, logical tab order, `aria-pressed`/`aria-current`, and local live announcements.
- Give skeletons fixed geometry matching final cards and mark them non-interactive/appropriately hidden from assistive tech.
- Add recoverable inline error states with Retry; distinguish empty collection from filtered-empty state.
- Reserve bottom padding for the app navigation and Capacitor safe area.
- Ensure animations use transform/opacity only and are disabled/reduced under `prefers-reduced-motion`.

**Files:**

- `apps/web/src/lib/components/app/SectionHeaderControls.svelte`
- `apps/web/src/lib/components/app/BulkActionToolbar.svelte`
- `apps/web/src/lib/components/app/catalog/*.svelte`
- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/sections/ShopSection.svelte`
- `apps/web/static/css/partials/animations.css`
- `apps/web/static/css/partials/components.css`
- `apps/web/static/css/partials/responsive.css`
- `apps/web/tests/e2e/tasks-shop-ui.spec.ts`

**Acceptance criteria:**

- Axe-equivalent manual/automated checks find no critical violations in both roles.
- Keyboard-only users can filter, switch view, select, submit, retry, and return focus to the triggering control after modal close.
- Reduced-motion mode has no celebratory scale/bounce and no content loss.
- Page and sticky group navigation respect safe areas in portrait and landscape.

### TSU-006 — Add timezone-safe period progress to the backend contract

**Priority:** P1
**Outcome:** "Today" and frequency progress are truthful and consistent with server validation.

**Change:**

- Introduce an explicit family timezone setting with a safe default/backfill migration and validation against IANA zone IDs.
- Refactor `FrequencyWindowService` so validation and dashboard projection share the same public period-window value object.
- Add one grouped history query returning current-window completion counts for all visible task IDs.
- Extend `TaskDto`, MapStruct mapping, web `Task` type, and `serverContract` normalization with `periodProgress`.
- Return null progress for unlimited tasks; do not translate null into `0 of 0`.

**Files:**

- `apps/backend/src/main/resources/db/migration/NNN_add_family_timezone.sql` (new; choose the next live sequence number)
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/FamilyEntity.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/TaskDto.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/TaskPeriodProgressDto.java` (new)
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FrequencyWindowService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FrequencyWindow.java` (new)
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardCatalogLoader.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardMapper.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/action/FrequencyWindowServiceTest.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardQueryServiceImplTest.java`
- `apps/web/src/lib/stores/app.ts`
- `apps/web/src/lib/services/serverContract.ts`
- `apps/web/tests/unit/serverContract.test.ts`

**Acceptance criteria:**

- Dashboard progress and mutation validation agree at period boundaries for day/week/month/year/season.
- Tests cover DST forward/back transitions and two families in different timezones.
- Query count remains constant as task count grows; no per-task query loop.
- PostgreSQL and H2 test baselines both apply the new migration.
- Older payloads without `periodProgress` still render.

### TSU-007 — Build the child-facing Today hierarchy

**Priority:** P1
**Outcome:** the child immediately knows what is achievable now and what to do next.

**Change:**

- Add a compact Today summary above Tasks for child mode: completed count, achievable count, earned coins after approval, and one suggested next task.
- Default child sorting to: available daily tasks → other available tasks → requested/pending → completed for current window → blocked.
- Keep the full list and existing group filters visible; the recommendation is a shortcut, not a lock-in.
- Keep period progress in the compact Today aggregate; do not place progress bars inside task cards.
- Use neutral copy when no task is completed and recovery copy when all current tasks are unavailable.
- Parent mode receives a compact preview/status row without displacing management controls.

**Files:**

- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/catalog/TodaySummary.svelte` (new)
- `apps/web/src/lib/services/todayTaskViewModel.ts` (new)
- `apps/web/src/lib/i18n/messages/en/tasks.ts`
- `apps/web/src/lib/i18n/messages/ru/tasks.ts`
- `apps/web/tests/unit/todayTaskViewModel.test.ts` (new)
- `apps/web/tests/e2e/tasks-shop-ui.spec.ts`

**Acceptance criteria:**

- Summary values reconcile with visible cards and server payload after child switch and approval.
- Unlimited tasks are available but excluded from misleading denominator math.
- No red failed-day state, lost-streak copy, or countdown urgency appears.
- The next action is keyboard reachable and does not remove access to other tasks.

### TSU-008 — Add honest immediate feedback and restrained celebration

**Priority:** P1
**Outcome:** every action has immediate, comprehensible cause-and-effect feedback.

**Change:**

- Add a pending state within `100 ms` while requests are submitted.
- On child request: show submitted state and preserve current balance/progress until approval.
- On parent direct award or approved refresh: animate only the changed progress/coin region and announce the result.
- Use one short check/shine transition; no confetti storm, screen takeover, sound, or haptic dependency.
- Restore the actionable state with an inline Retry when the network fails.

**Files:**

- `apps/web/src/lib/components/app/catalog/CatalogActionFeedback.svelte`
- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/sections/ShopSection.svelte`
- `apps/web/static/css/partials/animations.css`
- `apps/web/src/lib/i18n/messages/en/tasks.ts`
- `apps/web/src/lib/i18n/messages/ru/tasks.ts`
- `apps/web/src/lib/i18n/messages/en/shop.ts`
- `apps/web/src/lib/i18n/messages/ru/shop.ts`
- `apps/web/tests/e2e/tasks-shop-ui.spec.ts`

**Acceptance criteria:**

- Double-tap does not create duplicate requests or purchases.
- Pending, approved, and failed states are distinguishable without relying on color.
- No optimistic coin/progress value survives a rejected request.
- Reduced-motion produces an instant state change with the same live announcement.

### TSU-009 — Connect Tasks to a child-selected reward goal

**Priority:** P1
**Outcome:** the Shop supports autonomy and makes today's effort meaningful without pushing a random reward.

**Change:**

- Add a child action to select/clear one reward goal in Shop.
- Surface goal progress in the Shop header and as a compact secondary panel in the Tasks Today summary.
- Show current balance, target price, missing coins, and a truthful "ready" state.
- Keep purchase/request as the only primary Shop action; goal selection is secondary.
- Validate goal ownership and active/deleted state server-side.

**Files:**

- `apps/backend/src/main/resources/db/migration/NNN_add_child_reward_goal.sql` (new; choose after TSU-006 migration)
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ChildEntity.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/FamilyDashboardDetailResponse.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResource.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/*` (narrow goal command/service types)
- `apps/web/src/lib/services/api.ts`
- `apps/web/src/lib/services/serverContract.ts`
- `apps/web/src/lib/stores/app.ts`
- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/sections/ShopSection.svelte`
- `apps/web/src/lib/components/app/catalog/RewardGoalProgress.svelte` (new)
- `apps/web/src/lib/i18n/messages/en/shop.ts`
- `apps/web/src/lib/i18n/messages/ru/shop.ts`
- backend service/resource tests and `apps/web/tests/e2e/tasks-shop-ui.spec.ts`

**Acceptance criteria:**

- Goal persists across refresh, devices, and child switching without leaking between family scopes.
- Selecting a goal never spends coins or submits a purchase.
- Deleted, blocked, unaffordable, and newly affordable goal states have explicit recovery paths.
- The UI does not claim a specific task will guarantee the reward when approval is still required.

### TSU-010 — Add an optional if-then habit cue

**Priority:** P2
**Outcome:** recurring tasks can include a concrete situational cue without turning task setup into a large form.

**Change:**

- Add optional cue and action fields to task create/edit under progressive disclosure.
- Render the composed sentence on the child task card only when configured.
- Keep the existing comment separate; do not parse free text into structured fields.
- Include values in CSV import/export only after the API and UI contract is stable.

**Files:**

- next sequential backend migration for task cue fields
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TaskEntity.java`
- task request/command/DTO/mapper files under `apps/backend/src/main/java/com/sashplatonov/earnit/kids/`
- `apps/web/src/lib/components/app/modals/TaskModal.svelte`
- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/services/taskPayload.ts`
- `apps/web/src/lib/services/serverContract.ts`
- `apps/web/src/lib/i18n/messages/en/tasks.ts`
- `apps/web/src/lib/i18n/messages/ru/tasks.ts`
- relevant backend tests, `apps/web/tests/unit/taskPayload.test.ts`, and E2E coverage

**Acceptance criteria:**

- Both fields are optional and can be cleared.
- Labels and helper text remain visible; placeholders are not the only instructions.
- Long Russian cues wrap without pushing the primary action off-screen.
- Existing task payloads and CSV files remain backward compatible until an explicit import version is introduced.

### TSU-011 — Validate motivation quality and add ethical analytics

**Priority:** P2
**Outcome:** release decisions use usefulness and wellbeing signals, not engagement alone.

**Change:**

- Run five moderated child/parent usability sessions across younger and older age themes.
- Test comprehension of request vs approval, Today progress, reset behavior, and reward-goal meaning.
- Add only aggregate event names/IDs after privacy review; exclude task title, comment, cue, child nickname, and request note.
- Define a two-week before/after evaluation with guardrail metrics from AD-08.

**Files:**

- `docs/tasks-shop-ui-usability-protocol.md` (new, only when this task starts)
- existing web/backend observability modules selected during implementation
- `docs/monitoring/newrelic-dashboard.md` (only if dashboards are added)
- relevant metrics tests

**Acceptance criteria:**

- At least `4/5` child participants can explain what happens after pressing Complete/Request without help.
- No participant interprets a neutral new day as punishment or lost progress.
- Telemetry payload tests reject child-authored text and direct identifiers.
- A measurable increase in voluntary task initiation must not coincide with higher retry/error rates or negative qualitative feedback.

### TSU-012 — Run the release gate and staged rollout

**Priority:** P0 release gate
**Outcome:** selected scope ships without breaking roles, contracts, or mobile layout.

**Change:**

- Run the full backend/web gates and targeted Playwright projects.
- Verify parent owner/member permissions, child magic-link flow, child switching, group ordering, CSV, task request/approval, direct award, purchase request/approval, and insufficient-balance behavior.
- Test real Chromium mobile emulation plus at least one Capacitor iOS/Android device or simulator.
- Release P0 visual consistency independently from P1 backend gamification if P1 is not ready.

**Files:** no production file requirement; only fix failures in the owning task's file set.

**Acceptance criteria:** see [Verification matrix](#verification). All selected tasks are complete, no `.bak`/debug files remain, and migrations are sequential.

[↑ Back to top](#top)

<a id="files"></a>
## 🗂️ File impact map

| Area | Primary files | Responsibility |
| --- | --- | --- |
| Shared visual system | `apps/web/static/css/partials/tokens.css`, `design-system.css`, `components.css`, `responsive.css`, `animations.css` | Semantic tokens, shared states, layout, breakpoints, motion |
| Shared catalog UI | `apps/web/src/lib/components/app/catalog/*` | Pure presentation primitives |
| Existing shared controls | `SectionHeaderControls.svelte`, `CardHeader.svelte`, `BulkActionToolbar.svelte` | Migration bridge; shrink or delegate responsibilities |
| Task orchestration | `apps/web/src/lib/components/app/sections/TasksSection.svelte` | Task domain actions, role rules, task view model composition |
| Shop orchestration | `apps/web/src/lib/components/app/sections/ShopSection.svelte` | Shop domain actions, affordability, reward goal composition |
| Web contracts | `app.ts`, `serverContract.ts`, `api.ts`, `catalogViewState.ts`, `catalogItemViewModel.ts`, `todayTaskViewModel.ts` | Typed state, normalization, API calls, derived presentation data |
| i18n | `messages/en/tasks.ts`, `messages/ru/tasks.ts`, `messages/en/shop.ts`, `messages/ru/shop.ts` | Role-aware, non-coercive copy |
| Period progress | `TaskDto.java`, `FrequencyWindowService.java`, `FamilyDashboardCatalogLoader.java`, `FamilyDashboardMapper.java`, `HistoryRepository.java` | Server-owned window and grouped progress projection |
| Reward goal | Child entity/migration, family action/resource/detail response | Family-scoped persistence and validation |
| Verification | backend family/dashboard/action tests, web unit tests, `tests/e2e/tasks-shop-ui.spec.ts` | Contract, state, layout, accessibility, role regression |

[↑ Back to top](#top)

<a id="order"></a>
## 🧭 Execution order

### Release 1 — Coherent and accessible UI

1. `TSU-001` baseline contract.
2. `TSU-002` tokens and primitives.
3. `TSU-003` headers/controls/state and `TSU-004` card anatomy; implement sequentially against the shared primitives.
4. `TSU-005` mobile/accessibility/error hardening.
5. `TSU-012` release gate for P0 scope.

This release has no schema/API dependency and should ship first.

### Release 2 — Truthful Today motivation loop

1. `TSU-006` timezone and period-progress backend contract.
2. `TSU-007` Today hierarchy.
3. `TSU-008` honest immediate feedback.
4. `TSU-009` selected reward goal.
5. `TSU-012` release gate for P1 scope.

Do not implement `TSU-007` counters from client history while `TSU-006` is incomplete.

### Release 3 — Optional planning and validation

1. `TSU-010` if-then cue.
2. `TSU-011` qualitative validation and privacy-safe analytics.
3. `TSU-012` final gate.

[↑ Back to top](#top)

<a id="verification"></a>
## 🧪 Verification matrix

### Required automated gates

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify

cd ../web
npm run lint
npm run test
npm run build
npm run test:e2e
```

### Targeted checks by change type

| Change | Required evidence |
| --- | --- |
| Shared tokens/cards/controls | Unit view-model tests; Playwright desktop/mobile bounding-box intersection checks; chip clipping/wrapping/contrast checks; focus audit |
| URL-backed filters/view | Unit parser/serializer tests; reload, Back, invalid query, and child-switch E2E |
| Period progress/timezone | Backend unit/integration tests for every frequency period, DST, cross-family timezone, N+1 query guard; web contract tests |
| Reward goal | Migration test, ownership/permission tests, deleted/blocked item tests, cross-child isolation E2E |
| Async feedback | Duplicate-submit, rejection, retry, approval refresh, live-region, reduced-motion E2E |
| i18n | `npm run lint`, i18n unit tests, long Russian copy at `320 px` and `200%` zoom |

### Manual device and accessibility checklist

- [ ] `320 × 568`, `375 × 812`, `390 × 844`, `768 × 1024`, `1024 × 768`, and `1440 × 900`.
- [ ] Portrait and landscape; iOS/Android safe areas and bottom gesture zone.
- [ ] No document-level horizontal scroll; group tab scroller remains intentional and discoverable.
- [ ] In every grid/row card, title, amount, chips, description, progress/status, selection control, and actions stay inside the card and do not overlap each other at any target viewport.
- [ ] Chips remain readable with long English/Russian labels, maximum numeric values, `200%` zoom, and increased system text size; text is not clipped mid-glyph and adjacent chips have a visible gap.
- [ ] Touch targets `≥44 × 44 px`, gaps `≥8 px`, no precision-only interactions.
- [ ] Keyboard order follows visual order; focus remains visible and returns after modal close.
- [ ] Screen reader announces section heading, active filter/view, progress value, pending, success, and error.
- [ ] Normal text contrast `≥4.5:1`; large UI graphics/boundaries `≥3:1`; status does not rely on color alone.
- [ ] `200%` browser zoom and increased system text size do not hide primary actions.
- [ ] `prefers-reduced-motion: reduce` removes nonessential movement and retains all state information.
- [ ] Slow `3G`, offline, timeout, retry, duplicate tap, and server rejection paths.
- [ ] Parent owner/member and child variants; switch children while each filter/action state is active.

[↑ Back to top](#top)

<a id="risks"></a>
## ⚠️ Risks, rollout, and rollback

| Risk | Mitigation | Rollback boundary |
| --- | --- | --- |
| Shared component extraction changes unrelated sections through global selectors. | Namespace catalog styles, remove task/shop `:global` coupling incrementally, run all app-section E2E. | Revert shared primitive adoption while retaining additive tokens/tests. |
| "Today" disagrees with completion validation around midnight/DST. | One server window service and explicit family timezone; contract tests at boundaries. | Hide Today summary behind capability detection; retain existing task list. |
| Gamification becomes controlling or reward-centric. | Child choice, informational copy, no punishment/competition/random rewards, qualitative review. | Disable Today/reward-goal surfaces independently; core actions remain available. |
| New progress aggregation slows dashboard load. | One grouped query, query-count/performance test, observe existing dashboard latency. | Omit nullable `periodProgress` projection without changing mutation rules. |
| Reward goal leaks across children/families. | Family/child scope in every query and permission test; no client-only persistence. | Disable goal endpoints/UI and keep migration data for forward recovery. |
| Long Russian copy breaks compact mobile rows. | Two-line title policy, semantic min widths, `320 px` and zoom tests. | Fall back to stacked mobile card layout, not smaller text/touch targets. |

Rollout rules:

1. Ship P0 visual consistency first; it must not depend on new backend fields.
2. Treat `periodProgress` and reward-goal fields as additive capabilities and render P1 surfaces only when present.
3. Enable P1 for internal/test families, then a small family cohort, then all families after latency/error and qualitative review.
4. Roll back presentation by capability/feature switch where available; never delete migration history to roll back schema use.

[↑ Back to top](#top)
