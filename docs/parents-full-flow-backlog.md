# Parents UI/UX — Full Flow Alignment Backlog

## Goal

Bring the parents block, admin transfer, and invitation flows in the Mini App to match the attached HTML render (`docs/parents-full-flow-v2-with-telegram.html`) in sizes, colors, and flow: compact parent list with icon actions, a 3-step new-parent wizard (Profile → Account → Done) with Email/Telegram method choice, a Telegram link screen with QR, active profile cards with separate account boxes, change-role flow, and an admin-transfer approval flow (select recipient → pending request → recipient approval screen → complete).

## Architectural decisions

- **Source of truth:** backend remains the single source of truth for memberships and invitations. All parent state persists through the existing `/api/parents` endpoints; the new admin-transfer approval flow adds a new `family_admin_transfer_requests` table and three new endpoints.
- **Layer boundaries:** Svelte components in `apps/web/src/lib/components/telegram/` own the Mini App UI; `ParentInvitationFlow.svelte` and `TelegramParentAccess.svelte` are refactored (not duplicated) to match the render. No new parallel state stores — the existing `loadParentMemberships`/`addParentMembership`/etc. API functions are reused; only `transferParentAdmin` gains an approval step.
- **Existing mechanisms reused:** `createParentTelegramInvite`, `addParentMembership`, `updateParentMembership`, `deactivateParentMembership`, `reactivateParentMembership`, `transferParentAdmin`, `TelegramIcon` icon map (already has `send`, `copy`, `unlink`, `key`, `shield`, `users`, `mail`, `eye`, `pencil`, `check`, `close`, `arrowRight`, `arrowLeft`, `refresh`, `link`, `pause`, `play`).
- **Admin-transfer approval is new:** the current backend `transferAdmin` is instant. The HTML render requires a request/accept handshake (screens 9-12). A new `family_admin_transfer_requests` table, entity, repository, service methods, and three REST endpoints are added. The existing instant `POST /api/parents/{id}/transfer-admin` endpoint is repurposed to *create* a transfer request (returns a pending request, not an instant promotion); a new `POST /api/parents/transfer-requests/{id}/accept` and `POST /api/parents/transfer-requests/{id}/decline` complete the flow. This keeps a single transfer path and avoids a parallel instant-transfer API that the UI no longer uses.
- **Telegram invite integrated into wizard:** the separate Telegram section/button in `TelegramParentAccess.svelte` is removed; Telegram becomes the second tab of the wizard's Account step. The existing `createParentTelegramInvite(parentName)` API is reused.
- **No duplicate DTOs:** `ParentMembershipDto` gains optional `transferRequestStatus` and `transferRequestActorName`/`transferRequestTargetName` fields (nullable) to expose pending transfers in the list without a separate API call.
- **Rejected approaches:** no client-side optimistic transfer state; no separate "transfer request" Svelte store; no second membership-list endpoint. The list endpoint already returns memberships + pending invitations; pending transfer requests are surfaced through the same DTO enrichment.
- **i18n:** new keys added under `app.telegram.parents` (ru) and `app.workspaceAccess`/`app.parentAccess` (en) — no new namespaces.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-001 | P0 | - | Compact parent list is the entry point; all other flows reference it |
| 2 | TASK-002 | P0 | TASK-001 | New-parent wizard replaces the inline email form and separate Telegram button |
| 3 | TASK-003 | P0 | TASK-002 | Telegram link screen is step 3 of the wizard's Telegram path |
| 4 | TASK-004 | P1 | TASK-001 | Active/pending profile cards appear in the list and detail views |
| 5 | TASK-005 | P1 | TASK-001 | Change-role flow is a list action; reuses existing `updateParentMembership` API |
| 6 | TASK-006 | P0 | - | Schema + entity for admin-transfer requests (no UI dependency) |
| 7 | TASK-007 | P0 | TASK-006 | Backend service + endpoints for transfer request/accept/decline |
| 8 | TASK-008 | P0 | TASK-007, TASK-001 | Frontend admin-transfer flow: select → pending → approval → complete |
| 9 | TASK-009 | P2 | TASK-001..TASK-008 | Remove dead `TelegramRolesAccess.svelte` and unused i18n keys |

---

## TASK-001: Compact parent list with icon actions

**Status:** DONE
**Priority:** P0
**Depends on:** -

### Exact scope

