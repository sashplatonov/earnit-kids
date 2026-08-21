# Telegram Mini App Catalog List Alignment - Implementation Backlog

## Goal

Make the list items in the Telegram Mini App's tasks, rewards, history/activity,
and requests as compact, scannable, and visually coherent as the current
`TelegramReadyCatalog` rows. The catalog remains the visual reference: icon on
the left, title and two compact metadata lines in the middle, and a clear
trailing action or value, all inside one list surface.

## Architectural decisions

- `TelegramReadyCatalog.svelte` is the visual reference only. Its catalog
  filtering, bulk-selection, add, and details flows must not be moved into
  feature lists, and it must not become a second data source.
- `TelegramEntityRow.svelte` remains the single shared owner of the outer row
  grid, icon tile, title/metadata column, divider, keyboard focus treatment,
  and narrow-screen reflow. Feature components continue to own their data,
  translations, amount/status semantics, and mutations.
- Keep exactly one rounded, bordered `TelegramListSurface` per list. A row,
  an actions wrapper, or a feature section must not recreate an inner card;
  this explicitly prevents the earlier nested-container regression.
- Preserve the current app-store/API contracts, i18n keys, request ordering,
  deep-link context, pagination, and parent/child permissions. This is a
  presentation-only change, so it requires no migration, endpoint, DTO, or
  parallel client state.
- Keep task/reward completion, grant, edit, archive, request, approve, reject,
  and cancel controls as feature-owned actions. Do not introduce a generic
  "row mode" with business-specific boolean props.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-P1-1 | P1 | - | Establish measurable catalog-derived geometry before changing the shared primitive. |
| 2 | TASK-P1-2 | P1 | TASK-P1-1 | Migrate the task and reward rows through the shared primitive while retaining their role-specific actions. |
| 3 | TASK-P1-3 | P1 | TASK-P1-1, TASK-P1-2 | Apply the proven compact row contract to history/activity and request status/action layouts. |

## TASK-P1-1: Characterize the catalog-derived list-item contract

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Add browser coverage that treats the already shipped `TelegramReadyCatalog`
row as the reference and records the required compact geometry for task,
reward, history, and request consumers at a 320 px viewport. This task adds
tests only; it does not alter production markup or styles.

**Files:**

- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Search anchor: `class="row"` in `apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte` identifies the visual reference that assertions must compare against.

**Goal:**

Give the following implementation tasks executable visual constraints rather
than relying on a subjective interpretation of the screenshots.

### Outcome

At 320 px, each affected list has one surface, a stable left icon / readable
central text / trailing-control-or-value arrangement, compact metadata, and
no horizontal overflow. The catalog's existing row remains unchanged while
the tests protect the intended relationship.

### Architectural decision

Playwright owns rendered geometry, keyboard reachability, and role-specific
actions. Do not use static CSS snapshots or assert catalog-internal DOM from
other feature tests; compare observable row constraints and retain semantic
locators.

### Required changes

1. Extend existing child, parent, and layout fixtures with long localized
   titles, two metadata values, representative positive/negative amounts, and
   pending request statuses where needed.
2. Assert the catalog reference exposes one icon tile, a two-line-capable text
   column, and a trailing add/added control without horizontal page overflow.
3. Assert each affected list has one `.list-surface` with direct
   `.entity-row` children; no descendant row introduces its own bordered,
   rounded white card.
4. Assert title text is visible or safely clamped rather than hidden behind a
   trailing control, actions remain at least 44 by 44 px, and Tab produces a
   visible focus indicator for an interactive title/action.

### Out of scope

- Changing `TelegramReadyCatalog.svelte` styles or its catalog behaviour.
- Changing API fixtures, translations, data ordering, or business actions.
- Pixel-perfect screenshot comparison, visual-regression tooling, and
  dashboard routes.

### Acceptance criteria

- At 320 px, catalog, child task/reward, activity/history, child request, and
  parent request fixtures do not increase `documentElement.scrollWidth` beyond
  `innerWidth`.
- Tests prove a single outer list surface and direct divider-separated rows for
  all populated affected lists.
- A long Russian or English title retains at least one readable line and does
  not overlap a status, coin amount, or action.
- Interactive controls are reachable by keyboard with visible focus, and each
  row action retains a 44 by 44 px minimum target.
