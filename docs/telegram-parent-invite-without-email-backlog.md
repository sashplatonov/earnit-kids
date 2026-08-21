# Telegram Parent Invite Without Email - Implementation Backlog

## Goal

An administrator can create a Telegram invitation for another parent by entering that
parent's name. The recipient accepts it inside the Telegram Mini App without entering
an email address. The family parent list then shows the administrator-provided name and
the verified Telegram identity, while the existing email-based invitation path keeps its
current behaviour.

## Architectural decisions

- A Telegram parent invitation remains the one-time, expiring, server-side source of
  truth. It must persist the administrator-provided parent name before the link is
  shared; no name from an unsigned browser payload is trusted.
- A parent invited only through Telegram has a `ParentAccountEntity` without an email.
  The authenticated parent principal must therefore be the parent-account id carried by
  the signed session, not a fabricated email address. Existing sessions and email/Google
  login remain compatible during the transition by retaining the email claim and
  resolving the account id for legacy cookies.
- The name given by the administrator belongs to `family_parent_memberships`, because an
  account can belong to more than one family. Telegram presentation data belongs to the
  active `telegram_identities` record and is taken only from Telegram-verified init data.
  Do not put either value in a client-only store or synthesize an email such as
  `telegram-<id>@...`.
- `GET /api/parents` remains the sole parent-list contract. Extend its existing DTO and
  frontend type instead of creating a Telegram-only list endpoint or a parallel parent
  store. Legacy email memberships continue to show their email when no display name or
  Telegram identity is available.
- The existing email invite choice is explicitly out of scope for removal. The requested
  removal applies to the email field and email-oriented copy in the Telegram-link create
  and accept flow.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-041 | P1 | - | Makes Telegram-only parent identity and persisted presentation fields possible. |
| 2 | TASK-042 | P1 | TASK-041 | Replaces email-bound Telegram invite acceptance with a signed Telegram principal. |
| 3 | TASK-043 | P1 | TASK-041, TASK-042 | Exposes the persisted parent and Telegram labels through the canonical list. |
| 4 | TASK-044 | P1 | TASK-042, TASK-043 | Removes the recipient email UI and adds the administrator name step. |
| 5 | TASK-045 | P2 | TASK-044 | Proves the complete mobile Mini App flow and preserves regression gates. |

## TASK-041: Persist Telegram-only parent and presentation data

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V40__add_telegram_parent_profiles.sql`.
- Create `apps/backend/src/test/resources/db/migration/V40__add_telegram_parent_profiles.sql`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ParentAccountEntity.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/FamilyParentMembershipEntity.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramIdentityEntity.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramParentInvitationEntity.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramIdentityRepository.java`.

**Goal:**

Store the family-specific parent name, the verified Telegram presentation data, and an
email-less parent account without weakening existing email-account uniqueness.

### Outcome

An accepted Telegram invitation can create a parent account that has no email, has a
family-specific name, and has a linked Telegram identity that can be displayed later.

### Architectural decision

The migration is additive: make `parent_accounts.email` nullable only to support a new
Telegram-only account, keep its unique constraint, add a nullable membership display-name
column, add the invitation name column, and add nullable Telegram identity presentation
columns. Existing accounts keep their email unchanged; existing rows are not rewritten
and retain their email-based display fallback.

### Work

1. Add matching PostgreSQL and H2 test migrations after V39; preserve Flyway history and
   keep the production/test chains aligned.
2. Add entity mappings for the new columns and repository lookup/projection support for
   active parent Telegram identities by family and parent-account id, avoiding one query
   per parent list row.
3. Define the stored Telegram presentation values from the signed Telegram `user` object:
   keep the numeric Telegram user id as the authoritative binding and retain a safe,
   nullable username/display label only for presentation. Do not accept these values from
   request JSON.
4. Ensure an existing email parent and all pre-migration memberships remain valid with
   absent display-name/Telegram presentation values.

### Acceptance criteria

- Flyway applies V40 on PostgreSQL and the H2 test schema without editing any merged
  migration.
- `parent_accounts` allows a Telegram-only parent without an email but still rejects two
  non-null accounts with the same email.
- Existing parent accounts with an email retain that email and their current email-based
  behavior after the migration.
- A membership can retain the administrator-entered name independently of the same
  account's memberships in other families.
