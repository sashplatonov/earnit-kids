# Parents Full-Flow — Review Backlog

## Goal

Remediate confirmed findings from the review of TASK-001 through TASK-009 of `docs/parents-full-flow-backlog.md`. The implemented admin-transfer approval flow, new-parent wizard, compact parent list, and change-role sheet were inspected end-to-end across the Svelte frontend, the Quarkus backend resource/service/persistence layers, the H2/PostgreSQL migrations, and the unit-test suites. All quality gates pass (`./mvnw verify`, `npm run lint`, `npm run test`, `npm run build`), so every task below is a confirmed defect that the gates do not catch, not a build failure.

## Architectural decisions (review-rooted)

- **Transfer target identification is the core defect.** The DTO enrichment in `FamilyParentAccessServiceImpl.enrichWithPendingTransferRequest` stamps the same `transferRequestStatus`/`transferRequestActorName`/`transferRequestTargetName`/`transferRequestId` on both the actor and target rows. The frontend then tries to detect the target by comparing the current user's email against the first pending row's email. This is broken in two independent ways: (a) `parents.find(p => p.transferRequestStatus === 'pending')` returns whichever row the unsorted repository query yields first — typically the admin/actor who was created earlier — so the approval sheet renders for the wrong person or not at all; and (b) for Telegram-only parents both `pendingTransferRow.email` and `currentEmail` are null, so `'' === ''` is true and any Telegram-only parent sees the approval sheet. The safe remediation is a new `transferRequestRole` DTO field (`"actor" | "target" | null`) set from the already-known membership IDs, and a frontend check against that field — no email comparison, no row-order dependency.
- **Viewer-permission parents are valid transfer targets but are blocked at the resource guard.** `createTransferRequest` only rejects admins as targets; viewers are eligible. Yet `acceptTransferRequest`/`declineTransferRequest` are guarded by `auth.canEditFamilyData()`, which returns true only for `family_admin` and `editor`. A viewer target gets 401 before the service-level ownership check runs. The guard must allow any authenticated family parent, because the service already verifies the caller is the request target.
- **The tablist in the wizard step 2 is an incomplete ARIA pattern.** The tabs have `role="tab"` and `aria-selected` but no `id` or `aria-controls`, and the tab panels have no `role="tabpanel"`/`id`/`aria-labelledby`. Screen-reader users cannot associate tabs with their content.
- **No HTTP-level integration tests cover the new transfer endpoints.** The added `FamilyParentAccessServiceImplTest` cases mock the repository and exercise the service in isolation. The resource-level auth guards (viewer rejection, non-target 403, 409 on duplicate pending) are untested at the JAX-RS layer.
- **`enrichWithPendingTransferRequest` issues two redundant `findByIdOptional` calls** for the actor and target memberships that are already in the loaded `memberships` list. This is an N+1 within a list path that already batch-loads parents and identities.
- **`toTransferDto` drops Telegram identity fields.** The transfer create/accept/decline/cancel responses pass `null` for `telegramUserId`/`telegramUsername`/`telegramDisplayName`, so the frontend cannot render the target's Telegram chip from the mutation response and must wait for the list reload.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | REVIEW-001 | P1 | - | DTO contract change that unblocks the frontend fix in REVIEW-002 |
| 2 | REVIEW-002 | P1 | REVIEW-001 | Frontend approval-sheet target detection depends on the new DTO field |
| 3 | REVIEW-003 | P1 | - | Backend resource guard fix; independent of the DTO change |
| 4 | REVIEW-004 | P2 | REVIEW-001, REVIEW-003 | HTTP integration tests depend on the corrected contract and guard |
| 5 | REVIEW-005 | P2 | - | ARIA tablist completion; frontend-only |
| 6 | REVIEW-006 | P2 | - | Focus restoration for all sheets; frontend-only |
| 7 | REVIEW-007 | P2 | - | Eliminate redundant membership lookups in enrichment path |
| 8 | REVIEW-008 | P3 | - | Include Telegram identity in transfer mutation responses |

---

## REVIEW-001: Add `transferRequestRole` to `ParentMembershipDto` and set it during enrichment

**Status:** DONE

**Priority:** P1
**Depends on:** -

### Outcome

The list endpoint and transfer mutation responses carry an explicit `transferRequestRole` (`"actor"`, `"target"`, or `null`) so the frontend can deterministically identify which row is the target without email comparison or row-order assumptions.