- Tests continue to use local Telegram SDK fixtures and intercepted APIs only.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-layout.spec.ts telegram-child.spec.ts telegram-parent.spec.ts
```

### Commit

```bash
git add apps/web/tests/e2e/telegram-layout.spec.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "test(web): Characterize Telegram catalog list alignment"
```

## TASK-P1-2: Compact shared task and reward rows around the catalog pattern

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-P1-1

**Exact scope:**

Adjust the shared row layout and its task/reward consumers so parent and child
task/reward lists use the catalog's compact visual hierarchy without changing
their filtering, request, completion, grant, edit, archive, or menu flows.

**Files:**

- Modify `apps/web/src/lib/components/telegram/ui/TelegramEntityRow.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildRewards.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentTasks.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentRewards.svelte`.
- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Search anchor: `.row-main` in `apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte` identifies the target content/action relationship.

**Goal:**

Tasks and rewards become dense and easily scannable like catalog entries,
while their title, coin/group, last-use, disabled state, and role-specific
controls remain understandable and tappable.

### Outcome

The shared entity row gives its central content the available width, keeps
metadata in a consistent compact stack, and reserves a stable trailing column
for the existing check, grant, and more actions. Long titles wrap or clamp
safely instead of forcing an oversized or fragmented row.

### Architectural decision

Extend slot layout and semantic CSS in `TelegramEntityRow.svelte`; do not
duplicate catalog CSS into four feature components and do not make the catalog
consume the feature row. Feature-local CSS may only express a truly
feature-specific control tone or state.

### Required changes

1. Rework the shared row's grid/flex sizing, vertical alignment, title line
   handling, metadata stack, and narrow breakpoint to match the catalog's
   compact reading order while preserving direct-row dividers.
2. Remove task/reward CSS that duplicates shared title, metadata, icon, or
   generic action geometry; retain only task/reward-specific states such as a
   disabled request, primary completion/grant tone, archive opacity, and
   contextual menus.
3. Keep the central title trigger independent from its trailing action so both
   remain keyboard-focusable and each action keeps its existing accessible
   name and at least 44 by 44 px touch target.
4. Update the focused browser assertions from TASK-P1-1 for child and parent
   task/reward rows, including long title, group/last-use metadata, disabled
   controls, and no-overflow states.

### Out of scope

- Catalog design or catalog selection/add semantics.
- New task/reward fields, filters, group ordering, persistence, or endpoints.
- Changing confirmation sheets, dropdown menu contents, or request logic.

### Acceptance criteria

- Parent and child task/reward lists show the icon, title, coin/group and
  last-use metadata in a predictable compact order comparable to the catalog.
- On a 320 px viewport, long localized names do not overlap or push the
  completion, request, grant, or more-action control off screen.
- Rows remain transparent and square inside exactly one bordered,
  rounded `TelegramListSurface`; neighbouring rows alone own separators.
- Existing disabled/pending/archived visual states and all parent/child
  mutations retain their present behaviour after reload.
- Keyboard focus is visibly scoped to title triggers and actions; no control
  loses its accessible label or 44 by 44 px target.

### Targeted validation

```bash
cd apps/web && npm run lint
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-layout.spec.ts telegram-child.spec.ts telegram-parent.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/ui/TelegramEntityRow.svelte apps/web/src/lib/components/telegram/TelegramChildTasks.svelte apps/web/src/lib/components/telegram/TelegramChildRewards.svelte apps/web/src/lib/components/telegram/TelegramParentTasks.svelte apps/web/src/lib/components/telegram/TelegramParentRewards.svelte apps/web/tests/e2e/telegram-layout.spec.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "refactor(web): Align Telegram task and reward rows with catalog"
```

## TASK-P1-3: Align history, activity, and request rows without losing status actions

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-P1-1, TASK-P1-2

**Exact scope:**

Use the compact shared entity-row contract for both parent and child history
and for parent/child request lists, preserving timestamps, amount direction,
pending-first order, status chips, pagination, retry states, and decision or
cancellation actions.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramHistoryList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte`.
- Modify `apps/web/tests/e2e/telegram-activity.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Search anchor: `TelegramHistoryList` in `apps/web/src/lib/components/telegram/TelegramChildShell.svelte` and `apps/web/src/lib/components/telegram/TelegramParentHome.svelte` identifies both activity consumers.

**Goal:**

History/activity and requests have the same legibility and item density as
catalog-referenced task/reward rows, while their distinct timestamp, status,
amount, approve/reject, and cancel information stays readable.

### Outcome

An activity entry places its signed coin value in a stable trailing area and
its time under the title. A request reserves that same readable hierarchy for
status and time without causing status chips or action buttons to displace the
title; pending actions wrap below only when necessary.

### Architectural decision

`TelegramRequestPresentation` remains the single source for request kind,
metadata, status, amount direction, and ordering. `TelegramRequestRow` only
maps that presentation into the shared layout. History continues to derive its
own display fields from `HistoryEntry`; neither list creates a second mapper
or copied request state.

### Required changes

1. Adapt history title/time/amount slots to the compact shared layout and
   retain positive versus spending colours, relative-date formatting,
   loading/empty/error/retry states, and load-more behaviour.
2. Reorganize request status, timestamp, metadata, amount, and pending action
   slots so the title has priority; use a deliberate narrow layout instead of
   the shared row's generic trailing-column collapse.
3. Retain parent approve/reject and child cancel callbacks, busy/disabled
   state, error alert, pending-first ordering, and child request pagination.
4. Add browser coverage for long activity/request titles, positive and
   negative amounts, known status chips, pending actions, and 320 px geometry
   in both parent home and child Activity tabs.

### Out of scope

- Changing request presentation mapping, status vocabulary, API calls, sort
  order, pagination size, or mutations.
- Changing activity API pagination, timestamp formatter, data model, or
  dashboard analytics.
- New empty-state copy, icons, or a generic action framework.

### Acceptance criteria

- Parent home and child Activity → History display a readable title, timestamp,
  and signed amount in catalog-like density with no nested card or horizontal
  overflow at 320 px.
- Parent and child request entries preserve task/reward icon, metadata, status,
  timestamp, and amount; long titles cannot overlap status or actions.
- Pending parent approve/reject and child cancel actions remain reachable with
  visible keyboard focus, 44 by 44 px targets, correct disabled/busy state,
  and unchanged API callbacks.
- Approved, rejected, cancelled, and unknown-safe request presentation,
  pending-first ordering, retry/error states, and load-more work exactly as
  before.
- The activity tab and parent home retain a single visual list surface per
  populated list, with direct transparent divider-separated rows.

### Targeted validation

```bash
cd apps/web && npm run lint
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-activity.spec.ts telegram-child.spec.ts telegram-parent.spec.ts telegram-layout.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramHistoryList.svelte apps/web/src/lib/components/telegram/TelegramRequestRow.svelte apps/web/src/lib/components/telegram/TelegramRequestList.svelte apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/tests/e2e/telegram-activity.spec.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "refactor(web): Align Telegram activity and request rows with catalog"
```
