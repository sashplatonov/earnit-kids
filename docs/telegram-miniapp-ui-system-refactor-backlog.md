# Telegram Mini App UI System Refactor - Implementation Backlog

## Goal

Refactor the Telegram Mini App so equivalent task, reward, request, activity,
navigation, feedback, and bottom-sheet UI is composed from a small set of
semantic components. Preserve all current parent and child workflows, API
calls, role permissions, translations, deep-link contexts, and mobile geometry
while removing repeated markup, CSS values, and presentation decisions.

## Architectural decisions

- **Server state:** `appStore`, populated and reconciled by the existing
  `initializeFromServer`, `refreshData`, and `applyDataSnapshot` services,
  remains the only owner of family data. This work must not add a parallel
  query cache or copy server entities into component-local writable state.
- **View state:** a shell owns its selected tab, sheet open state, and an
  in-progress action; reusable components receive explicit data, visual
  variants, callbacks, and slots. Derived labels, icons, status tones, and row
  metadata belong to typed Telegram presentation mappers, not `.svelte`
  markup.
- **Shared presentation system:** create focused components under
  `apps/web/src/lib/components/telegram/ui/` for concepts that already have
  the same product meaning: list surface, entity row, async feedback,
  tab navigation, and bottom sheet. Do not turn unrelated forms or feature
  workflows into a generic configuration framework.
- **Styling contract:** the shared system owns semantic visual tokens and the
  established Mini App list invariant: one white rounded bordered outer
  surface; transparent, square inner rows divided only between neighbours.
  Touched interactive controls retain at least 44 by 44 px hit targets and a
  scoped `:focus-visible` ring.
- **Compatibility:** retain the existing `/telegram?context=...` meanings,
  `TelegramIcon`, existing i18n keys, event callback signatures, and backend
  contracts. Unknown request statuses must render a neutral safe fallback.
- **Explicitly rejected:** a new UI library, a global UI store, scattered CSS
  variable literals per feature, and duplicate legacy/new rendering paths.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 | P1 | - | Protect existing role-specific and mobile behaviour before structural moves. |
| 2 | P1-2 | P1 | P1-1 | Give request presentation one typed owner before migrating its screens. |
| 3 | P1-3 | P1 | P1-1 | Establish reusable list, entity-row, and async-state contracts. |
| 4 | P2-1 | P2 | P1-2, P1-3 | Move request and activity UI to the common contracts and delete duplicate presentation rules. |
| 5 | P2-2 | P2 | P1-3 | Move task/reward rows to one semantic entity-row family without merging their workflows. |
| 6 | P2-3 | P2 | P1-3 | Consolidate parent/child tab bars and repeated workspace feedback. |
| 7 | P2-4 | P2 | P1-3 | Give all existing Mini App sheets one accessible structural owner. |
| 8 | P3-1 | P3 | P2-1, P2-2, P2-3, P2-4 | Remove retired CSS/branches and prove the migration end to end. |

## TASK-P1-1: Characterize the current cross-role Mini App contracts

**Status:** DONE
**Priority:** P1  
**Depends on:** -

**Files:**

- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-consistency.spec.ts`.
- Create `apps/web/tests/unit/telegramRequestPresentation.test.ts`.

### Outcome

The current parent and child journeys have regression protection for the UI
contracts that the refactor will preserve, rather than relying on visual
similarity after components are moved.

### Architectural decision

Browser tests remain the owner of role, tab, deep-link, keyboard, and narrow
viewport behaviour; a unit test owns pure request presentation rules. Extend
the existing Telegram Playwright suite and Vitest conventions rather than
adding another test runner or snapshot-only test layer.

### Work

1. Add focused assertions for the selected tab from each supported
   `context` value, tab keyboard navigation, and the parent pending-count
   label without asserting incidental DOM structure.
2. Characterize the one-surface list contract for requests, child tasks, and
   child rewards: no nested card surfaces, neighbouring rows separated by a
   divider, and action targets at least 44 px where an action is offered.
3. Assert error, empty, loading, retry, and disabled-action states that are
   currently reachable in the existing fixtures.
4. Add pure fixture-based coverage for pending-first request ordering,
   task/reward metadata, known status tones, and the neutral fallback for an
   unknown backend status.

### Acceptance criteria

- [ ] Parent and child critical routes still open their existing default and
  deep-linked tabs.
- [ ] Keyboard Tab establishes visible focus and arrow/Home/End navigation
  changes the appropriate tab while preserving its ARIA selected state.
- [ ] Parent, child, and 320 px/compact-mobile checks protect the established
  outer-surface/inner-row geometry without horizontal overflow.
- [ ] Known and unknown request statuses have deterministic, localized or
  safe-neutral presentation coverage.
- [ ] Tests use existing bilingual locators and Telegram SDK fixtures; no
  production API or Telegram client is required.

### Verification

```bash
cd apps/web && npm run test -- telegramRequestPresentation
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-parent.spec.ts telegram-child.spec.ts telegram-layout.spec.ts telegram-consistency.spec.ts
```

### Commit

```bash
git add apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-layout.spec.ts apps/web/tests/e2e/telegram-consistency.spec.ts apps/web/tests/unit/telegramRequestPresentation.test.ts
git commit -m "test(web): Characterize Telegram UI contracts"
```

## TASK-P1-2: Centralize request presentation semantics

**Status:** TODO
**Priority:** P1  
**Depends on:** P1-1

**Files:**

- Create `apps/web/src/lib/components/telegram/telegramRequestPresentation.ts`.
- Modify `apps/web/src/lib/components/telegram/telegramRequestKind.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte`.
- Modify `apps/web/tests/unit/telegramRequestPresentation.test.ts`.

### Outcome

Every request row displays the same title, entity kind/icon input, amount
direction, group/type metadata, ordering, and finite status tone regardless of
whether it appears in the parent home or child activity view.

### Architectural decision

`telegramRequestPresentation.ts` owns pure conversion from the stable
`Request` store type to a typed presentation model; translation remains at the
component boundary through an explicit translator input. `TelegramRequestRow`
renders that model. Retire the parent list's local `requestKind` and the child
list's local status, label, metadata, and sort functions.

### Work

1. Define finite request status tone and request-row view-model types,
   including an unknown-status fallback.
2. Move shared request identity, title, amount-sign, metadata, status, and
   pending-first/newest-first sorting into pure exported functions.
3. Keep role-specific actions as slots or explicit callbacks: parent decision
   controls and child cancellation must not become one shared mutation path.
4. Update both list consumers and `TelegramRequestRow` to accept the stable
   presentation model rather than recomputing raw request conditions in
   markup.

### Acceptance criteria

- [ ] A request has one display mapping for both parent and child screens.
- [ ] Pending requests sort first and each group remains descending by
  `createdAt`, including missing-date handling.
- [ ] Task requests show positive and reward requests negative coin direction
  exactly as before.
- [ ] Approved, rejected, cancelled, pending, and unknown statuses render
  deterministically; unknown input cannot crash a row.
- [ ] Parent approve/reject and child cancel permissions, callbacks, busy
  state, and API calls remain unchanged.

### Verification

```bash
cd apps/web && npm run test -- telegramRequestPresentation
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/telegramRequestPresentation.ts apps/web/src/lib/components/telegram/telegramRequestKind.ts apps/web/src/lib/components/telegram/TelegramRequestRow.svelte apps/web/src/lib/components/telegram/TelegramRequestList.svelte apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/tests/unit/telegramRequestPresentation.test.ts
git commit -m "refactor(web): Centralize request presentation"
```

## TASK-P1-3: Establish shared list rows and async feedback primitives

**Status:** TODO
**Priority:** P1  
**Depends on:** P1-1

**Files:**

- Create `apps/web/src/lib/components/telegram/ui/TelegramListSurface.svelte`.
- Create `apps/web/src/lib/components/telegram/ui/TelegramEntityRow.svelte`.
- Create `apps/web/src/lib/components/telegram/ui/TelegramAsyncState.svelte`.
- Create `apps/web/src/lib/components/telegram/ui/telegramUi.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramHistoryList.svelte`.
- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.

### Outcome

Shared Mini App entities use one accessible outer list surface, one standard
row geometry, and one explicit loading/empty/error/retry presentation instead
of reimplementing equivalent borders, colours, icon placement, focus rings,
and status markup per screen.

### Architectural decision

The new `ui/` components own only reusable presentation and locally scoped
accessibility behaviour. Feature components retain server calls, mutations,
translation copy, filtering, pagination, and row-action decisions. The
semantic token module owns only repeated values already shared by these
primitives; it is not a replacement global styling system.

### Work

1. Implement a slotted list surface that owns the one outer border/radius/
   white background and adjacent-row divider contract.
2. Implement a composable entity row with icon, title, metadata, optional
   trailing content, action slot, truncation, and visible local keyboard focus
   support; do not give it business-specific booleans.
3. Model loading, empty, and error/retry as a finite async presentation
   component so an error is never silently displayed as an empty result.
4. Initially migrate the request and history renderers to validate the public
   primitive API before task/reward list movement.

### Acceptance criteria

- [ ] The migrated lists have exactly one rounded bordered outer surface;
  inner rows are transparent, square, and divider-separated.
- [ ] Entity text remains safely truncated/wrapped for long localized names;
  rows do not create horizontal overflow at 320 px.
- [ ] Retry remains a native button with its existing accessible label and a
  44 px minimum target; interactive rows and actions expose the local
  `:focus-visible` ring.
- [ ] Loading, success-with-data, success-without-data, and error are
  distinguishable rendered states.
- [ ] No new dependency, global store, or duplicate surface CSS is introduced.

### Verification

```bash
cd apps/web && npm run lint
cd apps/web && npm run test
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-layout.spec.ts telegram-child.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/ui/TelegramListSurface.svelte apps/web/src/lib/components/telegram/ui/TelegramEntityRow.svelte apps/web/src/lib/components/telegram/ui/TelegramAsyncState.svelte apps/web/src/lib/components/telegram/ui/telegramUi.ts apps/web/src/lib/components/telegram/TelegramRequestRow.svelte apps/web/src/lib/components/telegram/TelegramRequestList.svelte apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/src/lib/components/telegram/TelegramHistoryList.svelte apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "refactor(web): Add Telegram list primitives"
```

## TASK-P2-1: Complete the request and activity migration to shared UI

**Status:** TODO
**Priority:** P2  
**Depends on:** P1-2, P1-3

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramParentHome.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramHistoryList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.

### Outcome

Parent home, parent requests, child requests, and child activity use the same
request/activity rows and feedback surfaces where they represent the same
entity, while retaining each screen's intentionally different actions and
pagination.

### Architectural decision

The shared request presentation model and `ui/` primitives are the sole owners
of common rendering. `TelegramParentHome` remains the owner of its limited
pending-request preview and activity fetch; `TelegramChildShell` remains the
owner of child history pagination and request cancellation.

### Work

1. Replace remaining inline parent-home activity row markup with the entity
   row contract or a small activity adapter that feeds it.
2. Remove duplicate request/activity state, surface, row, status, amount, and
   focus CSS superseded by the shared components.
3. Preserve `showAll`, child request page size/load-more, and child history
   load-more behaviour at their existing owners.
4. Extend role-specific E2E assertions only where the shared rendering changed
   a previously unprotected user-visible contract.

### Acceptance criteria

- [ ] Parent home still limits and expands its pending-request preview exactly
  as today.
- [ ] Parent decision controls remain below the request primary content; child
  cancel controls remain available only for pending child requests.
- [ ] Activity dates continue to use `formatLastUsedTime` and signed amounts
  retain their task/reward semantics.
- [ ] Empty/error/retry and pagination messages remain role-appropriate and
  are not replaced by generic raw error text.
- [ ] Retired duplicate presentation helpers and CSS have no remaining import
  or selector references.

### Verification

```bash
cd apps/web && npm run lint
cd apps/web && npm run test
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-parent.spec.ts telegram-child.spec.ts telegram-activity.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramParentHome.svelte apps/web/src/lib/components/telegram/TelegramRequestList.svelte apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/src/lib/components/telegram/TelegramHistoryList.svelte apps/web/src/lib/components/telegram/TelegramRequestRow.svelte apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/e2e/telegram-child.spec.ts
git commit -m "refactor(web): Unify request and activity UI"
```

## TASK-P2-2: Compose task and reward lists from one entity-row family

**Status:** TODO
**Priority:** P2  
**Depends on:** P1-3

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramParentTasks.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentRewards.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildRewards.svelte`.
- Modify `apps/web/src/lib/components/telegram/ui/TelegramEntityRow.svelte`.
- Modify `apps/web/src/lib/components/telegram/ui/TelegramListSurface.svelte`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.