### Files

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/response/ParentMembershipDto.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImpl.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImplTest.java`
- `apps/web/src/lib/types/auth.ts`

### Work

1. Add `String transferRequestRole` to the `ParentMembershipDto` record (nullable), positioned after `transferRequestId`. Update all existing convenience constructors to pass `null` for the new field.
2. In `enrichWithPendingTransferRequest`, set `transferRequestRole = "actor"` on the row whose `id` equals `request.getActorMembershipId()`, and `transferRequestRole = "target"` on the row whose `id` equals `request.getTargetMembershipId()`.
3. In `toTransferDto` (used by `createTransferRequest`), set `transferRequestRole = "target"` on the returned target DTO.
4. In `acceptTransferRequest`'s returned DTO (currently `toDto(target, parent, null)`), enrich it with `transferRequestRole = null` (the transfer is completed, so the role is no longer relevant) — or leave `null` since the request is no longer pending. Ensure the response is consistent.
5. Add `transferRequestRole?: 'actor' | 'target' | null` to the `ParentMembership` TypeScript type in `apps/web/src/lib/types/auth.ts`.
6. Update `listMemberships_pendingTransfer_enrichesActorAndTargetRows` to assert `transferRequestRole` is `"actor"` on the actor row and `"target"` on the target row.

### Acceptance criteria

- `GET /api/parents` with a pending transfer returns `transferRequestRole: "actor"` on the admin row and `transferRequestRole: "target"` on the recipient row.
- `POST /api/parents/{id}/transfer-admin` returns a DTO with `transferRequestRole: "target"`.
- Rows not involved in a pending transfer have `transferRequestRole: null`.
- `FamilyParentAccessServiceImplTest.listMemberships_pendingTransfer_enrichesActorAndTargetRows` asserts both roles.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -DskipITs test -Dtest='FamilyParentAccessServiceImplTest'
cd apps/web && npm run lint && npm run test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/response/ParentMembershipDto.java \
        apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImpl.java \
        apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImplTest.java \
        apps/web/src/lib/types/auth.ts
git commit -m "fix(backend): add transferRequestRole to ParentMembershipDto for deterministic target detection"
```

---

## REVIEW-002: Frontend approval sheet uses `transferRequestRole` instead of email comparison

**Status:** DONE

**Priority:** P1
**Depends on:** REVIEW-001

### Outcome

The transfer approval sheet appears only for the actual target parent, regardless of row ordering or whether the parent has an email. The "Cancel request" action appears only on the actor's row.

### Files

- `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`

### Work

1. Replace the `isTransferTarget` reactive expression with:
   ```ts
   $: isTransferTarget = parents.some((p) => p.transferRequestStatus === 'pending' && p.transferRequestRole === 'target');
   ```
2. Replace `pendingTransferRow` with two precise reactive values:
   ```ts
   $: pendingTransferTargetRow = parents.find((p) => p.transferRequestStatus === 'pending' && p.transferRequestRole === 'target') ?? null;
   $: pendingTransferActorRow = parents.find((p) => p.transferRequestStatus === 'pending' && p.transferRequestRole === 'actor') ?? null;
   ```
3. Use `pendingTransferTargetRow` for the approval sheet content (avatar, name, actor name display). Use `pendingTransferTargetRow.transferRequestId` for accept/decline calls.
4. Use `pendingTransferActorRow.transferRequestId` for the "Cancel request" action on the actor's row. Remove the generic `cancelPendingTransfer` that used the ambiguous first-match row; bind the cancel button to `pendingTransferActorRow`.
5. Remove `loadCurrentEmail()` and the `currentEmail` variable — they are no longer needed for transfer target detection. Keep `getAccountConnection` only if another part of the component uses it; otherwise remove the import.
6. In the row template, show the "Cancel request" icon button only on the row where `parent.transferRequestRole === 'actor'` (not on every pending row).

### Acceptance criteria

- When an admin creates a transfer, the admin (actor) row shows "Cancel request" and the target row does not.
- The target parent sees the approval sheet; the actor and any unrelated parent do not.
- A Telegram-only target parent (no email) sees the approval sheet.
- Row ordering in the list does not affect which sheet appears.
- `currentEmail` and `loadCurrentEmail` are removed if no other code path uses them.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte
git commit -m "fix(web): identify transfer target by role field instead of email comparison"
```

---

## REVIEW-003: Allow viewer-permission parents to accept/decline admin transfers

**Status:** DONE

**Priority:** P1
**Depends on:** -

### Outcome

A parent with `viewer` permission who is the target of an admin transfer can accept or decline it. The resource guard no longer blocks them before the service-level ownership check.

### Files

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyParentAccessResource.java`

