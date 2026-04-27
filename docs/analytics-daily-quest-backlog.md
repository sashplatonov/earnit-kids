# Analytics Daily Quest Backlog

<a id="top"></a>

Planning snapshot created on 2026-04-27.

Delivery status: ✅ Completed and verified on 2026-04-27.

This backlog covers the "Daily Quest" redesign for the authenticated Analytics
section. The current section already has summary stats, level/week/streak mini
progress, charts, and recommendation cards in
`apps/web/src/lib/components/app/sections/AnalyticsSection.svelte`. The target
experience should make the tab compact, action-oriented, and useful for both
task completion and reward purchases.

<a id="table-of-contents"></a>

## 📚 Table of Contents

- [Goal](#goal)
- [Design Target](#design-target)
- [Phases](#phases)
- [Backlog Tasks](#backlog-tasks)
- [Task Details](#task-details)
- [Definition of Done](#definition-of-done)

<a id="goal"></a>

## 🎯 Goal

Turn the Achievements tab into a short daily action list:

- Show 3-5 quest goals such as "complete 2 tasks", "earn 30 coins", and "keep the streak".
- Give every quest a progress bar, reward signal, and a direct action button.
- Push the child toward the next useful action: Tasks when earning is needed, Shop when spending is possible.
- Keep history and charts available, but move them below the primary quest area in a compact collapsible block.
- Preserve parent and child role behavior, child switching, timeframe filters, and existing analytics data loading.

[↑ Back to top](#top)

<a id="design-target"></a>

## 🧭 Design Target

- First viewport: section title, timeframe selector, compact quest list, and one small progress summary.
- Primary user action: click a quest button and continue in Tasks or Shop.
- Secondary data: charts, top tasks, top rewards, and trend are visible only after expanding "Details".
- Empty state: when there is no activity, quests still show clear first actions instead of a blank analytics dashboard.
- Mobile layout: one-column quest cards with stable progress bars and full-width buttons.
- Desktop layout: dense two-column quest area with no oversized chart cards above the fold.

[↑ Back to top](#top)

<a id="phases"></a>

## 🧩 Phases

| Phase | Priority | Purpose | Exit criteria | Status |
| --- | --- | --- | --- | --- |
| Phase 1 - Quest model | P0 | Define quest data from existing analytics, tasks, balance, and shop signals | View-model tests cover quest progress and destinations | ✅ |
| Phase 2 - Compact quest UI | P0 | Replace the top analytics layout with actionable daily quests | Parent and child see compact quests with progress and buttons | ✅ |
| Phase 3 - Collapsible details | P1 | Move charts and recommendations below the quests in a details area | Existing charts still load after expand and timeframe changes | ✅ |
| Phase 4 - Motivation tuning | P1 | Add better purchase and task nudges without backend schema changes | Empty and low-progress states show useful next actions | ✅ |
| Phase 5 - Polish and release gates | P2 | Tighten responsive UI, accessibility, i18n, and regression coverage | E2E, unit, lint, and visual checks pass | ✅ |

[↑ Back to top](#top)

<a id="backlog-tasks"></a>

## 📝 Backlog Tasks

| ID | Phase | Priority | Depends on | Main outcome | Status |
| --- | --- | --- | --- | --- | --- |
| ADQ-01 | 1 | P0 | none | Frozen quest rules and reusable quest view model | ✅ |
| ADQ-02 | 1 | P0 | ADQ-01 | Route/action target contract for Tasks and Shop navigation | ✅ |
| ADQ-03 | 1 | P0 | ADQ-01 | Unit coverage for quest progress, empty states, and role-safe output | ✅ |
| ADQ-04 | 2 | P0 | ADQ-01, ADQ-02 | Compact daily quest list replaces bulky above-the-fold analytics | ✅ |
| ADQ-05 | 2 | P0 | ADQ-04 | Quest CTA buttons navigate to the correct app sections | ✅ |
| ADQ-06 | 2 | P0 | ADQ-04 | Parent and child layouts handle no children, selected child changes, and reloads | ✅ |
| ADQ-07 | 3 | P1 | ADQ-04 | Charts, trend, and top lists move into a compact collapsible details block | ✅ |
| ADQ-08 | 3 | P1 | ADQ-07 | Chart rendering stays reliable when details are expanded after load | ✅ |
| ADQ-09 | 4 | P1 | ADQ-01, ADQ-04 | Purchase-oriented quest encourages earning toward the nearest reward | ✅ |
| ADQ-10 | 4 | P1 | ADQ-01, ADQ-04 | Task-oriented quest recommends the fastest useful task | ✅ |
| ADQ-11 | 4 | P1 | ADQ-09, ADQ-10 | Empty and low-activity states become action-first quest states | ✅ |
| ADQ-12 | 5 | P2 | ADQ-04, ADQ-07 | Responsive and accessible UI polish | ✅ |
| ADQ-13 | 5 | P2 | ADQ-12 | i18n messages for quest labels, hints, states, and CTAs | ✅ |
| ADQ-14 | 5 | P0 | ADQ-03 onward | Regression and release verification gates | ✅ |

[↑ Back to top](#top)

<a id="task-details"></a>

## 🔧 Task Details

### ADQ-01 - Define the daily quest view model

Priority: P0

Primary surfaces: `AnalyticsSection.svelte`, `analyticsViewModel.ts`, analytics
i18n message files.

Description: extend the analytics view model with a small `dailyQuests` list
derived from data that already exists in the app. Do not require a backend
migration for the first version.

Do:

- Add a typed quest shape with stable fields: id, title, description, current, target, percent, rewardLabel, actionLabel, actionTarget, and status.
- Produce 3 default quests: complete tasks, earn coins, and keep streak.
- Use existing `earned`, `weekEarned`, `streakValue`, `currentBalance`, `tasks`, and analytics recommendation data where possible.
- Clamp all progress percentages to `0..100`.
- Return actionable fallback quests when there is no trend data.

Acceptance:

- The view model can build at least 3 quests from the current analytics payload.
- Quest output is deterministic for the same payload.
- No quest can render negative progress, NaN progress, or a missing title.
- No backend endpoint or database schema change is required.

### ADQ-02 - Define action targets and section navigation

Priority: P0

Depends on: ADQ-01

Primary surfaces: authenticated app route helpers, navigation state, Tasks and
Shop section links.

Description: define how quest buttons move the user to the next section without
creating one-off navigation logic inside each card.

Do:

- Support action targets for Tasks, Shop, and Details.
- Use existing app section routes or section navigation helpers instead of hardcoded page reloads.
- Keep parent and child behavior aligned with current app permissions.
- For parent view, task actions should stay analytics-safe and not expose child-only request actions.

Acceptance:

- A task quest button opens the Tasks section.
- A purchase quest button opens the Shop section.
- A details quest button expands the analytics details block.
- Navigation works after switching the selected child.

### ADQ-03 - Add unit tests for quest generation

Priority: P0

Depends on: ADQ-01

Primary surfaces: `tests/unit/analyticsViewModel.test.ts` or the existing
equivalent unit test file for analytics.

Description: lock the quest rules before UI changes land.

Do:

- Cover normal activity with earned coins, spent coins, trend, and recommendations.
- Cover empty activity with no trend and no recommendations.
- Cover zero balance and high balance cases.
- Cover streak 0, partial streak, and completed streak goal.
- Cover missing or malformed analytics payload fields.

Acceptance:

- Unit tests prove every generated quest has a valid id, title, percent, and action target.
- Tests prove progress is clamped to `0..100`.
- Tests prove empty analytics still produce action-first quests.

### ADQ-04 - Build the compact daily quest list UI

Priority: P0

Depends on: ADQ-01, ADQ-02

Primary surface: `AnalyticsSection.svelte`.

Description: replace the top-heavy summary and mini-progress area with a compact
quest-first layout.

Do:

- Keep the section header and timeframe selector.
- Add a `daily-quest-list` area above charts.
- Render each quest as a compact row/card with icon, title, short description, progress bar, reward signal, and CTA button.
- Preserve a small balance/level/streak summary as a single dense strip rather than separate large cards.
- Keep card radius and spacing consistent with existing app components.

Acceptance:

- The first viewport shows quests before charts on desktop and mobile.
- Quest rows do not shift layout when progress or labels change.
- Existing analytics stats are still visible in compact form.
- Existing admin no-child empty state still works.

### ADQ-05 - Wire quest CTA actions

Priority: P0

Depends on: ADQ-04

Primary surfaces: `AnalyticsSection.svelte`, app navigation helpers.

Description: make every quest actionable.

Do:

- Wire task quest CTA to Tasks.
- Wire purchase quest CTA to Shop.
- Wire streak and progress CTA to the best next section based on quest action target.
- Keep buttons accessible with clear labels and `type="button"`.

Acceptance:

- CTA clicks are covered by E2E tests.
- No CTA silently does nothing.
- Button text fits on mobile without overflow.

### ADQ-06 - Preserve parent, child, and switching behavior

Priority: P0

Depends on: ADQ-04

Primary surfaces: `AnalyticsSection.svelte`, app store subscriptions,
analytics E2E tests.

Description: keep current role behavior intact while changing the layout.

Do:

- Parent with no children still sees the add-child empty state.
- Parent with selected child sees that child's quests.
- Child sees only their own quests.
- Switching children reloads quest data and compact stats.
- Timeframe changes update quest progress and details data.

Acceptance:

- Existing analytics parent and child E2E coverage remains green after selector updates.
- New assertions prove child switching changes quest progress.
- No stale quests remain after child switch.

### ADQ-07 - Move charts into collapsible details

Priority: P1

Depends on: ADQ-04

Primary surface: `AnalyticsSection.svelte`.

Description: keep analytics depth without making charts dominate the tab.

Do:

- Add one collapsible block below the quest list: "Details" or localized equivalent.
- Move task charts, reward charts, trend chart, and recommendation cards into that block.
- Keep the block collapsed by default on mobile.
- Desktop can default to collapsed or remember the last local UI state, but must keep quests first.

Acceptance:

- Charts and recommendations are not above the quest list.
- Expanding details reveals the same analytics information as before.
- Collapsing details does not destroy quest state.

### ADQ-08 - Keep chart rendering reliable after expand

Priority: P1

Depends on: ADQ-07

Primary surfaces: chart rendering code in `AnalyticsSection.svelte`.

Description: Chart.js canvases need a visible container before final sizing, so
the rendering lifecycle must account for collapsed details.

Do:

- Render or update charts after the details block is expanded.
- Avoid rendering charts into hidden zero-size containers.
- Keep timeframe changes updating chart data whether the block is open or closed.
- Destroy old chart instances when data reloads or component unmounts.

Acceptance:

- Charts are nonblank after expanding details.
- Timeframe changes update charts after expand.
- Repeated expand/collapse does not stack duplicate Chart.js instances.

### ADQ-09 - Add purchase-oriented quest

Priority: P1

Depends on: ADQ-01, ADQ-04

Primary surfaces: analytics view model, app store shop item data if available,
Shop section navigation.

Description: make the tab motivate reward purchases by showing progress toward
the nearest useful shop item.

Do:

- If shop item data is available in the app store, choose the nearest affordable or almost-affordable item.
- Show progress as current balance toward item cost.
- If no item data is available, show a generic "save coins for a reward" quest.
- CTA opens Shop.

Acceptance:

- A child with enough balance sees a purchase-ready nudge.
- A child below item cost sees how many coins remain.
- The fallback quest works when no shop items are loaded.

### ADQ-10 - Add task-oriented quest

Priority: P1

Depends on: ADQ-01, ADQ-04

Primary surfaces: analytics view model, task data from app store,
recommendation cards.

Description: make the tab motivate task completion with a concrete next task
rather than generic analytics copy.

Do:

- Prefer an analytics recommendation if it maps to a known task.
- Otherwise choose an available task with a positive coin reward.
- Show the task coin value as the reward signal.
- CTA opens Tasks.

Acceptance:

- A recommended task appears as a quest when available.
- If recommendations are empty, a useful task fallback appears.
- If no tasks exist, the quest changes to a setup/first-action state.

### ADQ-11 - Redesign empty and low-activity states

Priority: P1

Depends on: ADQ-09, ADQ-10

Primary surfaces: `AnalyticsSection.svelte`, analytics view model, i18n messages.

Description: avoid empty charts as the first impression.

Do:

- For child view with no activity, show quests for first task completion, first coins, and first reward target.
- For parent view with a child but no activity, show parent-safe prompts that explain what to set up or review.
- Preserve the no-child empty state exactly as a separate parent setup flow.
- Keep the details block available but de-emphasized.

Acceptance:

- No-activity child view still has 3 actionable quests.
- No-activity parent selected-child view does not look broken or blank.
- Parent no-child empty state still shows only the add-child action.

### ADQ-12 - Polish responsive and accessible UI

Priority: P2

Depends on: ADQ-04, ADQ-07

Primary surfaces: `AnalyticsSection.svelte`, shared app CSS if needed.

Description: make the new quest layout feel native to the current app.

Do:

- Use stable dimensions for progress bars, CTA buttons, icons, and quest rows.
- Ensure text wraps cleanly on mobile.
- Keep color contrast readable for progress and reward labels.
- Add keyboard-friendly expand/collapse behavior for details.
- Avoid nested cards and decorative-only visual weight.

Acceptance:

- Mobile width does not overflow or overlap.
- Keyboard users can tab through quests and expand details.
- Progress bars have accessible text labels or equivalent context.

### ADQ-13 - Add localized quest copy

Priority: P2

Depends on: ADQ-12

Primary surfaces: `apps/web/src/lib/i18n/messages/en/analytics.ts`,
`apps/web/src/lib/i18n/messages/ru/analytics.ts`.

Description: keep all new quest strings in the existing analytics i18n domain.

Do:

- Add semantic keys for quest titles, descriptions, rewards, statuses, and CTAs.
- Keep English as the source-of-truth dictionary.
- Keep Russian copy concise enough for compact cards.
- Use existing number formatting helpers for coins and counts.

Acceptance:

- No new hardcoded user-facing quest strings remain in the component.
- English and Russian dictionaries contain matching keys.
- Long Russian labels do not break mobile layout.

### ADQ-14 - Run regression and release verification

Priority: P0

Depends on: ADQ-03 onward

Primary surfaces: frontend unit tests, Playwright E2E tests, lint, build.

Description: verify that the redesign changes presentation and motivation
without breaking analytics behavior.

Do:

- Run frontend lint.
- Run analytics view-model unit tests.
- Run analytics E2E tests for parent, child, timeframe switch, and child switch.
- Run the child shop E2E flow to verify task completion and purchase flow still connect.
- Run a production build.

Acceptance:

- All targeted tests pass or blockers are documented with exact command output.
- E2E proves quests render for parent and child.
- E2E proves task and shop CTAs navigate correctly.
- E2E proves details expand and charts are visible.

[↑ Back to top](#top)

<a id="definition-of-done"></a>

## ✅ Definition of Done

- The Achievements tab opens with daily quests above analytics details.
- Each quest has progress, reward signal, and a working CTA.
- The tab motivates both earning coins through tasks and spending coins in the shop.
- Charts and recommendations remain available in a compact collapsible details block.
- Parent, child, no-child, no-activity, child-switch, and timeframe flows are covered.
- New user-facing copy is localized in English and Russian.
- Verification includes lint, unit tests, analytics E2E, child shop E2E, and production build.

[↑ Back to top](#top)