Rebuild the parent list rendering inside `ParentInvitationFlow.svelte` (the `.members` block, lines 60-87) to match HTML render screen 1: compact `.parent-row` cards with avatar, name + status badge, role label, email/Telegram identity chips, and two 42×42 icon buttons (deactivate/change-role for active editors; transfer-admin + disabled-lock for the admin). Replace the current flat text-button rows.

### Files

- Modify `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`
- Search anchor: `.members` block, `label(parent)`, `permissionLabel`, `statusLabel` functions

### Goal

The parent list renders compact rows matching the HTML render: avatar tile, full name with status chip, role subtitle, email + Telegram identity chips (ellipsis-truncated), and consistent 42×42 icon action buttons with hover tooltips. Admin rows show a disabled lock icon + transfer icon; editor rows show deactivate (danger) + change-role icons.

### Architectural decision

The list component owns row layout and action dispatch. Identity chips read `parent.email` and `parent.telegramUsername` from the existing `ParentMembership` type (no type changes). Pending invitations (status `pending`, id null) render with the same row shape but show Resend/Revoke icon buttons instead of deactivate/transfer.

### Required changes

1. Replace `.member` grid layout with a 3-column grid: `auto minmax(0,1fr) auto` (avatar, main, actions) matching `.parent-row` in the HTML render.
2. Add `.avatar` tile (42×42, 12px radius, `--blueWeak` background) with the first letter of `label(parent)`.
3. Add `.topline` (name + status badge) and `.row-role` (permission label) below; status badges use `.state.active` (green), `.state.pending` (amber), `.state.off` (grey) color tokens from the render.
4. Add `.ids` chip row: email chip (mail icon + `parent.email`, max-width 320px, ellipsis) and Telegram chip (send icon + `@${parent.telegramUsername}`, max-width 240px, ellipsis); hide a chip when its value is null/empty.
5. Replace text action buttons with 42×42 `.icon-btn` elements using `TelegramIcon`: admin → disabled lock (`pause`/`unlink`) + transfer (`send` or a new transfer icon); active editor → danger deactivate (`unlink`) + change-role (`pencil`); inactive → reactivate (`play`); pending → resend (`send`) + revoke (`unlink`/`trash`).
6. Add `.tip` hover tooltips on each icon button (absolute, right-aligned, dark background) using the existing i18n labels.
7. Add the admin-protection note (`.note`) below the list only when an admin is present: "Администратора нельзя деактивировать. Сначала нужно передать админство другому активному родителю." (use a new i18n key `app.telegram.parents.adminProtectionNote`).
8. Add responsive `@media (max-width:640px)` rules: row collapses to `auto minmax(0,1fr)`, actions wrap to the second column, identity chips go full-width.

### Out of scope

- New-parent wizard (TASK-002), Telegram link screen (TASK-003), profile detail cards (TASK-004), change-role modal (TASK-005), admin-transfer flow (TASK-008).
- Backend changes — the existing `loadParentMemberships` payload already contains all needed fields.

### Acceptance criteria

- At a 390px viewport, each parent row shows the avatar, name (not truncated mid-word), status badge, role, and both identity chips without horizontal scroll; chips that overflow ellipsize.
- Admin rows render a disabled lock icon with tooltip "Администратора нельзя деактивировать" and an enabled transfer icon.
- Active editor rows render a danger deactivate icon and a change-role icon; inactive rows render a reactivate icon.
- Pending invitation rows (id null, invitationStatus `pending`) render Resend + Revoke icon buttons.
- Hovering an icon button reveals its tooltip within the viewport on mobile (right-aligned, not clipped).
- Keyboard focus reaches each icon button in a logical order; the focused button has a visible focus ring (`outline:3px solid #80aaff`).
- Icon buttons are at least 42×42px touch targets.
- The admin-protection note appears only when an admin membership is in the list.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test -- --reporter=verbose
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte \
        apps/web/src/lib/i18n/messages/ru/app.ts \
        apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "refactor(web): compact parent list with icon actions and identity chips"