### Outcome

Task and reward items have one visual and accessibility grammar for their
shared entity information while parent management and child request workflows
remain independent feature orchestration.

### Architectural decision

`TelegramEntityRow` owns identical entity chrome (semantic icon, title,
coin/group/last-used metadata, archived state, trailing action space). Each
task/reward component remains the source of truth for filtering, group order,
forms, confirmation, affordability, limits, mutations, menus, and i18n copy.
Do not collapse parent and child feature components into one component.

### Work

1. Add narrow slots/variants only for already-common row differences: coin
   sign, optional last-used line, disabled/archived state, and trailing action
   controls.
2. Migrate the four task/reward lists one at a time to shared surface and row
   composition, retaining their current group navigation and goal/progress
   blocks outside the list primitive.
3. Normalize action hit target dimensions to the existing 44 px contract,
   including child reward request controls.
4. Delete repeated row/list/icon/title/meta CSS once each migration is covered.

### Acceptance criteria

- [ ] Parent task complete, parent reward grant, edit/menu/archive/delete,
  child task request, and child reward request behaviour are unchanged.
- [ ] Permissions continue to hide or disable management actions exactly as
  before; client visibility remains UX-only, not authorization.
- [ ] Group filters, archived appearance, pending/limit/affordability disabled
  states, last-used copy, and the child reward goal panel remain intact.
