**# Child Submitted Requests (Mini App) — Implementation Backlog**

**## Goal**

Add a dedicated "Заявки" (Requests) status subsection inside the existing child "Активность" (Activity) area where the child sees every task-completion and reward-purchase request they have submitted, together with its current status (pending / approved / rejected / cancelled), and can cancel a still-pending request themselves with a single, clearly-labelled action. The section must match the existing Mini App visual language (Telegram components, semantic icons, bottom tab bar) and be fully usable on a 320px-wide phone.

**## Architectural decisions**

\- **\*\*Source of truth:\*\*** the backend \`PurchaseRequestEntity\` rows, already exposed to the child through the \`/api/data\` snapshot. \`FamilyDashboardHydrator.loadRequests\` already filters requests to the active child when the session is not admin (\`adminSession || Objects.equals(request.getChildId(), activeChildId)\`), so \`$appStore.requests\` already contains only the child's own requests in a child session. No new read endpoint is required.
\- **\*\*Cancellation keeps the existing endpoint but becomes a soft cancel:\*\*** keep using \`DELETE /api/requests/{requestId}\` so no new frontend API surface is introduced, but change the backend behavior for a child's own \`pending\` request from physical deletion to \`status = cancelled\`. Approved/rejected/cancelled requests remain immutable for the child. The frontend continues to reuse \`deleteRequest\` from \`apps/web/src/lib/services/api.ts\`. This preserves the product requirement that the child can still see every submitted request in history.
\- **\*\*Layer boundaries:\*\*** UI (\`TelegramChildRequestList.svelte\`) → \`deleteRequest\` API helper → existing \`DELETE /api/requests/{id}\` → \`FamilyActionRequestService\` → \`PurchaseRequestRepository\`. Keep the endpoint and DTO surface unchanged; only the backend mutation semantics change from delete to soft-cancel.
\- **\*\*State:\*\*** track a per-request \`cancelling\` state so the cancel action cannot be submitted twice. After success, update the shared request state from the authoritative response/snapshot when available; otherwise call \`refreshData()\` as reconciliation. Do not keep a child-list-only local mutation, because the pending badge in \`TelegramChildTasks\`/\`TelegramChildRewards\` derives from \`$appStore.requests\`.
\- **\*\*Rejected duplicate approaches:\*\*** do NOT add a new child-specific list endpoint or a new "cancel" verb endpoint. Do NOT reuse the parent-oriented \`TelegramRequestList.svelte\` as-is, but also do NOT duplicate its entity/title/icon/status rendering. Extract/reuse a small presentational request-row primitive (for example \`TelegramRequestRow.svelte\`) and let parent/child list components supply different actions. Do NOT reuse the legacy \`RequestsSection.svelte\` (web app, not Mini App).
\- **\*\*Copy separation:\*\*** all user-facing strings live in the \`app.telegram.\*\` i18n domain (\`en/app.ts\` + \`ru/app.ts\`), never hardcoded in the component.
\- **\*\*Request kind mapping:\*\*** do not branch on \`requestType === "shop_purchase"\` directly inside child UI markup. Reuse/extract a shared \`requestKind(request)\` mapper so parent and child request views classify task vs reward consistently.
\- **\*\*Ordering and identity:\*\*** show \`pending\` requests first, then resolved requests; within each group sort by \`createdAt DESC\`. Render the request date/time so repeated requests for the same task/reward remain distinguishable.
\- **\*\*Information architecture:\*\*** Requests is **not** a separate bottom tab. It lives as a status subsection inside the existing child Activity view, because both are history/status surfaces. The Activity screen gets a compact internal switch/segmented control such as \`История | Заявки\`; pending requests remain actionable inside the Requests subsection.
\- **\*\*Snapshot growth guard:\*\*** before shipping, verify the size of request history included in \`/api/data\`. If unbounded historical requests materially grow the snapshot, document a follow-up for retention/recent-window pagination; do not add a new history endpoint pre-emptively.

**## Recommended implementation order**

\| Order | Task | Priority | Depends on | Reason |
\| ---: | --- | --- | --- | --- |
\| 1 | P1-1 | P1 | - | i18n keys are the shared contract the tab and list both consume |
\| 2 | P1-2 | P1 | P1-1 | The Activity subsection switch must exist before the request list can be reached |
\| 3 | P1-3 | P1 | P1-2 | The list renders the child's requests and the cancel control |
\| 4 | P1-4 | P1 | P1-3 | Wire cancel to the existing endpoint and add regression coverage |

**## P1-1: Add child requests i18n keys (en + ru)**

**\*\*Status:\*\*** ✅ Completed
**\*\*Priority:\*\*** P1
**\*\*Depends on:\*\*** -

**### Outcome**

The \`app.telegram\` message catalog gains a \`childRequests\` block (and a \`requests\` tab label under \`childShell\`) in both English and Russian, with key parity between the two locales.

**### Architectural decision**

Copy is the shared contract. Keys are added to the existing \`telegram\` object in \`apps/web/src/lib/i18n/messages/en/app.ts\` and \`ru/app.ts\`; the \`MessageKey\` union is derived from the catalog, so both files must define the exact same keys.

**### Files**

\- Modify \`apps/web/src/lib/i18n/messages/en/app.ts\`.
\- Modify \`apps/web/src/lib/i18n/messages/ru/app.ts\`.

**### Work**

1\. Add a \`requests\` label to the existing Activity-related Mini App copy (e.g. \`requests: 'Requests'\` / \`'Заявки'\`) for the internal Activity subsection switch; do not add a fourth bottom-tab label.
2\. Add a new \`childRequests\` block under \`telegram\` with keys for: section title, empty state, loading, error, retry, status labels (\`pending\`/\`approved\`/\`rejected\`/\`cancelled\`), a \`cancel\` action label, a \`cancelAria\` label, a \`cancelConfirm\`/\`cancelConfirmTitle\`/\`cancelConfirmDescription\`/\`cancelConfirmAction\`/\`cancelConfirmCancel\` set for the confirmation, and \`cancelSuccess\`/\`cancelError\` toasts.
3\. Keep the same key set in both locales; do not add keys to only one file.

**### Acceptance criteria**

\- \`en/app.ts\` and \`ru/app.ts\` define identical \`childRequests\` keys and the Activity subsection \`requests\` label.
\- \`npm run lint\` and \`npm run test\` pass (the existing \`i18nCatalog.test.ts\` en↔ru key-parity check stays green).

**### Verification**

\`\`\`bash
cd apps/web && npm run lint && npm run test
\`\`\`

**### Commit**

\`\`\`bash
git add apps/web/src/lib/i18n/messages/en/app.ts apps/web/src/lib/i18n/messages/ru/app.ts
git commit -m "feat(web): add child requests i18n keys for Mini App"
\`\`\`

**## P1-2: Add a "Requests" subsection inside child Activity**

**\*\*Status:\*\*** ⬜ Not started
**\*\*Priority:\*\*** P1
**\*\*Depends on:\*\*** P1-1

**### Outcome**

The child workspace (\`TelegramChildShell.svelte\`) shows a fourth bottom tab labelled "Заявки" that opens a requests panel, alongside the existing Tasks / Rewards / Activity tabs.

**### Architectural decision**

Keep the existing three bottom tabs unchanged. Extend the Activity view with a small internal state, for example \`activityView: 'history' | 'requests'\`, and render a compact segmented/tab control inside Activity. Deep-link context \`requests\` should open the Activity tab and select the Requests subsection. Do not change the bottom-bar grid from three columns.

**### Files**

\- Modify \`apps/web/src/lib/components/telegram/TelegramChildShell.svelte\`.

**### Work**

1\. Keep the existing bottom \`view\` / \`tabs\` model unchanged; do not add \`'requests'\` as a fourth root view.
2\. Add a \`requests\` deep-link context that resolves to the existing Activity root tab and sets the internal Activity subsection to \`'requests'\`.
3\. Inside Activity, add a compact two-option switch/segmented control: \`История | Заявки\`. Reuse existing Mini App tab/chip styling; keep both labels on one line at 320px.
4\. Render the Requests panel inside the existing Activity branch when the internal subsection is \`'requests'\` (initially a placeholder that will be replaced by the list component in P1-3).
5\. Do **not** change the bottom navigation grid: keep the existing three columns.

**### Acceptance criteria**

\- The bottom navigation remains three tabs; no fourth root-level Requests tab is added.
\- At 320px viewport width the internal \`История | Заявки\` switch fits without wrapping or horizontal overflow, and each option remains easy to tap.
\- Selecting \`Заявки\` inside Activity focuses/shows the requests panel and updates \`aria-selected\`/\`tabindex\` correctly.
\- Requests and Activity history share the same top-level Activity area; pending requests remain actionable only inside the Requests subsection.

**### Verification**

\`\`\`bash
cd apps/web && npm run lint && npm run build
\`\`\`

**### Commit**

\`\`\`bash
git add apps/web/src/lib/components/telegram/TelegramChildShell.svelte
git commit -m "feat(web): add requests subsection to child activity"
\`\`\`

**## P1-3: Create the child request list component with cancel control**

**\*\*Status:\*\*** ⬜ Not started
**\*\*Priority:\*\*** P1
**\*\*Depends on:\*\*** P1-2

**### Outcome**

A new \`TelegramChildRequestList.svelte\` renders the child's own submitted requests (task and reward) with a status chip and, for pending requests, a visible "Отменить" (cancel) control.

**### Architectural decision**

A child-specific list component is still appropriate because its actions differ from the parent list, but the row rendering must reuse/extract a shared presentational request-row primitive rather than copying the parent card. It reads \`$appStore.requests\` (already child-scoped), uses the shared request-kind mapper plus existing \`getTelegramEntityIcon\` / \`stripLeadingEmoji\` / \`TelegramCoin\` helpers, and renders status/date consistently. The cancel control is rendered only when \`status === 'pending'\`.

**### Files**

\- Create \`apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte\`.
\- Create or extract a small shared request-row presentation primitive (for example \`apps/web/src/lib/components/telegram/TelegramRequestRow.svelte\`) if the existing parent list does not already expose one.
\- Modify \`apps/web/src/lib/components/telegram/TelegramChildShell.svelte\` (replace the P1-2 placeholder with this component).

**### Work**

1\. Build the list: for each request show the entity icon, title (\`taskName\`/\`itemName\`/\`title\`), a \`+coins\` amount via \`TelegramCoin\`, request kind, status chip, and \`createdAt\` date/time. Sort pending first, then resolved; inside each group sort by \`createdAt DESC\`.
2\. Distinguish task vs reward through a shared \`requestKind(request)\` mapper. Do not repeat the raw \`requestType === 'shop_purchase'\` check in child UI markup.
3\. Render a cancel button only for \`status === 'pending'\`; use the \`delete\`/\`trash\` icon and the \`childRequests.cancelAria\` label.
4\. Model screen state explicitly as \`loading | ready | empty | error\`, using the same \`state-empty\` / \`state-error\` / \`muted\` patterns used by \`TelegramHistoryList\`. Per-item mutation state is \`idle | cancelling\`.
5\. Emit a \`cancel\` event (or accept an \`onCancel\` prop) so the shell owns the actual API call (see P1-4); keep the component presentational.
6\. Ensure the list uses \`grid-template-columns\:minmax(0,1fr)\` (the known Mini App grid-blowout fix) and that cancel buttons are at least 44×44px.
7\. Reuse the shared request-row presentation primitive for icon/title/meta/status rendering; child-specific markup should contain only child-specific actions/states.

**### Acceptance criteria**

\- Inside Activity → Requests, the child sees their own submitted task and reward requests with correct pending/approved/rejected/cancelled status labels and creation date/time.
\- Pending requests show a cancel control; approved/rejected requests do not.
\- At 320px width the list does not overflow horizontally and the cancel control is reachable without scrolling.
\- Empty state renders when there are no requests.

**### Verification**

\`\`\`bash
cd apps/web && npm run lint && npm run build
\`\`\`

**### Commit**

\`\`\`bash
git add apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/src/lib/components/telegram/TelegramChildShell.svelte
git commit -m "feat(web): add child request list with cancel control"
\`\`\`

**## P1-4: Wire cancel to the existing delete endpoint and add regression coverage**

**\*\*Status:\*\*** ⬜ Not started
**\*\*Priority:\*\*** P1
**\*\*Depends on:\*\*** P1-3

**### Outcome**

Tapping "Отменить" on a pending request shows a confirmation, disables that request while cancellation is in flight, calls the existing \`deleteRequest\` helper (whose backend behavior soft-cancels the request), synchronizes the shared store, and surfaces success/error feedback. The pending badge in Tasks/Rewards updates accordingly while the request remains visible with status \`cancelled\`.

**### Architectural decision**

Reuse \`deleteRequest(requestId, childId)\` from \`apps/web/src/lib/services/api.ts\` and keep the existing \`DELETE /api/requests/{id}\` route, but change the service semantics for a child-owned pending request to persist \`cancelled\` instead of deleting the row. While the request is in flight, keep its id in a \`cancellingIds\` set and disable the action. On success, apply an authoritative returned snapshot when available; otherwise \`await refreshData()\` as reconciliation so \`pendingIds\` re-evaluate. Use the existing \`TelegramConfirmModal\` for confirmation.

**### Files**

\- Modify \`apps/web/src/lib/components/telegram/TelegramChildShell.svelte\` (own the cancel handler + confirmation + refresh).
\- Modify \`apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte\` (emit the cancel event / accept \`onCancel\`).
\- Modify \`apps/web/tests/e2e/telegram-child.spec.ts\` (add a cancel-flow test).
\- Modify \`apps/web/tests/unit/api.test.ts\` (assert the child cancel path calls \`DELETE /api/requests/{id}\` with the child query, if not already covered).
\- Modify the request status enum/model and \`FamilyActionRequestService\` so child cancellation changes a pending request to \`cancelled\` rather than deleting it; add/update the corresponding backend unit/integration test.

**### Work**

1\. In the shell, implement \`handleCancel(request)\`: open \`TelegramConfirmModal\`; on confirm, guard against duplicate submission with \`cancellingIds\`, disable the request action, then call \`deleteRequest(request.id, $appStore.currentChildId)\`.
2\. On success, synchronize the shared store from an authoritative response/snapshot when available, otherwise \`await refreshData()\`; show \`childRequests.cancelSuccess\`. On failure, clear the cancelling state, show \`childRequests.cancelError\`, and keep the request pending.
3\. Guard against cancelling a non-pending request (the button is only rendered for pending, but keep the handler defensive).
4\. Add E2E coverage for: open Activity → Requests; pending cancel → row remains with \`cancelled\` status and pending badge clears; double-click/slow cancel submits once; API failure leaves the request pending; approved/rejected requests have no cancel control; direct \`requests\` deep-link opens Activity with the Requests subsection selected at 320px.
5\. Add/confirm a unit test for the \`deleteRequest\` helper path.

**### Acceptance criteria**

\- Cancelling a pending request keeps it in the list with status \`cancelled\` and clears the corresponding pending badge in Tasks/Rewards.
\- Cancelling an already-approved request is not offered (no cancel control) and, if attempted, surfaces an error rather than silently succeeding.
\- A failed cancel shows an error toast, clears the in-flight state, and leaves the request pending.
\- Repeated taps while cancellation is in flight do not send duplicate DELETE requests.
\- The confirmation dialog is keyboard-accessible and dismissible.

**### Pre-ship check**

\- Inspect representative \`/api/data\` payload sizes with request history included. If historical requests are unbounded and materially inflate the snapshot, document a follow-up for retention/recent-window pagination instead of silently shipping an unbounded list.

**### Verification**

\`\`\`bash
cd apps/web && npm run lint && npm run test && npm run build && npm run test\:e2e
\`\`\`

**### Commit**

\`\`\`bash
git add apps/web/src/lib/components/telegram/TelegramChildShell.svelte apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/unit/api.test.ts
git commit -m "feat(web): wire child request cancellation to delete endpoint"
\`\`\`