```

---

## TASK-002: 3-step new-parent wizard (Profile → Account → Done)

**Status:** DONE
**Priority:** P0
**Depends on:** TASK-001

### Exact scope

Replace the inline email invite form (`.invite-form` in `ParentInvitationFlow.svelte`, lines 89-99) and the separate Telegram section in `TelegramParentAccess.svelte` (lines 18-41) with a single wizard flow matching HTML render screens 2-3: Step 1 Profile (name + role cards), Step 2 Account (Email/Telegram tab choice + input), Step 3 Done. The wizard is launched by an "Add parent" button and replaces both the email form and the standalone Telegram block.

### Files

- Modify `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`
- Modify `apps/web/src/lib/components/telegram/TelegramParentAccess.svelte`
- Search anchor: `invite()` function, `addParentMembership` call, `showTelegramInvite`/`onTelegramInvite` props, `createParentTelegramInvite` usage

### Goal

Clicking "Добавить родителя" opens a wizard sheet. Step 1 collects the parent name and role (editor/viewer cards). Step 2 offers Email and Telegram tabs; the Email tab shows an email input and creates the parent via `addParentMembership`; the Telegram tab shows the name (read-only) and creates a Telegram link via `createParentTelegramInvite`. Step 3 shows the result (link ready / email sent). The separate Telegram section in `TelegramParentAccess.svelte` is removed.

### Architectural decision

The wizard lives in `ParentInvitationFlow.svelte` as an overlay sheet (reusing the `.sheet` pattern from `TelegramParentFamily.svelte`). It calls existing `addParentMembership({ email, permission })` for the email path and `createParentTelegramInvite(parentName)` for the Telegram path. No new API. The `showTelegramInvite`/`onTelegramInvite` props on `ParentAccessPanel`/`ParentInvitationFlow` are removed (dead props after merge).

### Required changes

1. Add wizard state: `wizardOpen`, `wizardStep` (1|2|3), `wizardName`, `wizardRole` (`editor`|`viewer`), `wizardMethod` (`email`|`telegram`), `wizardEmail`, `wizardLink`, `wizardBusy`, `wizardError`.
2. Add an "Добавить родителя" button (`.btn` style) above the list that sets `wizardOpen = true` and resets wizard state.
3. Step 1 (Profile): name input (`.input`), role cards (`.role-grid` with two `.role-card` — Editor/Viewer, active state toggles `wizardRole`), warn note "Роль администратора здесь не выбирается…", Cancel/Далее buttons.
4. Step 2 (Account): `.tabs` with Email/Telegram tabs. Email tab → email input + info note + Назад/Создать родителя buttons (calls `addParentMembership`). Telegram tab → read-only name input + info/warn notes + Назад/Создать ссылку buttons (calls `createParentTelegramInvite`).
5. Step 3 (Done): Email path → success preview card (TASK-004 style). Telegram path → link card (TASK-003).
6. Remove the old `.invite-form` block and the `invite()` function.
7. Remove the `showTelegramInvite`/`onTelegramInvite` props from `ParentInvitationFlow.svelte` and `ParentAccessPanel.svelte`.
8. Remove the `<section class="telegram-option">` block and `createInvite`/`copy` functions from `TelegramParentAccess.svelte`; it becomes a thin sheet wrapping `<ParentAccessPanel />`.
9. Add stepper UI (`.stepper` with `.step`/`.step.active` chips) in the wizard header matching the render.
10. Add i18n keys: `app.telegram.parents.wizardStep1/2/3`, `profileStep`, `accountStep`, `doneStep`, `parentName`, `roleEditor`, `roleViewer`, `roleEditorDesc`, `roleViewerDesc`, `adminRoleNotSelectable`, `methodEmail`, `methodTelegram`, `createParent`, `createLink`, `next`, `back`.

### Out of scope

- Telegram link QR rendering (TASK-003), profile detail cards used in step 3 (TASK-004), admin-transfer flow (TASK-008).
- Backend changes.

### Acceptance criteria

- The "Добавить родителя" button opens the wizard at step 1; the old inline email form is gone.
- Step 1 requires a non-empty name before "Далее" is enabled; role defaults to Editor and only Editor/Viewer cards are selectable.
- Step 2 Email tab: email input validates presence; "Создать родителя" calls `addParentMembership` and on success advances to step 3.
- Step 2 Telegram tab: name is read-only (from step 1); "Создать ссылку" calls `createParentTelegramInvite` and on success advances to step 3 showing the link.
- Step 3 reflects the chosen path (email-sent card or Telegram link card).
- "Назад" returns to step 1 preserving name and role; "Отмена" closes the wizard.
- The stepper shows the active step highlighted.
- At 390px width the wizard sheet is full-width with single-column role cards and stacked action buttons.
- The separate Telegram section in `TelegramParentAccess.svelte` no longer renders.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte \
        apps/web/src/lib/features/workspace/family/ParentAccessPanel.svelte \
        apps/web/src/lib/components/telegram/TelegramParentAccess.svelte \
        apps/web/src/lib/i18n/messages/ru/app.ts \
        apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "feat(web): 3-step new-parent wizard with Email/Telegram method choice"
```