### Work

1. In `acceptTransferRequest`, `declineTransferRequest`, and `cancelTransferRequest`, replace the guard `if (auth == null || !auth.canEditFamilyData())` with a check that allows any authenticated family parent:
   ```java
   if (auth == null || auth.parentAccountId() == null) {
       return unauthorized();
   }
   ```
2. The service-level checks in `FamilyParentAccessServiceImpl` already verify that the caller is the request target (for accept/decline) or the request actor (for cancel), returning `PARENT_MEMBERSHIP_FORBIDDEN` otherwise. The resource maps that to 403. No further resource-level permission check is needed.
3. Keep `transferAdmin` (create request) guarded by `canManageMemberships()` — only the admin creates requests.

### Acceptance criteria

- A viewer-permission parent who is the transfer target can `POST /api/parents/transfer-requests/{id}/accept` and receives 200.
- A viewer-permission parent who is NOT the target receives 403 from accept/decline.
- A viewer-permission parent calling `cancel` receives 403 (only the actor can cancel).
- An unauthenticated request still receives 401.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -DskipITs test -Dtest='FamilyParentAccessServiceImplTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyParentAccessResource.java
git commit -m "fix(backend): allow viewer parents to accept or decline admin transfer requests"
```

---

## REVIEW-004: Add REST integration tests for transfer request/accept/decline/cancel endpoints

**Status:** DONE

**Priority:** P2
**Depends on:** REVIEW-001, REVIEW-003

### Outcome

The HTTP-level behavior of the four transfer endpoints is tested through the JAX-RS resource, covering auth guards, status codes, and JSON serialization — not just mocked service calls.

### Files

- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyParentAccessResourceTest.java` (new or existing if present)

### Work

