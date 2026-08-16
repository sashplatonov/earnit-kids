# Child Submitted Requests (Mini App) — Implementation Backlog

## Goal

Add a dedicated "Заявки" (Requests) section to the child's Telegram Mini App workspace where the child sees every task-completion and reward-purchase request they have submitted, together with its current status (pending / approved / rejected), and can cancel a still-pending request themselves with a single, clearly-labelled action. The section must match the existing Mini App visual language (Telegram components, semantic icons, bottom tab bar) and be fully usable on a 320px-wide phone.

## Architectural decisions

- **Source of truth:** the backend `PurchaseRequestEntity` rows, already exposed to the child through the `/api/data` snapshot. `FamilyDashboardHydrator.loadRequests` already filters requests to the active child when the session is not admin (`adminSession || Objects.equals(request.getChildId(), activeChildId)`), so `$appStore.requests` already contains only the child's own requests in a child session. No new read endpoint is required.
- **Cancellation reuses the existing endpoint:** `DELETE /api/requests/{requestId}` in `FamilyResource` already permits child sessions (`auth.isAdmin() || auth.isChild()`) and resolves `effectiveChildId` to `auth.childId()` for children. `FamilyActionRequestService.deleteRequest` already blocks a child from deleting an `approved` request (`isChildDeletingOwnRequest && status == approved` → `requests.alreadyProcessed`). **No backend change is required** — the frontend must reuse `deleteRequest` from `apps/web/src/lib/services/api.ts`.
- **Layer boundaries:** UI (`TelegramChildRequestList.svelte`) → `deleteRequest` API helper → existing `DELETE /api/requests/{id}` → `FamilyActionRequestService` → `PurchaseRequestRepository`. No new DTO, resolver, or aggregation logic.
- **State:** after a successful cancel, refresh the shared store via `refreshData()` (or `applyDataSnapshot` if the endpoint returned a snapshot) rather than mutating local state only, so the pending badge in `TelegramChildTasks`/`TelegramChildRewards` (which derive `pendingIds` from `$appStore.requests`) stays consistent.
- **Rejected duplicate approaches:** do NOT add a new child-specific list endpoint, do NOT add a new "cancel" verb endpoint, do NOT duplicate the request card rendering logic that already exists in `TelegramRequestList.svelte` (that component is parent-oriented with approve/reject and must not be reused for the child). Do NOT reuse the legacy `RequestsSection.svelte` (web app, not Mini App).
- **Copy separation:** all user-facing strings live in the `app.telegram.*` i18n domain (`en/app.ts` + `ru/app.ts`), never hardcoded in the component.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 | P1 | - | i18n keys are the shared contract the tab and list both consume |
| 2 | P1-2 | P1 | P1-1 | The tab must exist before the list can be reached |
| 3 | P1-3 | P1 | P1-2 | The list renders the child's requests and the cancel control |
| 4 | P1-4 | P1 | P1-3 | Wire cancel to the existing endpoint and add regression coverage |

## P1-1: Add child requests i18n keys (en + ru)

**Status:** ⬜ Not started
**Priority:** P1
**Depends on:** -

### Outcome

The `app.telegram` message catalog gains a `childRequests` block (and a `requests` tab label under `childShell`) in both English and Russian, with key parity between the two locales.

### Architectural decision

Copy is the shared contract. Keys are added to the existing `telegram` object in `apps/web/src/lib/i18n/messages/en/app.ts` and `ru/app.ts`; the `MessageKey` union is derived from the catalog, so both files must define the exact same keys.

### Files

- Modify `apps/web/src/lib/i18n/messages/en/app.ts`.
- Modify `apps/web/src/lib/i18n/messages/ru/app.ts`.

### Work

1. Add a `requests` tab label to the existing `childShell` block (e.g. `requests: 'Requests'` / `'Заявки'`).
2. Add a new `childRequests` block under `telegram` with keys for: section title, empty state, loading, error, retry, status labels (`pending`/`approved`/`rejected`), a `cancel` action label, a `cancelAria` label, a `cancelConfirm`/`cancelConfirmTitle`/`cancelConfirmDescription`/`cancelConfirmAction`/`cancelConfirmCancel` set for the confirmation, and `cancelSuccess`/`cancelError` toasts.
3. Keep the same key set in both locales; do not add keys to only one file.

### Acceptance criteria

- `en/app.ts` and `ru/app.ts` define identical `childRequests` keys and the `childShell.requests` key.
- `npm run lint` and `npm run test` pass (the existing `i18nCatalog.test.ts` en↔ru key-parity check stays green).

### Verification

```bash
cd apps/web && npm run lint && npm run test
```

### Commit

```bash
git add apps/web/src/lib/i18n/messages/en/app.ts apps/web/src/lib/i18n/messages/ru/app.ts
git commit -m "feat(web): add child requests i18n keys for Mini App"
```

## P1-2: Add a "Requests" tab to the child Mini App shell

**Status:** ⬜ Not started
**Priority:** P1
**Depends on:** P1-1

### Outcome

The child workspace (`TelegramChildShell.svelte`) shows a fourth bottom tab labelled "Заявки" that opens a requests panel, alongside the existing Tasks / Rewards / Activity tabs.

### Architectural decision