---

## TASK-003: Telegram link screen with QR and Open-in-Telegram

**Status:** DONE
**Priority:** P0
**Depends on:** TASK-002

### Exact scope

Render the Telegram link result (wizard step 3, Telegram path) to match HTML render screen 5: a `.link-card` with the one-time link value, a copy icon button, a decorative QR placeholder, a "Ссылка действует 24 часа…" note, and "Закрыть" / "Открыть в Telegram" buttons. The "Открыть в Telegram" button opens `result.launchUrl` in a new tab.

### Files

- Modify `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`
- Search anchor: `createParentTelegramInvite` result, `wizardLink` state (from TASK-002)

### Goal

When the wizard creates a Telegram invite, step 3 shows the link in a card with a copy button, a QR placeholder, an expiry note, and an "Открыть в Telegram" action that navigates to the launch URL.

### Architectural decision

The QR is a decorative placeholder (the bot URL is already a deep link). A real QR generator library is not added in this task; the `.qr` CSS background pattern from the render is used as a visual placeholder. The "Открыть в Telegram" button uses `window.open(inviteLink, '_blank', 'noopener')`.

### Required changes

1. Add the `.link-card` markup inside wizard step 3 (Telegram path): `.link-box` with `.link-value` (ellipsized link) and a 42×42 copy `.icon-btn`.
2. Add the `.qr` placeholder (94×94 CSS pattern from the render) and the `.small` expiry note beside it.
3. Add the `.action-grid` with "Закрыть" (`.cancel`) and "Открыть в Telegram" (`.btn`) buttons.
4. Wire the copy button to `navigator.clipboard.writeText(inviteLink)` with a copied state.
5. Wire "Открыть в Telegram" to `window.open(inviteLink, '_blank', 'noopener')`.
6. Add i18n keys: `app.telegram.parents.linkExpiryNote`, `openInTelegram`, `close`.

### Out of scope

- Real QR code generation, email-path step 3 card (TASK-004), backend changes.

### Acceptance criteria

- After creating a Telegram link, step 3 shows the link value (ellipsized if long), a copy button that copies to clipboard and shows "Скопировано", a QR placeholder, and the expiry note.
- "Открыть в Telegram" opens the launch URL in a new tab with `noopener`.
- "Закрыть" closes the wizard and refreshes the parent list.
- At 390px the link card, QR, and buttons stack without horizontal scroll.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte \
        apps/web/src/lib/i18n/messages/ru/app.ts \
        apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "feat(web): Telegram link screen with QR placeholder and Open-in-Telegram"
```

---

## TASK-004: Active/pending profile cards with separate account boxes

**Status:** TODO
**Priority:** P1
**Depends on:** TASK-001

### Exact scope

Render a parent profile detail card matching HTML render screens 6-8 and 10: `.preview` card with `.preview-head` (avatar, name, role, status badge) and a 2-column `.account-grid` showing Email and Telegram boxes separately (title, value, sub-text), plus `.preview-actions` (Deactivate/Change-role for active; Resend email/Telegram-link for pending; Cancel-transfer for pending transfer). Used in the wizard step-3 success state and as a detail sheet when tapping a parent row.

### Files

- Modify `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`
- Search anchor: `ParentMembership` type usage, `label(parent)`, `telegramUsername`/`telegramDisplayName` fields

### Goal

Tapping a parent row opens a detail sheet showing the profile card with separate Email/Telegram account boxes and contextual actions. The wizard step-3 email-success path reuses the same card shape.

### Architectural decision

The card reads `parent.email`, `parent.telegramUsername`, `parent.telegramDisplayName` from the existing DTO. "Not linked" is shown when a field is null. Actions reuse existing API functions (`deactivateParentMembership`, `updateParentMembership`, `resendParentInvitation`, `createParentTelegramInvite`).

### Required changes

1. Add a `selectedParent` state and a detail sheet (`.sheet` pattern) opened from a row tap (the row's main area becomes a button; icon buttons stop propagation).
2. Render `.preview` with `.preview-head` (avatar, name, role, status badge) and `.account-grid` (Email box + Telegram box).
3. Email box: title "Email", value `parent.email ?? 'Не привязан'`, sub-text "Подтверждён"/"Приглашение отправлено"/"Можно добавить позже" depending on status.
4. Telegram box: title "Telegram", value `@${telegramUsername}` or "Не привязан", sub-text `Имя: ${telegramDisplayName}` or "Можно добавить позже".
5. `.preview-actions`: active → Deactivate (`.danger`) + Change role (`.ghost`); pending → Повторить email (`.ghost`) + Telegram-ссылка (`.ghost`); inactive → Активировать (`.ok`); pending transfer → Отменить запрос (`.cancel`) (wired in TASK-008).
6. Add i18n keys: `app.telegram.parents.emailBox`, `telegramBox`, `confirmed`, `inviteSent`, `notLinked`, `canAddLater`, `resendEmail`, `telegramLink`.

### Out of scope

- Change-role modal internals (TASK-005), admin-transfer flow (TASK-008), backend changes.

### Acceptance criteria

- Tapping a parent row opens the detail sheet; icon buttons on the row do not open the sheet.
- The card shows both account boxes side-by-side at ≥640px and stacked at <640px.
- A parent with only email shows "Не привязан" in the Telegram box; a Telegram-only parent shows "Не привязан" in the Email box.
- Active parents show Deactivate + Change role actions; pending parents show Resend email + Telegram-link actions.
- Closing the sheet returns focus to the tapped row.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte \
        apps/web/src/lib/i18n/messages/ru/app.ts \
        apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "feat(web): parent profile detail card with separate account boxes"
```