- [ ] Every migrated task/reward list meets the established one-surface and
  44 px keyboard/touch contract at compact mobile width.
- [ ] Each displayed task or reward type has one shared row styling owner.

### Verification

```bash
cd apps/web && npm run lint
cd apps/web && npm run test
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-parent.spec.ts telegram-child.spec.ts telegram-layout.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramParentTasks.svelte apps/web/src/lib/components/telegram/TelegramParentRewards.svelte apps/web/src/lib/components/telegram/TelegramChildTasks.svelte apps/web/src/lib/components/telegram/TelegramChildRewards.svelte apps/web/src/lib/components/telegram/ui/TelegramEntityRow.svelte apps/web/src/lib/components/telegram/ui/TelegramListSurface.svelte apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "refactor(web): Reuse Telegram entity rows"
```

## TASK-P2-3: Consolidate Mini App workspace navigation and feedback

**Status:** TODO
**Priority:** P2  
**Depends on:** P1-3

**Files:**

- Create `apps/web/src/lib/components/telegram/ui/TelegramTabBar.svelte`.
- Create `apps/web/src/lib/components/telegram/telegramWorkspaceContext.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentShell.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildShell.svelte`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.

### Outcome

Parent and child shells use one accessible responsive tab-bar implementation
and one URL-context parser while continuing to render their distinct tabs,
headers, history loading, and parent-preview behaviour.

