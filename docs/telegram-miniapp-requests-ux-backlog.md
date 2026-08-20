# Telegram Mini App Request Lists UX - Implementation Backlog

## Goal

Make the Telegram Mini App request areas and the child **My tasks** area feel like the existing Tasks and Rewards lists. The parent home inbox, **Needs attention**, the child Tasks tab, and child Activity -> **My requests** must each use a single list surface with divider-separated rows rather than cards nested inside another visual group. The child Activity -> **My requests** list must show product icons instead of title emoji.

The supplied reference `docs/earnit-kids-requests-reference-v2(1).html` is the layout authority: request content occupies the full line next to the icon, and parent decision buttons form a separate compact row below it.

## Architectural decisions

- Request data, status ordering, mutations, localization, and API contracts remain owned by the existing store and request services. This is a presentation-only change; no backend, migration, DTO, or endpoint work is required.
- Reuse the established Mini App list language from Tasks and Rewards: one white bordered list container, `0.6rem` horizontal inset, divider-separated rows, indigo `TelegramIcon` tiles, existing typography, and existing focus treatment. Do not introduce a second request-specific design-token system.
- `TelegramRequestList.svelte` remains the parent decision-list owner. `TelegramRequestRow.svelte` remains the shared child row primitive; do not duplicate title, status, date, coin, or request-kind formatting in `TelegramChildRequestList.svelte`.
- `TelegramChildTasks.svelte` remains the owner of the child task list, its grouping filter, request sheet, pending/limit guards, and today progress. Preserve that ownership; do not move it into the request-list components or add a parallel child-task store.
- Parent actions remain in `TelegramRequestList.svelte` and child cancellation remains in `TelegramChildRequestList.svelte`. Their existing handlers, busy states, error states, role-specific labels, and response behavior are backward compatible.
- For child requests, always derive the tile from the existing `getTelegramEntityIcon` / `requestKind` path. Continue stripping a leading emoji from the rendered title, but never render that emoji as the list icon.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 | P1 | - | Refactors the parent decision inbox without changing its workflow. |
| 2 | P1-2 | P1 | P1-1 | Makes child My tasks explicitly follow the one-surface list invariant. |
| 3 | P1-3 | P1 | P1-2 | Aligns the child request list with the same established list language. |
| 4 | P2-1 | P2 | P1-1, P1-2, P1-3 | Locks the reference geometry and both role-specific regressions in browser tests. |

## P1-1: Convert the parent Needs attention inbox into list rows

**Status:** ✅ Completed
**Priority:** P1  
**Depends on:** -

### Outcome

On the parent Home tab, pending requests under **Needs attention** render as rows inside one Tasks/Rewards-style list surface. No pending request is presented as an inner standalone card, while approve and reject controls remain clear and usable.

### Architectural decision

`TelegramRequestList.svelte` owns the parent request-list DOM and its decision controls, so it must change the list/card hierarchy in place. Keep `TelegramParentHome.svelte` as the owner of the heading, count, and two-item/show-all policy; do not create another parent request component or move request data into local UI state.

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramRequestList.svelte`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.

### Work

1. Replace the per-request bordered `request-card` presentation with one outer list surface and divider-separated request rows, matching the existing Tasks/Rewards list geometry, colors, icon tile, typography, and row padding.
2. Keep each row's icon + title + child/request-type metadata + coin amount in its full-width primary content area. Preserve the existing title normalization and parent request metadata.
3. Keep pending approve/reject controls below the primary row content, in a two-column action row that cannot reduce the title's available width. At narrow widths, retain the reference's full-width fallback rather than clipping, overlapping, or horizontally scrolling.
4. Preserve loading, empty, retry, decision-error, disabled/busy, keyboard-focus, and screen-reader labels exactly through the existing component contract.
5. Add or update the parent Mini App E2E fixture/assertions for task and reward pending requests, both decision buttons, the separate action row, and no horizontal overflow at 320px.

### Acceptance criteria

- With two or more pending requests, there is one white bordered list container with a divider between rows; the individual request rows have no independent outer border, background, or rounded-card nesting.
- The list visually matches the current Mini App Tasks/Rewards lists: indigo icon tile, dark title, muted metadata, coin amount, border color, radius, and row spacing.
- At 320px, a long request title uses the available width beside the icon; approve/reject controls start on a later row and do not force title truncation, overlap, or horizontal page overflow.
- A pending task request and a pending reward request can still be approved or rejected once; the clicked controls are disabled while the existing request mutation is in progress.
- Loading, empty, API-error/retry, and decision-error states remain available and accessible; visible focus remains present for keyboard users.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- tests/e2e/telegram-parent.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramRequestList.svelte apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "refactor(web): align parent request inbox with lists"
```

