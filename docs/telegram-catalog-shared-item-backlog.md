# Telegram Catalog Shared Item - Implementation Backlog

## Goal

Make task and reward catalog entries use the same shared item block as the
Telegram task and reward lists. Both catalog modes must retain their existing
search, filters, details, add, duplicate, and bulk-selection behaviour while
sharing the list surface, icon, title, metadata, divider, focus, and compact
mobile layout implementation.

## Architectural decisions

- `TelegramEntityRow.svelte` is the single owner of an entry's common layout:
  optional leading selection control, icon tile, title, metadata, trailing
  action, divider, and focus treatment. `TelegramReadyCatalog.svelte` owns
  catalog-specific data, translations, filtering, bulk state, and dispatched
  add/details events.
- `TelegramListSurface.svelte` remains the sole catalog container. Do not keep
  the catalog-local `.list` border/card implementation or create a second
  row/card abstraction.
- Extend the existing row only with a named leading-selection slot needed for
  catalog bulk mode. Do not introduce task/reward/catalog-specific booleans or
  move business flows into the UI primitive.
- This is presentation composition only: catalog DTOs, app stores, API calls,
  telemetry event payloads, translations, and persistence remain compatible.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-CAT-ROW-1 | P1 | - | Lock the required shared rendered contract for both catalog kinds before changing markup. |
| 2 | TASK-CAT-ROW-2 | P1 | TASK-CAT-ROW-1 | Replace the duplicated catalog row and surface with the established shared components. |

## TASK-CAT-ROW-1: Characterize shared catalog row behaviour

**Status:** IN_PROGRESS
**Priority:** P1  
**Depends on:** -

**Exact scope:**

Extend the parent Telegram Playwright coverage to describe task and reward
catalog rows at the narrow 320 px fixture viewport. The checks must identify
the common row/surface contract that the subsequent migration will expose;
they must not alter production components.

**Files:**

- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Search anchor: `const catalog = page.locator('.catalog .list')` identifies
  the current task-catalog assertion block.
- Search anchor: `catalog: { tasks:` in the same file identifies the task and
  reward fixtures that must remain representative.

**Goal:**

Prevent the shared-item migration from changing either catalog's interactive
behaviour or causing narrow-screen overflow.

### Outcome

Both catalog modes are asserted as one labelled list surface with direct shared
rows. Each row presents an icon, readable title, coin/group and frequency
metadata, and its appropriate add state; the bulk selection control is still
reachable and does not change the order or accessibility of the entry.

### Architectural decision

Playwright owns observable DOM semantics, touch geometry, keyboard focus, and
viewport overflow. It must not lock catalog-private CSS class names or test
implementation details that are deliberately being removed.

### Required changes

1. Update the existing task-catalog assertions from local `.list`/`.row`
   structure to the intended `TelegramListSurface` and `TelegramEntityRow`
   semantics, including a direct-row count and the icon/title/metadata/action
   relationship.
2. Navigate to the reward catalog and add equivalent coverage for its price,
   group, frequency, and add state; use the existing bilingual locators.
3. Exercise bulk mode for at least one catalog kind and prove that its
   checkbox remains labelled, keyboard reachable, and does not create page
   horizontal overflow.
4. Assert the title trigger opens the existing details flow and the add action
   remains separately reachable, without asserting new network contracts.

### Out of scope

- Production Svelte or CSS changes.
- Visual screenshot baselines, API fixture contract changes, or new catalog
  filters.
- Child task/reward list changes.

### Acceptance criteria

- At 320 px, task and reward catalog pages have one `.list-surface` with
  direct `.entity-row` items and no horizontal document overflow.
- Each populated catalog row has one icon tile, a visible or safely clamped
  localized title, two readable metadata lines, and a separately focusable
  title trigger and add/added state.
- The task and reward add controls retain their accessible labels and an
  interactive control is at least 44 by 44 px.
- Bulk selection keeps a labelled checkbox before its entry content, works
  with keyboard interaction, and does not prevent details or adding once bulk
  mode is exited.