Extend the existing tab mechanism in `TelegramChildShell.svelte` (the `tabs` const, `view` union, `tabForContext`, `selectView`, `handleTabKeydown`, and the `role="tablist"` markup) rather than introducing a separate navigation surface. The tab grid must move from `repeat(3, …)` to `repeat(4, …)` in both the desktop and the `@media (max-width:700px)` fixed bottom-bar rules.

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramChildShell.svelte`.

### Work

1. Extend the `view` type and `tabs` const to include `'requests'`.
2. Add a `requests` case to `tabForContext` (accept `'requests'` as a deep-link context).
3. Add the fourth tab button with a semantic icon (reuse an existing `TelegramIconName` such as `request`/`tag` or `clipboardList`) and the `childShell.requests` label, wired to `selectView('requests')`.
4. Render the new panel in the `{#if view === …}` chain (initially a placeholder that will be replaced by the list component in P1-3).
5. Update both `grid-template-columns` rules from `repeat(3, …)` to `repeat(4, …)`.

### Acceptance criteria

- Four tabs are visible and keyboard-navigable (arrow keys / Home / End) in the child workspace.
- At 320px viewport width the four tabs fit the fixed bottom bar without horizontal overflow and each tab's touch target is at least 44px tall.
- Selecting the Requests tab focuses the requests panel and updates `aria-selected`/`tabindex` correctly.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildShell.svelte
git commit -m "feat(web): add requests tab to child Mini App shell"
```

## P1-3: Create the child request list component with cancel control

**Status:** ⬜ Not started
**Priority:** P1
**Depends on:** P1-2

### Outcome

A new `TelegramChildRequestList.svelte` renders the child's own submitted requests (task and reward) with a status chip and, for pending requests, a visible "Отменить" (cancel) control.

### Architectural decision

A new, child-specific component (not a reuse of the parent-oriented `TelegramRequestList.svelte`). It reads `$appStore.requests` (already child-scoped) and derives per-request display data using the existing `getTelegramEntityIcon` / `stripLeadingEmoji` helpers and `TelegramCoin`, matching the `TelegramHistoryList`/`TelegramRequestList` visual style. Status labels come from the `childRequests` i18n keys. The cancel control is rendered only when `status === 'pending'`.

### Files

- Create `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramChildShell.svelte` (replace the P1-2 placeholder with this component).

### Work

1. Build the list: for each request show the entity icon, title (`taskName`/`itemName`/`title`), a `+coins` amount via `TelegramCoin`, the request kind (task vs reward), and a status chip.
2. Distinguish task vs reward using `requestType === 'shop_purchase'` (same convention as `TelegramRequestList.requestKind`).
3. Render a cancel button only for `status === 'pending'`; use the `delete`/`trash` icon and the `childRequests.cancelAria` label.
4. Handle loading / empty / error states with the same `state-empty` / `state-error` / `muted` patterns used by `TelegramHistoryList`.
5. Emit a `cancel` event (or accept an `onCancel` prop) so the shell owns the actual API call (see P1-4); keep the component presentational.
6. Ensure the list uses `grid-template-columns:minmax(0,1fr)` (the known Mini App grid-blowout fix) and that cancel buttons are at least 44×44px.

### Acceptance criteria

- The child sees their own submitted task and reward requests with correct status labels.
- Pending requests show a cancel control; approved/rejected requests do not.
- At 320px width the list does not overflow horizontally and the cancel control is reachable without scrolling.
- Empty state renders when there are no requests.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/src/lib/components/telegram/TelegramChildShell.svelte
git commit -m "feat(web): add child request list with cancel control"
```

## P1-4: Wire cancel to the existing delete endpoint and add regression coverage

**Status:** ⬜ Not started
**Priority:** P1
**Depends on:** P1-3

### Outcome

Tapping "Отменить" on a pending request shows a confirmation, calls the existing `deleteRequest` helper, refreshes the shared store, and surfaces success/error feedback. The pending badge in Tasks/Rewards updates accordingly.

### Architectural decision

Reuse `deleteRequest(requestId, childId)` from `apps/web/src/lib/services/api.ts` (which already targets `DELETE /api/requests/{id}` and flushes pending saves). After a successful delete, call `refreshData()` so `$appStore.requests` is authoritative and the `pendingIds` derivations in `TelegramChildTasks`/`TelegramChildRewards` re-evaluate. Use the existing `TelegramConfirmModal` for the confirmation step. No backend change.

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramChildShell.svelte` (own the cancel handler + confirmation + refresh).
- Modify `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte` (emit the cancel event / accept `onCancel`).
- Modify `apps/web/tests/e2e/telegram-child.spec.ts` (add a cancel-flow test).
- Modify `apps/web/tests/unit/api.test.ts` (assert the child cancel path calls `DELETE /api/requests/{id}` with the child query, if not already covered).

### Work

1. In the shell, implement `handleCancel(request)`: open `TelegramConfirmModal` with the `childRequests.cancelConfirm*` copy; on confirm call `deleteRequest(request.id, $appStore.currentChildId)`.
2. On success, `await refreshData()` and show the `childRequests.cancelSuccess` toast; on failure show `childRequests.cancelError`.
3. Guard against cancelling a non-pending request (the button is only rendered for pending, but keep the handler defensive).
4. Add an E2E test that mocks `/api/data` with a pending request, opens the Requests tab, cancels it, and asserts the request disappears and the pending badge clears.
5. Add/confirm a unit test for the `deleteRequest` helper path.

### Acceptance criteria

- Cancelling a pending request removes it from the list after refresh and clears the corresponding pending badge in Tasks/Rewards.
- Cancelling an already-approved request is not offered (no cancel control) and, if attempted, surfaces an error rather than silently succeeding.
- A failed cancel shows an error toast and leaves the request in place.
- The confirmation dialog is keyboard-accessible and dismissible.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildShell.svelte apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/unit/api.test.ts
git commit -m "feat(web): wire child request cancellation to delete endpoint"
```