- Telegram profile fields are nullable presentation data; authorization continues to use
  the existing verified numeric Telegram user id and active identity state.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest=TelegramParentInvitationServiceImplTest,FamilyParentAccessServiceImplTest
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration/V40__add_telegram_parent_profiles.sql apps/backend/src/test/resources/db/migration/V40__add_telegram_parent_profiles.sql apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ParentAccountEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/FamilyParentMembershipEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramIdentityEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramParentInvitationEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramIdentityRepository.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImplTest.java
git commit -m "feat(backend): Persist Telegram parent profiles"
```

## TASK-042: Accept a named Telegram invitation without email

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-041

**Files:**

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/CreateTelegramParentInviteRequest.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/ParentInviteAcceptRequest.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramParentInviteResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramParentInviteAcceptResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImpl.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramInitDataVerifier.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/AuthContext.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/CookieBuilder.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyParentAccessResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImpl.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/account/AccountResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/account/AccountService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/account/AccountServiceImpl.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramAccountConnectionResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramAccountConnectionService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramAccountConnectionServiceImpl.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramParentInviteAcceptResourceTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramInitDataVerifierTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImplTest.java`.

**Goal:**

Make the Telegram invitation create endpoint require the invited parent's name and make
the accept endpoint require only the invite token and signed Telegram init data.

### Outcome

After a valid one-time link opens in Telegram, the recipient is added as an editor and
is authenticated without ever entering or receiving an email address.

### Architectural decision

`POST /api/telegram/parents/invite` evolves from an empty JSON request to a validated
name request, while `POST /api/telegram/parents/invite/accept` removes `email` from its
request. The service returns the accepted parent-account identity needed to mint the
session; it must not recover identity from a synthetic email. The session adds a signed
parent-account id claim and preserves email-claim support for existing cookies.

### Work

1. Validate and normalize the non-blank parent name at invite creation, persist it with
   the invitation, and keep the existing admin authorization, feature gate, 15-minute
   TTL, digest-only token storage, and launch URL format.
2. Parse the already signed Telegram `user` object in `TelegramInitDataVerifier` to
   obtain the presentation fields along with its verified numeric id; update all verifier
   callers and test fixtures for the expanded verified result.
3. On acceptance, keep the existing lock, expiry/revocation/consumption checks and
   active-Telegram-account collision check. Create an email-less account only when the
   Telegram identity is not already linked, persist the invitation name on its family
   membership, link the verified Telegram identity, and consume the invitation in the
   same transaction.
4. Add parent-account id to the signed auth payload and `AuthContext`; update the
   membership-management, account-connection, and account-settings service boundaries to
   identify the acting parent by that id. For a legacy cookie with no id, resolve it from
   its existing email once; do not reject logged-in email parents solely because their
   cookie predates this change.
5. For a Telegram-only parent, expose account connection as Telegram-linked and make
   email-specific actions return a clear normal application error rather than dereference
   a null email. Keep email/Google registration and email invitation semantics unchanged.
6. Update resource, service, verifier, and cookie tests for valid acceptance, blank name,
   invalid/reused/expired invitation, already-linked Telegram user, legacy email session,
   and email-less account boundaries.

### Acceptance criteria

- An authenticated family admin receives `400` for a blank/invalid parent name and `200`
  with the existing launch URL for a valid name; a non-admin remains unauthorized and a
  disabled Mini App remains `404`.
- The accept request contains only `token` and `initData`; a valid signed Telegram user
  can accept once without an email field, receives an editor session, and reaches the
  family as that exact parent account.
- Replaying, expiring, revoking, or using the link from an already active Telegram account
  fails without creating a membership or consuming a different invitation.
- Existing email-based parent accounts, their sessions, email invitation endpoint, and
  ownership checks retain their current behavior.