## P1-2: Keep child My tasks as one un-nested list

**Status:** ✅ Completed
**Priority:** P1  
**Depends on:** P1-1

### Outcome

On the child Tasks tab, **My tasks** is one continuous Mini App list surface: the task rows sit directly in that surface with dividers, not inside individual nested cards or secondary panel shells.

### Architectural decision

`TelegramChildTasks.svelte` already owns the task rows and request-sheet action. Retain its current store-derived `items`, group filtering, request mutation, pending-state, and period-limit behavior, while making the single-list DOM/CSS contract explicit. Do not reuse `TelegramRequestRow.svelte`: task rows have a different primary action and progress metadata.

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.

### Work

1. Make the Tasks-tab hierarchy explicit as one outer list surface with directly nested task rows and divider separators; remove any redundant wrapper/card styling discovered in the final DOM/CSS without changing the current group selector, summary, or progress bar.
2. Preserve the existing Mini App task-row visual language: indigo semantic `TelegramIcon` tile, stripped title emoji, coin/group/last-completed metadata, and right-aligned request control.
3. Preserve the task request sheet, duplicate-pending guard, period-limit guard, success/error/stale feedback, and data-snapshot refresh behavior.
4. Add focused E2E assertions for several child tasks, including a long title at 320px, that prove the rows share one list container, show separators, keep a 44x44px request target, and do not create horizontal overflow.

### Acceptance criteria

- Multiple child tasks are rendered inside one white, rounded, bordered list surface with exactly one separator between adjacent rows; a task row has no independent card border, white background, or outer radius.
- Existing task icons, title normalization, progress summary, group navigation, pending indicator, and task-request flow remain unchanged.
- At 320px, the long task title stays inside the viewport, the request control remains reachable, and the document has no horizontal overflow.
- Keyboard users can focus and invoke each task request control, and all actionable controls retain a minimum 44x44px touch target.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- tests/e2e/telegram-child.spec.ts tests/e2e/telegram-layout.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildTasks.svelte apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "refactor(web): keep child tasks in one list"
```

## P1-3: Align My requests rows and replace child emoji tiles

**Status:** ✅ Completed
**Priority:** P1  
**Depends on:** P1-2

### Outcome

Child Activity -> **My requests** uses one Tasks/Rewards-style list with divider-separated rows. Task and reward requests display the corresponding existing Mini App vector icon, not an emoji copied from the request title.

Implemented the shared list surface, icon-only request row tile, responsive cancel target, and E2E coverage for emoji-prefixed task and reward titles.

### Architectural decision

Keep `TelegramChildRequestList.svelte` responsible only for sorting, pagination, states, and cancel action, and retain `TelegramRequestRow.svelte` as its presentational row. Extend the shared primitive rather than recreating its title/status/date/coin markup in the child list; the primitive's child-only icon policy must not alter the parent request component.

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.

### Work

1. Change the child list from individually bordered request cards to a single Tasks/Rewards-style list container with inset padding and separators, while preserving pending-first and newest-first ordering plus Show more behavior.
2. Update the shared row layout to use the existing `TelegramIcon` selected by `getTelegramEntityIcon` and `requestKind` for every child request. Keep `stripLeadingEmoji` for the title but remove the conditional emoji tile rendering.
3. Align row hierarchy, tile colors, metadata/status placement, coin line, spacing, and responsive behavior with the existing child Tasks and Rewards rows. Status chips and the cancel control remain distinguishable from non-actionable rows.
4. Preserve the existing empty/loading/error/retry/cancel-error states, cancellation confirmation workflow, busy/disabled state, date formatting through `formatLastUsedTime`, and i18n labels.
5. Add or update E2E coverage using titles with leading emoji for both task and reward requests. Assert that the rendered tile contains the expected icon semantics rather than the emoji, the normalized title remains readable, pending-only cancellation is reachable, and the 320px view has no horizontal overflow.

### Acceptance criteria

- My requests has one outer white, rounded, bordered list surface and visible dividers between multiple rows; no child row appears as a separate nested card.
- A title such as `🏠 Clean room` displays `Clean room` as its title and a task-style `TelegramIcon` tile; a reward request receives the existing reward-style tile. The leading emoji is not displayed as the icon.
- Pending, approved, rejected, cancelled, and neutral request statuses retain their correct localized chip color and label; only pending rows expose the cancel action.
- At 320px, long titles, status/date, amount, and cancel control remain reachable without horizontal page overflow. All actionable controls keep a visible keyboard focus state and a 44x44px minimum touch target.
- Loading, empty, retry, Show more, cancel failure, and successful cancellation behavior are unchanged.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- tests/e2e/telegram-child.spec.ts
```