1. Add tests using the existing test pattern for `FamilyParentAccessResource` (look at how other resource tests build auth context and call the service — prefer the `@QuarkusTest` or mock-service pattern already used in this repo's resource tests).
2. Cover: (a) admin creates transfer → 200 with `transferRequestStatus: "pending"` and `transferRequestRole: "target"`; (b) non-admin creates transfer → 401/403; (c) target accepts → 200; (d) non-target accepts → 403; (e) viewer target accepts → 200 (proves REVIEW-003); (f) duplicate pending → 409; (g) decline by target → 200; (h) cancel by actor → 200; (i) cancel by non-actor → 403.
3. If the repo does not have a `FamilyParentAccessResourceTest` and resource tests are service-level mocks, follow the pattern of existing resource tests in `family/api/resource/` — search for `*ResourceTest.java` in that package.

### Acceptance criteria

- All nine scenarios above are tested at the HTTP/resource level.
- The test suite passes with `./mvnw verify`.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -DskipITs test -Dtest='FamilyParentAccessResourceTest'
```

### Commit

```bash
git add apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyParentAccessResourceTest.java
git commit -m "test(backend): add resource-level integration tests for admin transfer endpoints"
```

---

## REVIEW-005: Complete ARIA tablist pattern in the wizard Account step

**Status:** DONE

**Priority:** P2
**Depends on:** -

### Outcome

The Email/Telegram tabs in wizard step 2 follow the full WAI-ARIA tablist pattern: tabs have `id` and `aria-controls`, tab panels have `role="tabpanel"`, `id`, and `aria-labelledby`.

---

## REVIEW-006: Restore focus to the triggering button after closing wizard/role-edit/transfer sheets

**Priority:** P2
**Depends on:** -

### Outcome

When a sheet (wizard, role-edit, transfer-select) closes, keyboard focus returns to the button that opened it, not to the top of the page.

### Files

- `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`

### Work

1. Store the triggering element in a variable before opening each sheet:
   ```ts
   let lastFocused: HTMLElement | null = null;
   function openWizard(): void { lastFocused = document.activeElement as HTMLElement; ... }
   function closeWizard(): void { wizardOpen = false; ... lastFocused?.focus(); lastFocused = null; }
   ```
2. Apply the same pattern to `openRoleEdit`/`closeRoleEdit` and `openTransfer`/`closeTransfer`.
3. For the transfer approval sheet (target view), focus the "Принять админство" button on open and restore focus to the previously focused element on close.
4. Use Svelte's `onMount`/`afterUpdate` or a bind to focus the sheet's heading or first button when it opens (the `tabindex="-1"` on the sheet container is already present but no code auto-focuses it).

### Acceptance criteria

- After closing the wizard, focus is on the "Добавить родителя" button.
- After closing the role-edit sheet, focus is on the change-role icon button that opened it.
- After closing the transfer-select sheet, focus is on the transfer icon button.
- Tabbing through the sheet starts at the sheet heading or first interactive control.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte
git commit -m "fix(web): restore focus to triggering button after closing parent sheets"
```

---

## REVIEW-007: Eliminate redundant membership lookups in `enrichWithPendingTransferRequest`

**Priority:** P2
**Depends on:** -

### Outcome

The enrichment path reuses the already-loaded `memberships` list to resolve actor/target names instead of issuing two extra `findByIdOptional` queries.

### Files

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImpl.java`

### Work

1. Pass the full `memberships` list (or a `Map<Integer, FamilyParentMembershipEntity>` built from it) into `enrichWithPendingTransferRequest`.
2. Replace `membershipRepository.findByIdOptional(membershipId)` calls inside `membershipName(Integer, Map)` with a lookup against the passed map. Keep the fallback to `parentAccountRepository.findByIdOptional` for the email resolution (parents are already batch-loaded in `parentsById`).
3. Ensure the `membershipName` helper used by `createTransferRequest` (which does not have a pre-loaded list) still works — extract two overloads or pass `null` to signal "fall back to repository lookup".

### Acceptance criteria

- `listMemberships` with a pending transfer issues zero additional `membershipRepository.findByIdOptional` calls beyond the initial `findByFamilyIdIncludingInactive`.
- The `listMemberships_pendingTransfer_enrichesActorAndTargetRows` test still passes.
- `./mvnw verify` passes.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -DskipITs test -Dtest='FamilyParentAccessServiceImplTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImpl.java
git commit -m "perf(backend): reuse loaded memberships in transfer request enrichment"
```

---

## REVIEW-008: Include Telegram identity in transfer mutation response DTOs

**Priority:** P3
**Depends on:** -

### Outcome

The DTO returned by `createTransferRequest`, `acceptTransferRequest`, `declineTransferRequest`, and `cancelTransferRequest` includes the parent's Telegram identity fields (`telegramUserId`, `telegramUsername`, `telegramDisplayName`), consistent with the list endpoint's `toDto` output.

### Files

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImpl.java`

### Work

1. In `createTransferRequest`, `acceptTransferRequest`, `declineTransferRequest`, and `cancelTransferRequest`, look up the `TelegramIdentityEntity` for the returned membership's parent account using the existing `telegramIdentityRepository` (follow the same pattern as `listMemberships`). Pass it to `toDto` instead of `null`.
2. Alternatively, refactor `toTransferDto` to accept an optional `TelegramIdentityEntity` and include its fields, then call `toTransferDto` from all four mutation methods instead of `toDto`.

### Acceptance criteria

- `POST /api/parents/{id}/transfer-admin` response includes `telegramUsername` and `telegramDisplayName` when the target has a linked Telegram account.
- `POST /api/parents/transfer-requests/{id}/accept` response includes the new admin's Telegram identity.
- `./mvnw verify` passes.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -DskipITs test -Dtest='FamilyParentAccessServiceImplTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImpl.java
git commit -m "fix(backend): include telegram identity in transfer mutation responses"
```

---

## Rejected observations

- **`transferAdmin` returns 200 instead of 201 for creating a resource.** The backlog spec explicitly says "returns 200 with the target DTO". Not a defect.
- **`listMemberships` early-returns when `memberships.isEmpty()` without checking for pending transfers.** A pending transfer request requires both an actor and a target membership, so this branch is unreachable when a transfer exists. Not a real issue.
- **Transfer approval sheet backdrop has `on:click={() => {}}` (no-op).** This is intentional — the target must explicitly accept or decline a critical permission change. Not a defect.
- **`cancelTransferRequest` resource guard uses `canEditFamilyData()` instead of `canManageMemberships()`.** The service verifies the caller is the actor (a `family_admin`), so a non-admin editor would get 403 from the service. The imprecise guard does not create a security hole. Minor, not worth a task.
- **TASK-004 (profile detail card on row tap) is still TODO.** This is an incomplete backlog item, not a review finding. The wizard step-3 email preview reuses the `.preview` card shape as planned.
- **`.account-grid` has no `@media (max-width: 390px)` breakpoint.** The `minmax(0,1fr)` columns prevent overflow; the two boxes are narrow but not broken on 390px. Minor visual, not a task.