### Architectural decision

`TelegramTabBar` owns roving tabindex, ArrowLeft/ArrowRight/Home/End keyboard
behaviour, mobile bottom-bar geometry, ARIA tab relationships, and focus
styles. `telegramWorkspaceContext.ts` owns parsing the existing `context`
parameter. Each role shell owns its allowed tab set, selected view, bootstrap,
role content, error copy, and child activity subview.

### Work

1. Replace duplicated `tabForContext`, browser URL parsing, tab markup, and
   keyboard handlers with typed context and tab configuration.
2. Reuse the async feedback primitive for shell loading/error/retry where the
   existing visual contract permits; retain child skeleton loading if it is a
   deliberate distinct loading treatment.
3. Centralize the duplicate public-site footer only if its semantics and copy
   remain identical after migration; otherwise leave it local and document why.
4. Preserve visibility refresh, history pagination, parent preview exit,
   bottom safe-area padding, and all tab identifiers needed by existing E2E.

### Acceptance criteria

- [ ] Every currently supported parent/child deep-link context resolves to the
  same tab and child activity subview as before.
- [ ] Tab labels, icons, count badge, selected state, focus movement, and
  `aria-controls`/`aria-labelledby` stay correct for both role configurations.
- [ ] At <=700 px the shared bar remains fixed above safe areas without
  covering panel content; at 320 px it has no horizontal overflow.
- [ ] Parent bootstrap failure and child refresh failure remain distinguishable
  and retry their existing `initializeFromServer`/`refreshData` paths.
- [ ] The role shells contain no copied keyboard-navigation or context-parser
  implementation after migration.

### Verification

```bash
cd apps/web && npm run lint
cd apps/web && npm run test
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-parent.spec.ts telegram-child.spec.ts telegram-layout.spec.ts telegram-consistency.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/ui/TelegramTabBar.svelte apps/web/src/lib/components/telegram/telegramWorkspaceContext.ts apps/web/src/lib/components/telegram/TelegramParentShell.svelte apps/web/src/lib/components/telegram/TelegramChildShell.svelte apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "refactor(web): Unify Telegram workspace tabs"
```

## TASK-P2-4: Introduce an accessible shared bottom-sheet shell

**Status:** TODO
**Priority:** P2  
**Depends on:** P1-3

**Files:**

- Create `apps/web/src/lib/components/telegram/ui/TelegramBottomSheet.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramTaskForm.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRewardForm.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestSheet.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramCoinAdjust.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramCatalogFilters.svelte`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.

### Outcome

The most frequently used Mini App forms and request flows render inside one
focus-safe, escape-close, safe-area-aware bottom-sheet shell rather than each
recreating backdrop, dialog, sizing, and close mechanics.

### Architectural decision

`TelegramBottomSheet` owns modal semantics, initial focus/return focus,
Escape/backdrop close rules, scroll containment, and safe-area layout. Each
feature owns open state, fields, validation, submit/cancel meaning, busy state,
and localized copy. Begin with the listed high-frequency sheets; do not force
all sheets into the component until their current close contract is covered.

### Work

1. Build a slotted bottom-sheet shell using native dialog semantics where
   compatible with the current Svelte/browser support, otherwise a fully
   explicit `role="dialog"` contract with focus management.
2. Migrate the listed sheets and preserve every current submit and close
   callback signature.
3. Ensure backdrop click is disabled or remains enabled exactly as each
   existing busy/confirmation workflow requires; never close a destructive
   confirmation accidentally.
4. Add keyboard assertions for focus entry, Escape close, focus restoration,
   and mobile safe-area visibility.

### Acceptance criteria

- [ ] Opening a migrated sheet moves focus into the dialog; Escape and allowed
  backdrop close return focus to its trigger.
- [ ] Sheet content is scrollable above mobile safe areas and its controls stay
  reachable with the software keyboard/narrow viewport.