- Tests use local Telegram SDK fixtures and intercepted APIs only.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-parent.spec.ts
```

### CHECKPOINT

- completed: Replaced the catalog task assertions with shared list/row contract checks and added reward-catalog, details, and bulk keyboard coverage.
- remaining: Make the catalog render the asserted `list-surface`/`entity-row` contract in TASK-CAT-ROW-2, then rerun this focused E2E.
- changed files: `apps/web/tests/e2e/telegram-parent.spec.ts`
- current test/verification status: Focused Playwright run is blocked because the current catalog still renders legacy `.list`/`.row`; no production changes were made in this task.
- confirmed blockers: The required shared catalog DOM does not exist until the separately scoped migration task.
- next exact action: Resume TASK-CAT-ROW-1 after the catalog migration and rerun `PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-parent.spec.ts`.

### Commit

```bash
git add apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "test(web): characterize shared Telegram catalog rows"
```

## TASK-CAT-ROW-2: Migrate both catalogs to the shared item block

**Status:** TODO
**Priority:** P1  
**Depends on:** TASK-CAT-ROW-1

**Exact scope:**

Replace the catalog-local `.list` and `.row` composition in
`TelegramReadyCatalog.svelte` with the same `TelegramListSurface` and
`TelegramEntityRow` implementation used by task and reward lists. Make the
smallest shared-row extension required to position the catalog bulk checkbox
before the icon.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte`.
- Modify `apps/web/src/lib/components/telegram/ui/TelegramEntityRow.svelte`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Search anchor: `{#if bulkMode}` in `TelegramReadyCatalog.svelte` identifies
  the leading checkbox behaviour that the shared row must preserve.
- Search anchor: `TelegramEntityRow interactive` in
  `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte` identifies
  the established consumer composition.

**Goal:**

Task and reward catalog entries render through one shared item-block code path
with the task/reward lists, while every current catalog action and visual state
continues to behave as before.

### Outcome

The catalog has one `TelegramListSurface`; every template is a
`TelegramEntityRow` with named icon, title, metadata, and interactive action
slots. Bulk mode additionally renders a shared-row leading-selection slot.
No catalog-local copy of shared surface, row, icon, title, metadata, divider,
or focus CSS remains.

### Architectural decision

`TelegramEntityRow` owns generic placement only. Its optional named
`selection` slot must reserve the leading column only when populated, so all
existing task, reward, activity, and request consumers retain their current
geometry. `TelegramReadyCatalog` continues to own checkbox state, add versus
added rendering, `openDetails`, `addOne`, and catalog-specific labels.

### Required changes

1. Add an optional named leading-selection slot and scoped layout rules to
   `TelegramEntityRow.svelte`; it must place a supplied checkbox before the
   icon without changing rows that omit the slot.
2. Import and compose `TelegramListSurface` and `TelegramEntityRow` in
   `TelegramReadyCatalog.svelte`. Map the existing icon, title button,
   amount/group and frequency metadata, add/added button, and bulk checkbox
   into the corresponding slots.
3. Remove superseded catalog-local surface/row/icon/text/metadata/action and
   focus CSS. Keep only catalog-specific bulk bar, filters, empty state, and
   visual tones not owned by the shared primitive.
4. Preserve the existing event dispatches, duplicate disabled state, bulk
   selection reset, details opening, i18n labels, and `getTelegramEntityIcon`
   mapping exactly; do not duplicate or move catalog filtering/state logic.
5. Update the focused browser coverage from TASK-CAT-ROW-1 only as needed for
   stable semantic locators and verify both task and reward catalog paths.

### Out of scope

- Changes to catalog content, filters, search matching, ordering, telemetry,
  bulk-add semantics, group mapping, stores, API endpoints, or persistence.
- Changes to non-catalog consumers other than compatibility of the shared row
  slot extension.
- A separate reusable catalog item component or a second list surface.

### Acceptance criteria

- Opening either parent catalog renders catalog entries through
  `TelegramListSurface` and `TelegramEntityRow`, using the same shared item
  block as Telegram task and reward lists.
- Task entries still show coin value, group, and frequency; reward entries
  still show price, group, and frequency; long Russian and English titles do
  not overlap the trailing control at 320 px.
- Normal mode retains separate accessible details and add/added controls; add
  still dispatches the current catalog action and added templates remain
  disabled.
- Bulk mode retains a labelled checkbox before the icon, selection count and
  bulk add behaviour; exiting it restores normal add/added actions.
- Existing consumers without a selection slot retain their layout, and all
  interactive catalog controls have visible keyboard focus and at least 44 by
  44 px hit targets.
- No API, store, migration, translation, or telemetry contract changes are
  introduced.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-parent.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte apps/web/src/lib/components/telegram/ui/TelegramEntityRow.svelte apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "refactor(web): share Telegram catalog item rows"
```