✅ Passed: lint, 177 unit tests, production build, and 3 child E2E tests.

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/src/lib/components/telegram/TelegramRequestRow.svelte apps/web/tests/e2e/telegram-child.spec.ts
git commit -m "refactor(web): unify child request list styling"
```

## P2-1: Add request-list visual and geometry regression coverage

**Status:** ⬜ Not started  
**Priority:** P2  
**Depends on:** P1-1, P1-2, P1-3

### Outcome

Browser coverage prevents a future change from restoring nested request cards, emoji child tiles, or narrow-screen clipping in either Telegram Mini App role.

### Architectural decision

Extend the existing Telegram visual-regression fixture rather than adding a separate application harness. Fixtures stay client-side and use the existing mocked bootstrap endpoints; no production data or new test-only UI contract is introduced.

### Files

- Modify `apps/web/tests/e2e/visual-regression-miniapp.spec.ts`.

### Work

1. Add parent request, child request, and child task fixtures containing multiple rows, a long title, task/reward kinds, and a leading-emoji child request title.
2. Assert the semantic hierarchy/geometry that distinguishes one list surface plus rows from nested cards, including divider visibility and no independent row border/background shell.
3. At 320px, 375px, and 430px, check page horizontal geometry and verify that request actions do not encroach on title bounds.
4. Add focused screenshots only if this existing suite is configured to retain visual snapshots; otherwise keep DOM geometry and interaction assertions deterministic without introducing a snapshot baseline.

### Acceptance criteria

- The test visits the parent Home inbox, child Tasks tab, and child Activity -> My requests with realistic multi-row data at 320px, 375px, and 430px.
- It fails if child task rows regain a standalone card shell inside their list surface.
- It fails if individual request rows regain a standalone card shell inside the list surface.
- It fails if a child request with a leading emoji uses an emoji tile instead of the established icon tile.
- It fails if the document becomes horizontally scrollable or parent decisions reduce the long-title content area by occupying its primary row.

### Verification

```bash
cd apps/web && npm run test:e2e -- tests/e2e/visual-regression-miniapp.spec.ts
```

### Commit

```bash
git add apps/web/tests/e2e/visual-regression-miniapp.spec.ts
git commit -m "test(web): cover Telegram request list UX"
```

## Final quality gate

Run after all three commits, against their combined final diff:

```bash
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e
```