- [ ] Task/reward create-edit, task/reward request, coin adjustment, and
  catalog filters retain existing validation, disabled/busy, submit, and cancel
  behaviour.
- [ ] No click-only close control or duplicate backdrop/sheet/focus CSS remains
  in migrated files.
- [ ] Existing `TelegramConfirmModal` stays untouched unless a later targeted
  task establishes the same lifecycle contract.

### Verification

```bash
cd apps/web && npm run lint
cd apps/web && npm run test
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- telegram-parent.spec.ts telegram-child.spec.ts telegram-layout.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/ui/TelegramBottomSheet.svelte apps/web/src/lib/components/telegram/TelegramTaskForm.svelte apps/web/src/lib/components/telegram/TelegramRewardForm.svelte apps/web/src/lib/components/telegram/TelegramRequestSheet.svelte apps/web/src/lib/components/telegram/TelegramCoinAdjust.svelte apps/web/src/lib/components/telegram/TelegramCatalogFilters.svelte apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/e2e/telegram-child.spec.ts
git commit -m "refactor(web): Share Telegram bottom sheets"
```

## TASK-P3-1: Retire duplicate Mini App presentation paths and run final gates

**Status:** TODO
**Priority:** P3  
**Depends on:** P2-1, P2-2, P2-3, P2-4

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramParentHome.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentShell.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildShell.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentTasks.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentRewards.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildRewards.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramHistoryList.svelte`.
- Modify `apps/web/tests/e2e/telegram-activity.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-consistency.spec.ts`.

### Outcome

Only the shared UI system remains for migrated concepts, with no obsolete
helpers, duplicated CSS, or old/new branches left to drift; the Telegram Mini
App passes its complete local web quality gates.

### Architectural decision

Deletion follows migration evidence: all consumers first use the typed
presentation and `ui/` components, then unused local selectors/helpers are
removed in this final task. No compatibility path is needed because the changes
are internal presentation refactors and preserve public component behaviour.

### Work

1. Search for and remove obsolete duplicated list, row, state, tab, sheet,
   request-status, and context-parser implementations only after confirming
   their replacement owns all call sites.
2. Re-check that no new component has taken ownership of server state,
   authorization, or feature mutations.
3. Add or update only the remaining E2E coverage necessary to demonstrate
   cross-role list interaction, activity pagination, bottom sheets, and compact
   geometry after the complete migration.
4. Run the full web lint, unit, build, and Telegram E2E sequence; record local
   results separately from unperformed real Telegram-client or remote-CI proof.

### Acceptance criteria

- [ ] Searches find no duplicate implementation of migrated request status
  mapping, tab keyboard navigation, shared list/row geometry, or sheet shell.
- [ ] No dead imports, commented-out alternatives, broad `any`, TypeScript or
  ESLint suppressions, or new dependencies were introduced.
- [ ] Parent and child journeys, including stale-state reconciliation and
  child activity/request switching, still pass their existing E2E coverage.
- [ ] The production build succeeds and the Mini App remains responsive and
  keyboard accessible at compact mobile widths.
- [ ] Local verification is not presented as remote CI, deployed Mini App, or
  physical Telegram-client validation.

### Verification

```bash
cd apps/web && npm run lint
cd apps/web && npm run test
cd apps/web && npm run build
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e
git diff --check
```

### Commit

```bash
git add apps/web/src/lib/components/telegram apps/web/tests/e2e/telegram-activity.spec.ts apps/web/tests/e2e/telegram-consistency.spec.ts
git commit -m "refactor(web): Complete Telegram UI system migration"
```

## Execution notes

- Execute strictly one task at a time, update its `Status` only after the
  listed acceptance criteria and commit succeed, then stop for handoff.
- Run Mini App E2E from `apps/web`. If the standard local server port is not
  available, retain `PLAYWRIGHT_USE_PREVIEW=true`; this is local browser
  evidence only.
- Do not run backend or Docker gates for this frontend-only refactor unless a
  later task changes their files or contracts.