- Neither responses, cookies, logs, nor persistence use a fabricated email to represent a
  Telegram-only parent.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest=TelegramInitDataVerifierTest,TelegramParentInvitationServiceImplTest,TelegramParentInviteAcceptResourceTest,FamilyParentAccessServiceImplTest,AccountServiceImplTest,TelegramAccountConnectionServiceImplTest
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/CreateTelegramParentInviteRequest.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/ParentInviteAcceptRequest.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramParentInviteResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramParentInviteAcceptResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramInitDataVerifier.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/AuthContext.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/CookieBuilder.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyParentAccessResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/account/AccountResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/account/AccountService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/account/AccountServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramAccountConnectionResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramAccountConnectionService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramAccountConnectionServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramParentInviteAcceptResourceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramInitDataVerifierTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/account/AccountServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramAccountConnectionServiceImplTest.java
git commit -m "feat(backend): Link invited parents by Telegram"
```

## TASK-043: Return named Telegram parents in the canonical membership list

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-041, TASK-042

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ParentMembershipDto.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImpl.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyParentAccessResource.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImplTest.java`.

**Goal:**

Extend the existing parent-membership response so the Mini App can show a parent name
and their Telegram identity without exposing or depending on an email for Telegram-only
accounts.

### Outcome

The same `/api/parents` request returns a deterministic record for every parent: legacy
email display data where applicable and named Telegram identity data for Telegram-linked
parents.

### Architectural decision

`ParentMembershipDto` is the single response model. It carries optional presentation
fields for the membership name and Telegram identity in addition to the existing id,
permission, and status. The family service resolves all parent accounts and relevant
active Telegram identities in bounded collection queries before building the DTOs.

### Work

1. Extend the response DTO and service mapping with nullable display-name and Telegram
   identity fields, keeping the existing field names/semantics intact for current clients.
2. Join the active Telegram parent identity to the matching parent account and family;
   never associate a Telegram user from another family or an inactive identity.
3. Preserve the current membership ordering and inactive rows. For an email-only legacy
   membership, return its email and absent Telegram fields; for a Telegram-only invite,
   return the invitation name and verified Telegram label with no email requirement.
4. Cover mixed legacy and Telegram-only lists, identity absence/inactivation, and a
   cross-family account/identity mismatch in service/resource tests.

### Acceptance criteria

- `GET /api/parents` remains family-admin protected and does not reveal another family's
  parent or Telegram identity.
- A Telegram-invited parent response contains the admin-entered name and their active
  verified Telegram label/user id, while `email` may be null.
- A legacy email parent continues to receive the same email value and permissions/status
  it received before this change.
- Listing N parents does not issue an N+1 Telegram identity lookup.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest=FamilyParentAccessServiceImplTest
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ParentMembershipDto.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyParentAccessResource.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImplTest.java
git commit -m "feat(backend): Expose Telegram parent profiles"
```

## TASK-044: Redesign the Telegram parent invitation Mini App flow

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-042, TASK-043

**Files:**

- Modify `apps/web/src/lib/services/api.ts`.
- Modify `apps/web/src/lib/types/auth.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentAccess.svelte`.
- Modify `apps/web/src/routes/telegram/+page.svelte`.
- Modify `apps/web/src/lib/i18n/messages/en/app.ts`.
- Modify `apps/web/src/lib/i18n/messages/ru/app.ts`.
- Modify `apps/web/tests/e2e/telegram-auth.spec.ts`.

**Goal:**

Let an administrator name the parent before creating a Telegram link, remove email
input/copy from the Telegram recipient screen, and show name plus Telegram identity in
the parent list.

### Outcome

The Telegram route needs one affirmative acceptance action only; the parent-access sheet
asks the administrator for a name before it creates/copies a link and subsequently
renders the linked parent unambiguously.

### Architectural decision

The existing `TelegramParentAccess.svelte` bottom-sheet state machine and `api.ts`
service remain the frontend owners. Change their request/response types in lockstep with
the canonical backend contract; do not add direct fetches, localStorage state, or a
second parent-list resolver.

### Work

1. Change `createParentTelegramInvite` to send the trimmed parent name and change
   `acceptParentTelegramInvite` to send only token and Telegram init data. Update the
   parent membership type for optional email, membership name, and Telegram label.
2. Replace the Telegram-link creation screen's implicit link generation with a labelled,
   required parent-name input and a create-link action. Retain the email invitation choice
   and its existing email input untouched.
3. Remove the email input, email validation, placeholder, and email-oriented explanatory
   text from the `parent-invite` route state. Keep loading, disabled-submit, failure, and
   retry behavior; on success rerun the existing auth exchange.
4. Render each parent with the administrator-provided name as the primary label when
   present and a separate Telegram secondary label/badge when linked. Fall back to the
   existing email display for legacy parents, and update destructive/transfer confirmation
   copy to use the same safe display label rather than interpolate a nullable email.
5. Add complete RU and EN copy for the new name field, validation, Telegram-only
   acceptance, Telegram identity label, and fallback display. Remove only stale
   Telegram-invite email copy, not general account-email settings copy.
6. Keep controls keyboard reachable with visible focus, make primary mobile actions at
   least 44 by 44 CSS pixels, prevent text/identity overflow at a 320px viewport, and
   expose validation/errors with associated labels and alert semantics.

### Acceptance criteria

- A family admin cannot create a Telegram link until a non-blank parent name is entered;
  the request body contains that name and no recipient email.
- A recipient opening `pi_…` inside Telegram sees no email field or email request and can
  accept using the signed Telegram Mini App data alone.
- After acceptance and reload, the access sheet shows the supplied parent name together
  with a Telegram identity label; it does not display a fabricated email.
- Existing email invitation UI and email-only parent display remain functional.
- At 320px and desktop widths, all inputs, copy, and action buttons are reachable without
  horizontal scrolling; keyboard focus is visible and errors are announced.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- telegram-auth.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/services/api.ts apps/web/src/lib/types/auth.ts apps/web/src/lib/components/telegram/TelegramParentAccess.svelte apps/web/src/routes/telegram/+page.svelte apps/web/src/lib/i18n/messages/en/app.ts apps/web/src/lib/i18n/messages/ru/app.ts apps/web/tests/e2e/telegram-auth.spec.ts
git commit -m "feat(web): Invite parents through Telegram"
```

