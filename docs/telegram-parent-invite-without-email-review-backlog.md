# Telegram Parent Invite Without Email - Review Remediation Backlog

## Goal

Make the completed Telegram-only parent invitation flow fulfil its intended contract:
persist and show Telegram-verified presentation data, and allow a Telegram-only parent
who has been made family admin to manage memberships without requiring an email address.

## Architectural decisions

- **TASK-046 — Root cause and requirement:** `TelegramInitDataVerifier` correctly extracts
  the signed `username` and display name, but `TelegramParentInvitationServiceImpl.accept`
  calls the legacy `linkParent` overload. `TelegramIdentityServiceImpl` therefore persists
  neither value, so `GET /api/parents` returns null Telegram labels despite the completed
  backlog's TASK-041/043/045 requirement to retain and display verified Telegram identity
  data. The invitation service must pass the verified values through the canonical identity
  service; no client-provided profile fields or second presentation store may be introduced.
- **TASK-047 — Root cause and requirement:** the new signed `parentAccountId` claim is
  available in `AuthContext`, but parent-membership resources and service methods still pass
  `auth.email()` and resolve the actor by email. A Telegram-only parent has no email, so a
  legitimately promoted family admin cannot transfer admin rights or remove another admin.
  Membership authorization must use the canonical account id, with one explicit email
  fallback only for legacy cookies that lack the claim.
- The existing `ParentMembershipDto` and `GET /api/parents` remain the only parent-list
  contract. Preserve email-based invitations, email-account sessions, and old cookies; do
  not restore email collection to the Telegram invite acceptance route.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-046 | P1 | - | Restores the verified identity data required by the canonical list and Mini App. |
| 2 | TASK-047 | P1 | - | Completes the account-id authorization migration for email-less admins. |

## TASK-046: Persist signed Telegram profile data for parent invitations

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImpl.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityServiceImpl.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImplTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java`.
- Modify `apps/web/tests/e2e/telegram-auth.spec.ts`.

**Goal:**

An accepted Telegram invitation persists the username and display name supplied by the
validated Telegram init data, so the existing parent list can present an independently
verified Telegram identity after a fresh read.

### Outcome

For a signed Telegram user with `username=maria_example` and name `Maria Example`, the
identity row and canonical `GET /api/parents` projection retain those values; the Mini App
shows the name and an unambiguous Telegram label after reload.

### Architectural decision

Keep `TelegramInitDataVerifier` as the sole parser of signed init data. Extend the existing
identity-link service's profile-aware call path and persist its nullable values on
`telegram_identities`; do not derive them from the invitation name or accept them in request
JSON.

### Work

1. Replace the invitation acceptance call to the legacy link overload with the existing
   profile-aware identity-service contract, passing only `VerifiedInitData` values.
2. Implement that contract in `TelegramIdentityServiceImpl` so a newly linked parent stores
   the nullable Telegram username and display name along with the verified numeric id.
3. Preserve existing parent-link callers and identity lifecycle behaviour; inactive or
   legacy identities may legitimately have no presentation fields.
4. Correct the integration fixture, which currently signs profile values but asserts null
   projections, and add service/browser regression assertions for both the stored values and
   their parent-list rendering.

### Acceptance criteria

- Accepting a valid named Telegram invite with signed username and first/last name stores
  those values on the active parent identity without adding an email to the account.
- A fresh `GET /api/parents` result contains the invitation display name plus the verified
  Telegram username/display label for that parent and does not expose a profile from another
  family or inactive identity.
- The Mini App's 320px parent-access flow renders the supplied parent name and Telegram
  identity label after its canonical list reload.
- A signed Telegram user without username or display fields is accepted and produces null
  optional presentation fields without weakening numeric-id authorization.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest=TelegramParentInvitationServiceImplTest,TelegramCrossChannelIntegrationTest,FamilyParentAccessServiceImplTest
cd apps/web && npm run test:e2e -- telegram-auth.spec.ts
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java apps/web/tests/e2e/telegram-auth.spec.ts docs/telegram-parent-invite-without-email-review-backlog.md
git commit -m "fix(telegram): Persist invited parent profiles"
```

## TASK-047: Authorize membership management by parent account id

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyParentAccessResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImpl.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImplTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/family/FamilyParentAccessResourceTest.java`.

**Goal:**

A parent authenticated through Telegram-only acceptance can perform the same permitted
membership-management actions as an email parent after receiving family-admin permission.

### Outcome

The DELETE and transfer-admin ownership checks resolve the acting membership by the signed
parent-account id, rather than treating a null email as an unauthorized actor.

### Architectural decision

Use `AuthContext.parentAccountId` as the primary actor identity at the resource-to-service
boundary. For a legacy signed cookie without that claim, resolve the email to an account id
once in the service before ownership comparison. Do not synthesize an email or duplicate
authorization rules in the resource.

### Work

1. Change the membership mutation service contract and resource callers to provide the
   optional signed parent-account id instead of `auth.email()` for actor-sensitive actions.
2. Centralize actor membership resolution by account id and family; use the one legacy email
   fallback only when the account id is absent.
3. Apply that resolver to admin-transfer and protected-admin removal checks, preserving the
   existing last-admin and cross-family safeguards and normal error mapping.
4. Add service/resource coverage for a Telegram-only family admin, an email-based legacy
   session without `parentAccountId`, a non-member account, and an account from another family.

### Acceptance criteria

- A Telegram-only parent account with an active `family_admin` membership can transfer admin
  rights and perform permitted protected-membership operations without an email claim.
- The same account cannot operate on a membership from a different family, remove another
  admin contrary to the existing policy, or bypass the last-admin safeguard.
- An email parent whose pre-change cookie has no `parentAccountId` retains the existing
  membership-management behaviour through the explicit compatibility fallback.
- Resources continue to reject unauthenticated, editor, viewer, and malformed actor contexts
  before service mutation.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest=FamilyParentAccessServiceImplTest,FamilyParentAccessResourceTest
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyParentAccessResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/family/FamilyParentAccessResourceTest.java docs/telegram-parent-invite-without-email-review-backlog.md
git commit -m "fix(backend): Authorize Telegram-only parent admins"
```

### Verification outcome

- Targeted service and resource tests passed (59 tests).
- Full backend `verify` passed: 584 tests, JaCoCo, PMD, SpotBugs, and Quarkus build.

## Rejected observations

- The current target-test suite passes, but it does not prove the required Telegram profile
  projection: the cross-channel test signs a username/display name and then explicitly
  expects both projected fields to be null. This is regression-coverage evidence for
  TASK-046, not a separate test-gate defect.
- The parent-access sheet's primary buttons are `2.75rem` (44 CSS pixels), use local visible
  keyboard focus styling, and the existing focused Playwright case checks 320px horizontal
  overflow. No additional responsive or primary-control accessibility defect was confirmed
  from the reviewed changes.