---

## TASK-005: Change-role flow with role cards

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-001

### Exact scope

Wire the change-role icon button (from TASK-001) to a small sheet matching the role-grid in HTML render screen 2: two role cards (Editor/Viewer) and a Save/Cancel pair. Calls the existing `updateParentMembership(membershipId, { permission })` API. The admin role is not selectable here (transferred via TASK-008).

### Files

- Modify `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`
- Search anchor: `updateParentMembership` import (already present in api.ts, line 462), `permissionLabel` function

### Goal

The change-role icon opens a sheet with Editor/Viewer role cards; selecting one and saving calls `updateParentMembership` and refreshes the list.

### Architectural decision

Reuses the `.role-grid`/`.role-card` markup from the wizard (TASK-002). The `family_admin` role card is not shown — admin is only transferred via the approval flow. The API already prevents demoting the last admin.

### Required changes

1. Add `roleEditParent` and `roleEditValue` state.
2. Open the sheet from the change-role icon button (stop propagation so the detail card doesn't also open).
3. Render two `.role-card` options (Editor/Viewer) with active highlight on `roleEditValue`.
4. Save calls `updateParentMembership(roleEditParent.id, { permission: roleEditValue })`, refreshes the list, closes the sheet.
5. Disable Save while busy; show errors inline.
6. Reuse i18n keys from TASK-002 (`roleEditor`, `roleViewer`, `roleEditorDesc`, `roleViewerDesc`, `saveButton`, `cancelButton`).

### Out of scope

- Admin transfer (TASK-008), backend changes.

### Acceptance criteria

- The change-role icon opens the sheet with the current role highlighted.
- Selecting Editor or Viewer and saving updates the parent's role in the list after reload.
- The admin role is never offered in this sheet.
- Save is disabled while the request is in flight.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte \
        apps/web/src/lib/i18n/messages/ru/app.ts \
        apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "feat(web): change-role sheet with Editor/Viewer cards"
```

---

## TASK-006: Admin-transfer request schema and entity

**Status:** DONE
**Priority:** P0
**Depends on:** -

### Exact scope

Add a new `family_admin_transfer_requests` table and `FamilyAdminTransferRequestEntity` to support the approval-based admin transfer shown in HTML render screens 9-12. The entity stores: familyId, actorMembershipId (current admin), targetMembershipId (recipient), status (pending/accepted/declined/cancelled), createdAt, respondedAt, cancelledAt.

### Files

- Create `apps/backend/src/main/resources/db/migration/V48__add_family_admin_transfer_requests.sql`
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/membership/FamilyAdminTransferRequestEntity.java`
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/membership/FamilyAdminTransferRequestRepository.java`
- Search anchor: `FamilyParentMembershipEntity` (entity pattern), `ChildMagicLinkInvitationRepository` (repository pattern)

### Goal

A new table and JPA entity exist for admin-transfer requests with a pending/accepted/declined/cancelled lifecycle, and a Panache repository with lookups by family, target, and pending status.

### Architectural decision

The entity extends `CreatedAtEntity` (consistent with other audit entities) and includes `respondedAt`/`cancelledAt` timestamps. Only one pending request per family is allowed (enforced by a partial unique index on `family_id WHERE status = 'pending'`). The repository is `ApplicationScoped` implementing `PanacheRepositoryBase`. No changes to `FamilyParentMembershipEntity` — the transfer request references membership IDs.

### Required changes

1. Migration `V48`: create `family_admin_transfer_requests` with `id`, `family_id`, `actor_membership_id`, `target_membership_id`, `status` (CHECK constraint for pending/accepted/declined/cancelled), `created_at`, `updated_at`, `responded_at`, `cancelled_at`; FK to `family_parent_memberships(id)` for actor and target; partial unique index `uq_family_admin_transfer_pending ON (family_id) WHERE status = 'pending'`.
2. Entity class with `Status` enum (pending/accepted/declined/cancelled), `@Builder.Default status = pending`, columns matching the migration.
3. Repository with `findPendingByFamily(Integer familyId)`, `findByTarget(Integer targetMembershipId)`, `findPendingByTarget(Integer targetMembershipId)`.
4. Add the H2 test baseline migration `apps/backend/src/test/resources/db/migration/V48__add_family_admin_transfer_requests.sql` mirroring the production migration with H2-compatible syntax.

### Out of scope

- Service logic (TASK-007), frontend (TASK-008), modifying the existing instant `transferAdmin` endpoint (done in TASK-007).

### Acceptance criteria

- Migration applies cleanly on PostgreSQL (verified via `docker compose` dev DB) and on H2 (test baseline).
- The entity maps all columns; Hibernate validates the schema on startup without errors.
- The repository returns pending requests by family and by target.
- Only one pending request per family is allowed by the DB constraint.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration/V48__add_family_admin_transfer_requests.sql \
        apps/backend/src/test/resources/db/migration/V48__add_family_admin_transfer_requests.sql \
        apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/membership/FamilyAdminTransferRequestEntity.java \
        apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/membership/FamilyAdminTransferRequestRepository.java
git commit -m "feat(backend): admin-transfer request schema and entity"
```

---

## TASK-007: Admin-transfer request/accept/decline backend

**Status:** DONE
**Priority:** P0
**Depends on:** TASK-006

### Exact scope

Implement the approval-based admin transfer on the backend. Repurpose `POST /api/parents/{membershipId}/transfer-admin` to *create* a pending transfer request (no instant promotion). Add `POST /api/parents/transfer-requests/{requestId}/accept` and `POST /api/parents/transfer-requests/{requestId}/decline`. Enrich `ParentMembershipDto` with `transferRequestStatus` and `transferRequestActorName`/`transferRequestTargetName` so the list can show pending transfers (HTML render screen 10).

### Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessService.java`
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImpl.java`
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyParentAccessResource.java`
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/response/ParentMembershipDto.java`
- Search anchor: `transferAdmin` method (line 348 in service impl), `transferAdmin` endpoint in resource, `toDto`/`toInvitationDto` methods

### Goal

An admin can create a pending transfer request targeting an active non-admin parent; the target can accept (promoting themselves and demoting the actor) or decline. The list endpoint surfaces pending transfer state on the actor and target rows.

### Architectural decision

`transferAdmin` now creates a `FamilyAdminTransferRequestEntity` (status `pending`) instead of instantly swapping permissions. `accept` verifies the requester is the target membership's account, promotes target to `family_admin`, demotes actor to `editor`, sets request `accepted` + `respondedAt`, and cancels any other pending request for the family. `decline` sets `declined` + `respondedAt`. The DTO enrichment reads the pending request for the family and sets `transferRequestStatus` on both actor and target rows, plus names for the approval screen.

### Required changes

1. `FamilyParentAccessService`: add `createTransferRequest`, `acceptTransferRequest`, `declineTransferRequest` methods; change `transferAdmin` to delegate to `createTransferRequest` (keep the old method signature for the endpoint, but its behavior changes to creating a request).
2. `FamilyParentAccessServiceImpl.transferAdmin`: validate target is active + non-admin + actor is admin; check no existing pending request for the family; persist a new `FamilyAdminTransferRequestEntity`; return the target DTO with `transferRequestStatus = "pending"`.
3. Add `acceptTransferRequest(requestId, familyId, actorParentAccountId, actorEmail)`: load request, verify status `pending`, verify the caller is the target membership's parent account (not the original actor), promote target → `family_admin`, demote original actor → `editor`, set request `accepted` + `respondedAt`, cancel other pending requests for the family. Return the target DTO.
4. Add `declineTransferRequest(requestId, familyId, actorParentAccountId, actorEmail)`: verify caller is the target; set request `declined` + `respondedAt`.
5. `FamilyParentAccessResource`: keep `POST /api/parents/{membershipId}/transfer-admin` (now creates a request); add `POST /api/parents/transfer-requests/{requestId}/accept` and `POST /api/parents/transfer-requests/{requestId}/decline`, both gated by `auth.canManageMemberships()` (the target is a family member, so they have manage-memberships permission as an editor? — verify: currently `canManageMemberships` is admin-only. The accept/decline endpoints must allow the *target* (an editor) to respond, so they need a different guard: `auth.canEditFamilyData()` plus verify the caller's membership is the request's target).
6. `ParentMembershipDto`: add nullable `transferRequestStatus` (String), `transferRequestActorName` (String), `transferRequestTargetName` (String). Update the two existing convenience constructors to pass null for the new fields.
7. `listMemberships`: after loading memberships, load the pending transfer request for the family; if present, set `transferRequestStatus = "pending"` and the actor/target names on the matching rows.
8. Add unit tests: `FamilyParentAccessServiceImplTest` cases for create/accept/decline (success, wrong caller, non-pending, non-active target, last-admin guard).

### Out of scope

- Frontend transfer flow (TASK-008), wizard/list visual changes.

### Acceptance criteria

- `POST /api/parents/{id}/transfer-admin` returns 200 with the target DTO and `transferRequestStatus = "pending"`; no permission change occurs until accept.
- `POST /api/parents/transfer-requests/{id}/accept` by the target account promotes target and demotes the original actor; returns 200 with the updated target DTO.
- `POST /api/parents/transfer-requests/{id}/accept` by a non-target account returns 403.
- `POST /api/parents/transfer-requests/{id}/decline` by the target sets the request to `declined`.
- Creating a second pending request while one is already pending returns 409.
- `GET /api/parents` includes `transferRequestStatus` and actor/target names on the relevant rows when a pending request exists.
- Accepting a transfer cancels any other pending request for the family.
- Backend `./mvnw verify` passes including new unit tests.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/ \
        apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyParentAccessResource.java \
        apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/response/ParentMembershipDto.java \
        apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/membership/
git commit -m "feat(backend): approval-based admin transfer with request/accept/decline"
```

---

## TASK-008: Frontend admin-transfer flow (select → pending → approval → complete)

**Status:** DONE
**Priority:** P0
**Depends on:** TASK-007, TASK-001

### Exact scope

Implement the frontend admin-transfer flow matching HTML render screens 9-12: the admin clicks the transfer icon on an active non-admin row → a "Передать админство" sheet lists eligible active parents (with disabled cards for pending-invitation parents) → "Отправить запрос" calls `transferParentAdmin` (now creates a pending request) → the list shows a "Ожидает" transfer badge on actor and target rows (screen 10) with a "Отменить запрос" action → the target parent sees an approval sheet (screen 11) with "Принять админство"/"Отклонить" → on accept, the list refreshes showing the new admin and the old admin as editor (screen 12).

### Files

- Modify `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`
- Modify `apps/web/src/lib/services/api.ts`
- Modify `apps/web/src/lib/types/auth.ts`
- Search anchor: `transferParentAdmin` (api.ts line 471), `ParentMembership` type (auth.ts), `transferRequestStatus` usage from TASK-007 DTO

### Goal

The admin can initiate a transfer; the target can accept or decline; the list reflects pending and completed transfers with the correct badges and actions.

### Architectural decision

The frontend calls the existing `transferParentAdmin(membershipId)` (which now creates a pending request per TASK-007). Two new API functions are added: `acceptAdminTransfer(requestId)` and `declineAdminTransfer(requestId)`. The `ParentMembership` TS type gains `transferRequestStatus`, `transferRequestActorName`, `transferRequestTargetName` (nullable). The list reads `transferRequestStatus` to show the "Ожидает" transfer badge (screen 10) and the "Отменить запрос" action. The target parent (detected by `transferRequestTargetName` matching the current user's membership) sees an approval banner/sheet.

### Required changes

1. `api.ts`: add `acceptAdminTransfer(requestId)` → `POST /api/parents/transfer-requests/{id}/accept`; add `declineAdminTransfer(requestId)` → `POST /api/parents/transfer-requests/{id}/decline`; add `cancelAdminTransfer(requestId)` (reuse decline or a dedicated cancel endpoint if added — confirm in TASK-007; if only accept/decline exist, "Отменить запрос" by the actor calls a new `cancelAdminTransfer` or reuses `decline` — decide in TASK-007: add `POST /api/parents/transfer-requests/{id}/cancel` for the actor).
2. `types/auth.ts`: add `transferRequestStatus?: string | null`, `transferRequestActorName?: string | null`, `transferRequestTargetName?: string | null` to `ParentMembership`.
3. Transfer-initiate sheet: list active non-admin parents as eligible cards; pending-invitation parents show as disabled cards ("Приглашение не принято"); "Отправить запрос" calls `transferParentAdmin(targetId)`.
4. Pending-transfer state: rows with `transferRequestStatus === 'pending'` show a `.state.transfer` badge ("Ожидает") and, on the actor row, a "Отменить запрос" action.
5. Target approval: when the current user's membership has `transferRequestStatus === 'pending'` and they are the target, show an approval sheet (screen 11) with actor name, "Принять админство" (`.ok`) and "Отклонить" (`.cancel`), calling accept/decline APIs.
6. On accept/decline/cancel, reload the list.
7. Add i18n keys: `app.telegram.parents.transferEligible`, `transferIneligible`, `sendRequest`, `transferPending`, `cancelRequest`, `acceptAdmin`, `declineAdmin`, `transferComplete`, `transferCompleteInfo`, `becomesAdminAfterConfirm`, `transferWarn`.

### Out of scope

- Backend changes (TASK-007), wizard (TASK-002), profile cards (TASK-004).

### Acceptance criteria

- The admin's transfer icon opens the eligible-parents sheet; only active non-admin parents are selectable; pending-invitation parents appear disabled.
- "Отправить запрос" creates a pending transfer; the actor and target rows show "Ожидает" badges; the actor row shows "Отменить запрос".
- The target parent sees an approval sheet with the actor's name and Accept/Decline buttons.
- Accepting promotes the target and demotes the actor; the list refreshes to show the new admin (with disabled lock + transfer icons) and the old admin as editor (with deactivate + change-role icons) — screen 12.
- Declining or cancelling removes the pending badges and returns to the normal list state.
- At 390px the transfer sheets are full-width with stacked action buttons.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run build && npm run test
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte \
        apps/web/src/lib/services/api.ts \
        apps/web/src/lib/types/auth.ts \
        apps/web/src/lib/i18n/messages/ru/app.ts \
        apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "feat(web): admin-transfer approval flow (select, pending, accept/decline)"
```

---

## TASK-009: Remove dead code and unused i18n keys

**Status:** TODO
**Priority:** P2
**Depends on:** TASK-001, TASK-002, TASK-008

### Exact scope

Delete the unused `TelegramRolesAccess.svelte` component (never imported) and prune i18n keys from `app.telegram.roles` and `app.parentAccess` that are no longer referenced after the refactor. Verify `i18nCatalog.test.ts` (en↔ru parity) still passes.

### Files

- Delete `apps/web/src/lib/components/telegram/TelegramRolesAccess.svelte`
- Modify `apps/web/src/lib/i18n/messages/ru/app.ts`
- Modify `apps/web/src/lib/i18n/messages/en/app.ts`
- Search anchor: `TelegramRolesAccess` (no imports), `app.telegram.roles` namespace, `app.parentAccess` namespace

### Goal

No dead parent-management component or orphaned i18n keys remain; the i18n parity test passes.

### Architectural decision

Only remove keys confirmed unused by grep across `apps/web/src`. Keep keys that any remaining component references. The `i18nCatalog.test.ts` enforces ru/en parity, so removals must be mirrored in both files.

### Required changes

1. Delete `TelegramRolesAccess.svelte`.
2. Grep for each key in `app.telegram.roles` and `app.parentAccess`; remove unreferenced keys from both ru and en files.
3. Run the i18n parity test and fix any drift.

### Out of scope

- Renaming namespaces, backend changes.

### Acceptance criteria

- `TelegramRolesAccess.svelte` is deleted; no import references remain.
- `npm run lint` reports 0 errors and 0 warnings.
- `npm run test` passes including `i18nCatalog.test.ts`.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramRolesAccess.svelte \
        apps/web/src/lib/i18n/messages/ru/app.ts \
        apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "chore(web): remove dead TelegramRolesAccess and unused parent i18n keys"
```