Implemented in this session. Verification: `npm run lint`, `npm run test`,
`npm run build`, and the focused `telegram-auth.spec.ts` parent-invitation test
passed. The full E2E file was also attempted; its preview-server run is blocked by
the existing `/public/index.html`/backend session setup, while the default run has
no listener on port 5001.

## TASK-045: Prove cross-layer Telegram parent invitation behavior

**Status:** DONE
**Priority:** P2
**Depends on:** TASK-044

**Files:**

- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java`.
- Modify `apps/web/tests/e2e/telegram-auth.spec.ts`.
- Modify `apps/web/tests/e2e/helpers.ts` only if the existing shared fixture needs a
  Telegram-parent setup helper; otherwise do not change it.

**Goal:**

Prove that the persisted backend contract and the Mini App use the same Telegram-only
parent identity across an invitation, acceptance, fresh session, and parent-list reload.

### Outcome

Automated tests catch a reintroduction of recipient email collection, an unbound Telegram
user, or an incorrect name/identity shown in the admin list.

### Architectural decision

Use the existing cross-channel backend integration test and Telegram Playwright fixture.
Mock Telegram init data only at the browser boundary; the backend test must exercise the
verified identity, one-time invitation semantics, persisted membership, and canonical
list projection together.

### Work

1. Add an integration scenario that creates a named invitation as one family admin,
   accepts it as a verified Telegram user, asserts the resulting editor membership and
   session principal, then lists that family and verifies the name/Telegram fields.
2. Assert rejection when a different family tries to view or operate on the membership,
   when the token is reused, and when the Telegram identity is already linked.
3. Extend Playwright with a 320px Telegram SDK fixture that asserts the create request
   body, absence of an email input on acceptance, one accept request containing only
   token/initData, post-accept auth handoff, and no horizontal overflow.
4. Run full configured quality gates after the code and tests are complete; record local
   test/build/browser evidence separately from remote CI or real Telegram-client proof.

### Acceptance criteria

- The integration test demonstrates exactly one active membership/identity binding after
  acceptance and preserves the invitation's parent name through a fresh list read.
- The browser test fails if an email input or `email` request property returns to the
  Telegram invite recipient path.
- The browser test verifies the mobile viewport geometry and visible parent name plus
  Telegram identity without claiming real Telegram-client or deployed-webhook proof.
- Backend verify, web lint/test/build, and targeted Playwright pass without lint/type or
  static-analysis suppressions.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- telegram-auth.spec.ts
git diff --check
```

### Commit

```bash
git add apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java apps/web/tests/e2e/telegram-auth.spec.ts apps/web/tests/e2e/helpers.ts
git commit -m "test(telegram): Cover parent invite without email"
```
