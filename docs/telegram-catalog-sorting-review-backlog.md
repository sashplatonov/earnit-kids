# Telegram catalog sorting - Review backlog

## Goal

Restore a reliable Mini App quality gate and make task and reward sorting behave identically for parents and children: group order first with ascending coins inside each group by default, or one ascending coin order when explicitly selected.

## Architectural decisions

- Group preference remains server-owned in `ChildDto` and the existing `POST /children/{childId}/group-order` flow; the new view-only sorting mode stays local to the screen because the requirement does not ask to persist it.
- `orderGroups` must expand the effective saved order with every group present in the current list before `sortCatalogItems` receives it. Passing only saved entries creates an accidental global coin sort for all remaining groups.
- Keep `sortCatalogItems` as the sole presentation sorter. Do not duplicate group/coin comparator logic in the four role-specific components.
- The E2E suite must assert user-visible reward-button semantics (`.grant`), not a task implementation class (`.check`).

## Rejected observations

- The backend group-order API, service, mapper, DTO, and persistence fields correctly preserve parent and child orders. No backend, migration, authorization, or API-contract task is required for this review.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P0-1 | P0 | - | The targeted child Mini App E2E gate is currently red before it reaches the sorting assertions. |
| 2 | P1-1 | P1 | - | Restores the required default grouping for child task and reward lists. |
| 3 | P2-1 | P2 | P1-1 | Makes the new sort control operable on touch devices. |
| 4 | P2-2 | P2 | P0-1, P1-1, P2-1 | Adds cross-role browser proof for the two sorting modes. |

## P0-1: Repair the child reward E2E selector

**Status:** DONE
**Priority:** P0  
**Depends on:** -

**Exact scope:**

Repair the currently failing assertion in the child Mini App flow without changing the reward request UI contract.

**Files:**

- Modify `apps/web/tests/e2e/telegram-child.spec.ts` (`child Mini App opens tasks first and requests a grouped task`).
- Reference `apps/web/src/lib/components/telegram/TelegramChildRewards.svelte` (`row-action grant`) only to preserve the existing UI contract.

**Goal:**

The focused child Mini App E2E test passes and still proves that an unaffordable reward cannot be requested.

### Outcome

The test locates the disabled reward action as the reward action, rather than as a task completion action that does not exist in that list.

### Architectural decision

Keep task and reward action CSS semantics distinct: `.check` represents task completion, while `.grant` represents a reward request. The test is the stale layer; no production component change belongs in this task.

### Required changes

1. Change the reward-action locator to target the rendered `.grant` button.
2. Retain the disabled assertion for the 50-coin reward at a 40-coin balance.
3. Do not rename the shared or role-specific production CSS classes only to satisfy the test.

### Out of scope

- Sorting behavior and controls.
- Reward request API behavior.
- Refactoring other E2E locators.

### Acceptance criteria

- The isolated test reaches the reward assertion and verifies that the unavailable reward action is disabled.
- `PLAYWRIGHT_USE_PREVIEW=true` runs the focused test without selector failures.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-child.spec.ts --grep "child Mini App opens tasks first"
```

### Commit

```bash
git add apps/web/tests/e2e/telegram-child.spec.ts
git commit -m "test(web): repair child reward action assertion"
```

## P1-1: Preserve default group ordering in child catalog lists

**Status:** DONE
**Priority:** P1  
**Depends on:** -

**Exact scope:**

Make the default `group` mode in `TelegramChildTasks` and `TelegramChildRewards` use the complete visible group sequence, including groups that are new or have no saved order.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte` (`orderedGroups`, `items`).
- Modify `apps/web/src/lib/components/telegram/TelegramChildRewards.svelte` (`orderedGroups`, `items`).
- Modify `apps/web/tests/unit/catalogSort.test.ts`.
- Reference `apps/web/src/lib/telegram/services/groupOrder.ts` (`getEffectiveGroupOrder`, `orderGroups`) and `apps/web/src/lib/telegram/services/catalogSort.ts` (`sortCatalogItems`).

**Goal:**

A child sees all task and reward groups in the effective group order by default, with coins ascending only within each group; selecting `Монеты ↑` flattens the same list into global ascending coin order.

### Outcome

An empty or partial saved child order no longer makes unsaved groups bypass group grouping and appear as one globally coin-sorted remainder.

### Architectural decision

The backend-provided effective order is a preference, not a complete catalog. Each child component must combine it with its active visible groups through the existing `orderGroups` helper before passing it to the shared sorter.

### Required changes

1. Derive child `orderedGroups` from the visible named groups plus `getEffectiveGroupOrder`, matching the parent list behavior.
2. Keep hidden and inactive entries excluded before deriving the list and its group order.
3. Cover no saved order and a partial saved order, proving that every group remains contiguous and coins are ascending inside it.
4. Keep equal coin values stable in their incoming order.

### Out of scope

- Persisting the selected `group`/`coins` sort mode.
- Changing the parent-managed group order API or database schema.
- New filters or changes to group chips.

### Acceptance criteria

- With groups `Дом` and `Учёба` and no saved order, group mode never interleaves them by coin value.
- With a saved order that names only one group, the named group appears first and all remaining groups stay grouped afterward.
- In both parent and child uses of `sortCatalogItems`, `coins` mode orders the complete visible list from lower to higher values.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/catalogSort.test.ts tests/unit/groupOrder.test.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildTasks.svelte apps/web/src/lib/components/telegram/TelegramChildRewards.svelte apps/web/tests/unit/catalogSort.test.ts
git commit -m "fix(web): preserve child catalog group ordering"
```

## P2-1: Make the sort mode controls touch-accessible

**Status:** DONE
**Priority:** P2  
**Depends on:** P1-1

**Exact scope:**

Bring both interactive choices in `TelegramSortControl` to the Mini App 44 px touch-target contract without making the compact control overflow at 320 px.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramSortControl.svelte` (`button` styles).
- Create `apps/web/tests/e2e/telegram-sorting.spec.ts` with a focused touch-target and overflow assertion.

**Goal:**

Every sort choice is comfortably operable by touch and keyboard on narrow Telegram Mini App screens.

### Outcome

The control remains compact, each button measures at least 44 by 44 CSS pixels, and focus remains visibly indicated.

### Architectural decision

Keep a native two-button `role="group"` control with `aria-pressed`; do not replace it with an icon-only menu or hover-only affordance.

### Required changes

1. Increase the actionable button geometry to at least 44 px in both dimensions.
2. Preserve `aria-pressed`, accessible group labeling, and the local visible focus indicator.
3. Verify that the 320 px viewport has no horizontal document overflow.

### Out of scope

- New sorting modes.
- Global typography or token changes.
- Persisting the view preference.

### Acceptance criteria

- Both `Группы` and `Монеты ↑` buttons measure at least 44 px wide and high at a 320 px viewport.
- Keyboard focus is visible on either choice.
- The document has no horizontal overflow at 320 px, 390 px, and desktop width.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-sorting.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramSortControl.svelte apps/web/tests/e2e/telegram-sorting.spec.ts
git commit -m "fix(web): enlarge catalog sort touch targets"
```

## P2-2: Add cross-role sorting browser regression coverage

**Status:** DONE
**Priority:** P2  
**Depends on:** P0-1, P1-1, P2-1

**Exact scope:**

Add browser regression coverage for parent and child task/reward lists with at least two groups and non-monotonic coin values.

**Files:**

- Modify `apps/web/tests/e2e/telegram-sorting.spec.ts`.
- Reference `apps/web/src/lib/components/telegram/TelegramParentTasks.svelte`, `TelegramParentRewards.svelte`, `TelegramChildTasks.svelte`, and `TelegramChildRewards.svelte`.

**Goal:**

The real Mini App interaction proves both sort modes across both roles and both catalog types.

### Outcome

Future UI changes cannot silently regress default grouping, ascending coin sort, touch geometry, or accessible pressed state.

### Architectural decision

Use the existing Playwright Telegram fixture and API route mocks. Assert row title order and `aria-pressed`, not internal reactive variable names or implementation-specific DOM nesting.

### Required changes

1. Create fixtures with at least two groups and coin values that would interleave under an incorrect global sort.
2. For parent and child tasks and rewards, assert default group order plus ascending values within each group.
3. Select `Монеты ↑` and assert global ascending order and correct pressed state; return to `Группы` and assert restoration.
4. Cover Russian and English accessible labels where the suite’s fixtures use both locales.

### Out of scope

- Visual snapshot baselines.
- Server persistence for sorting mode.
- Modifying group-management behavior.

### Acceptance criteria

- The E2E test proves the two modes for parent and child task/reward screens.
- It runs at 320 px, 390 px, and a desktop viewport with no horizontal overflow.
- All assertions use user-observable row order and accessible state.

### Targeted validation

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-sorting.spec.ts tests/e2e/telegram-child.spec.ts tests/e2e/telegram-parent.spec.ts
```

### Commit

```bash
git add apps/web/tests/e2e/telegram-sorting.spec.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "test(web): cover Telegram catalog sorting"
```
