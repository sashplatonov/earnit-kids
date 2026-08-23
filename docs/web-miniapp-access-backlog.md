# Web and Telegram Mini App Access - Implementation Backlog

## Goal

Make the EarnIt Kids product available as a normal responsive web application and as a Telegram Mini App, while keeping one product experience and one authorization model. Parents sign in with Google and can invite another parent by email; children receive revocable magic links. Installed PWA clients can opt into browser notifications.

## Architectural decisions

- `/telegram` remains the Telegram host entrypoint. `/workspace` is the new authenticated browser entrypoint; the previously removed `/app` route must not be restored or aliased.
- The shared Svelte product boundary is `src/lib/features/workspace/`: role resolution, parent/child workspace shells, feature views, and shared UI primitives live there. `src/lib/features/telegram/` contains only Telegram SDK/bootstrap and host adaptation. Existing `src/lib/components/telegram/` is migrated incrementally into these names; no parallel Web and Telegram feature implementations are allowed.
- Existing session cookies, `AuthService`, `AuthGoogleResource`, and the API proxy remain the only parent-session contract. Google OAuth is extended only to preserve a validated post-login invitation continuation; it is not duplicated in a browser-only auth flow.
- An email parent invitation is a persisted, single-use, expiring invitation. A membership becomes active only after the invited Google identity accepts it. The current immediate `POST /api/parents` membership creation must be replaced by this flow, with an explicit migration strategy for already-active memberships.
- Every parent/child invitation token has at least 128 bits of CSPRNG entropy (256 bits preferred), is stored as a deterministic `HMAC-SHA-256` digest using a separately configured pepper, and is never reversibly encrypted. The raw token is generated only for its initial delivery and is absent from database records, logs, audit events, list responses, redirects, and client telemetry.
- Credential-bearing links are consumed by a server endpoint and immediately redirected to a token-free route. `Cache-Control: no-store` is defence in depth, not a substitute for removing credentials from browser history, referrers, analytics, and client-side routing.
- Child access uses the same cookie transport but a dedicated, opaque, single-use and revocable child invitation token. Successful child authentication rotates the complete credential set, including a per-issuance session identifier, clears every prior role/family/child cookie, and issues only the new child context. It must not reuse Telegram invite tokens.
- A parent identity can have several memberships. The authenticated principal and membership list determine the active family stored in the signed auth context; a UI-selected or request-supplied family identifier never grants access. Every family-scoped endpoint independently checks the caller's membership and permission for its target family.
- Browser push subscriptions and delivery attempts have their own persistence and service boundary. `FamilyNotificationPreferenceEntity` remains the per-family preference source of truth and `TelegramDeliveryEntity` remains Telegram-specific; neither is repurposed for web push. Web Push is planned and sent asynchronously from committed `ApplicationOutboxEventEntity` records, with a database idempotency constraint per event/subscription/transport.
- Security-sensitive lifecycle changes use a shared non-Telegram `SecurityAuditEvent` contract. It records event type, actor/target references, family, result/reason, timestamp, correlation metadata, and optional privacy-preserving network metadata; it never records a token, OAuth code, full magic URL, VAPID private key, or PushSubscription secret.
- Browser and Telegram rendering use the same role, permissions, data services, i18n keys, and workspace components. Host-specific safe-area, theme, and launch behavior stays outside the shared feature modules.
- All invitation redirects are same-origin allow-listed relative paths. Raw tokens are accepted only at the consuming endpoint, stored only as one-way hashes, excluded from logs, and responses use `Cache-Control: no-store`.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-WM-001 | P1 | - | Establishes the durable shared-module and route boundary before moving UI. |
| 2 | TASK-WM-002 | P0 | - | Adds atomic parent-invitation, audit, and membership data invariants before an email can be sent. |
| 3 | TASK-WM-003 | P1 | TASK-WM-002 | Makes the email invitation and Google acceptance secure and observable. |
| 4 | TASK-WM-004 | P0 | - | Replaces long-lived child-link sharing with invitation-grade, session-rotating magic links. |
| 5 | TASK-WM-005 | P1 | TASK-WM-001, TASK-WM-003, TASK-WM-004 | Delivers the browser entry, invitations, and shared parent/child UI. |
| 6 | TASK-WM-006 | P1 | TASK-WM-002 | Adds persisted browser-push subscriptions and committed-outbox delivery independently of host UI. |
| 7 | TASK-WM-007 | P1 | TASK-WM-005, TASK-WM-006 | Connects PWA lifecycle and consent UI to the shared workspace. |
| 8 | TASK-WM-008 | P2 | TASK-WM-005, TASK-WM-007 | Proves host parity, mobile geometry, and offline/push lifecycle. |
| 9 | TASK-WM-009 | P2 | TASK-WM-008 | Documents production configuration and completes cross-layer quality gates. |

## TASK-WM-001: Establish the shared workspace module boundary

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Create the semantic Svelte module boundary for the product workspace and a normal-browser route. Migrate only the role resolver and parent/child shell composition first; do not redesign every existing feature in this task.

**Files:**

- Create `apps/web/src/routes/workspace/+page.svelte`.
- Create `apps/web/src/lib/features/workspace/WorkspaceRoleResolver.svelte`.
- Create `apps/web/src/lib/features/workspace/ParentWorkspaceShell.svelte`.
- Create `apps/web/src/lib/features/workspace/ChildWorkspaceShell.svelte`.
- Create `apps/web/src/lib/features/telegram/TelegramWorkspaceBootstrap.ts`.
- Modify `apps/web/src/routes/telegram/+page.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramRoleResolver.svelte`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/application/auth/AuthMembershipService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthResource.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/application/auth/AuthServiceImplTest.java`.
- Search anchor: `TelegramParentShell` and `TelegramChildShell` in `apps/web/src/lib/components/telegram/` identifies the first composition points to move or wrap.

**Goal:**

An authenticated browser can open `/workspace`; a Telegram user can open `/telegram`; both reach the same parent or child workspace component tree after their host-specific authentication succeeds.

### Outcome

The code clearly distinguishes the product workspace from its Telegram adapter. Existing Telegram launch, role resolution, and active views remain functional while browser access gains an explicit entrypoint.

### Architectural decision

`WorkspaceRoleResolver` owns product-role composition, not authorization. The `/telegram` page owns Telegram init-data exchange and passes a verified session into the shared resolver; `/workspace` relies on the normal server session. The backend's signed auth context owns the active family after membership resolution; client state cannot select or broaden it. Do not copy shells or introduce an `/app` compatibility route.

### Required changes

1. Define the `features/workspace` and `features/telegram` import direction and move/wrap only the shell-level composition necessary to make it real.
2. Add server-side protection for `/workspace` using the existing session/load pattern, redirecting unauthenticated browser users to localized `/login` with a safe continuation.
3. Keep Telegram SDK access, `start_param`, safe-area setup, and unavailable/unlinked handling in the Telegram adapter.
4. Harden the existing multi-family selection handoff: selection is bound to the authenticated/login continuation principal, never a freely trusted email field, and a selected family is emitted only after active membership and permission checks.
5. Make workspace bootstrap read the server-selected active family and available memberships as presentation data only; family-scoped API requests remain independently authorized server-side.
6. Rename imports and tests at the moved boundary so `Telegram*` means a Telegram-only concern, not a general product screen.
7. Add focused unit coverage for host-to-workspace role routing, multi-family permission changes, and a browser route smoke test.

### Out of scope

- Email or child invitation behavior.
- Reworking individual task, reward, history, or family feature views.
- PWA, push, Capacitor, or visual redesign.

### Acceptance criteria

- A normal authenticated parent and child session can load `/workspace` and receive the correct workspace role shell.
- An unauthenticated request to `/workspace` redirects only to a local login path and returns to `/workspace` after login.
- Telegram `/telegram` still requires verified Telegram init data; visiting it in a normal browser does not silently grant a session.
- A parent with memberships in families A and B receives an active context only through the authenticated selection flow; changing from admin in A to viewer in B cannot retain A's permission or authorize an A/B cross-family operation.
- A request body or browser state containing a different `familyId` or another parent's email cannot change the server's active context or authorize a target family.
- No route, test, or navigation restores `/app` or `/ru/app`.
- Shared shell modules have no direct Telegram WebApp SDK dependency; Telegram-only imports are confined to `features/telegram` or the `/telegram` route.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=AuthServiceImplTest test && cd ../web && npm run lint && npm run test -- tests/unit/workspaceRoleResolver.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts tests/e2e/telegram-auth.spec.ts
```

### Commit

```bash
git add apps/web/src/routes/workspace apps/web/src/routes/telegram/+page.svelte apps/web/src/lib/features apps/web/src/lib/components/telegram/TelegramRoleResolver.svelte apps/web/tests apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/application/auth/AuthMembershipService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthResource.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/application/auth/AuthServiceImplTest.java
git commit -m "refactor(web): establish shared workspace boundary"
```

## TASK-WM-002: Persist a secure email parent-invitation lifecycle

**Status:** DONE
**Priority:** P0
**Depends on:** -

**Exact scope:**

Introduce the domain storage and service contract for an email-addressed parent invitation, replacing immediate activation for new `POST /api/parents` additions.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V42__add_parent_email_invitations.sql` (use the next Flyway number if another migration lands first).
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/invitation/ParentEmailInvitationEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/invitation/ParentEmailInvitationRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/security/SecurityAuditEventEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/security/SecurityAuditEventRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/security/SecurityAuditWriter.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImpl.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/membership/FamilyParentMembershipEntity.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/response/ParentMembershipDto.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImplTest.java`.

**Goal:**

Creating a parent invitation persists a pending, permission-scoped invitation without granting the email address family access until it is redeemed by the intended Google identity.

### Outcome

The database can safely represent pending, accepted, expired, revoked, and superseded email invitations, and the parent-access projection explains this state to an authorized family admin.

### Architectural decision

The invitation table is the source of truth for the unaccepted invite; `FamilyParentMembershipEntity` remains the source of truth for actual membership. Store only the HMAC token digest, expiry, creator, intended normalized email, permission, and consumption/revocation timestamps. The redemption transaction performs one conditional database update (`consumed_at IS NULL`, not revoked/superseded, unexpired) and proceeds only when exactly one row is affected; a database unique constraint on the real membership identity/family pair is the final concurrency safety net. Existing active memberships stay valid without a backfill-generated invitation.

### Required changes

1. Add the migration, entity, repository queries, indexes, and database constraints needed to prevent duplicate active invitations for the same family/email and to support expiry/revocation lookups; use a CSPRNG 256-bit token and HMAC-SHA-256 with a configured, separately rotatable pepper.
2. Add the membership unique constraint using the actual persistent family and parent-account identity columns, then map a constraint race to the same deterministic already-member result as the service precheck.
3. Change the parent-access service from immediate membership provisioning to an invitation-creation operation for new email addresses, retaining authorization and permission validation.
4. Define deterministic conflict behavior for primary admin email, already-active membership, a still-valid matching invite, expired/revoked tokens, conditional-consume affected-row zero, and a membership unique-constraint race.
5. Create the shared `platform/security` audit writer and record parent invitation creation, resend/revoke, accepted, and rejected outcomes with reason codes and request correlation metadata. Do not extend `TelegramSecurityAuditEventEntity`, which remains Telegram-only.
6. Extend the authorized parent-access response with an invitation status that never exposes the raw token or a credential.
7. Cover PostgreSQL and H2-compatible migration behavior plus conditional-update, concurrent-redemption, and database-constraint service cases.

### Out of scope

- Sending email or adding SMTP/provider credentials.
- Google OAuth callback changes and UI acceptance.
- Telegram-only parent invitations, which retain their existing flow.

### Acceptance criteria

- A family admin can create an invitation for a syntactically valid email and permitted role; the result is pending and does not create an active membership.
- The raw invitation token is generated only for delivery and is not persisted, returned by list endpoints, or logged.
- A token has 256 bits of CSPRNG entropy; lookup uses the configured HMAC digest, never reversible encryption or a password hash intended for user passwords.
- An existing active parent, primary admin, or duplicate valid invitation is rejected with stable API error codes.
- An expired/revoked/superseded invitation cannot be accepted, and only one parallel redemption can create a membership.
- Two valid concurrent redemption attempts result in exactly one consumed invitation and one membership, including when they reach the final membership insert simultaneously.
- Every create/revoke/reject/accept transition emits a safe shared security audit event with a stable reason code and no credential material.
- Existing active email and Telegram parent memberships continue to appear in `GET /api/parents` with their current authorization semantics.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=FamilyParentAccessServiceImplTest test
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration apps/backend/src/main/java/com/sashplatonov/earnit/kids/family apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/security apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/membership/FamilyParentAccessServiceImplTest.java
git commit -m "feat(backend): persist email parent invitations"
```

## TASK-WM-003: Deliver and redeem email parent invitations through Google OAuth

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-WM-002

**Exact scope:**

Add the email-delivery adapter, public invitation landing/acceptance contract, and Google OAuth continuation needed to turn a pending parent invitation into an active membership.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V43__add_oauth_invitation_continuations.sql` (use the next sequential Flyway number if it differs at execution time).
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/invitation/ParentInvitationEmailSender.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/invitation/ParentInvitationService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/domain/model/OAuthInvitationContinuationEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/infrastructure/persistence/OAuthInvitationContinuationRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/ParentInvitationEntryResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyParentAccessResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/application/google/GoogleOAuthService.java`.
- Modify `apps/backend/src/main/resources/application.properties` and `.env.example` only for the selected mail-provider and public-origin configuration shape.
- Create `apps/web/src/routes/invite/parent/[token]/+server.ts`.
- Create `apps/web/src/routes/invite/parent/+page.svelte`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/invitation/ParentInvitationServiceImplTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResourceTest.java`.

**Goal:**

The invited email receives a one-time link, signs in with the matching Google account, and becomes an active family member only after explicit acceptance.

### Outcome

Email delivery, invite verification, a server-bound OAuth continuation, Google identity matching, and membership activation form one auditable transaction boundary without trusting a browser-provided email or redirect URL.

### Architectural decision

The invitation service owns delivery state and redemption; the mail adapter only sends a rendered link. An invite-entry endpoint validates the raw URL token and immediately replaces it with a short-lived, single-use server-side OAuth continuation. Its random nonce is bound to the invitation ID (never its token), initiating browser flow cookie, issue/expiry time, and allow-listed relative post-login path. Google OAuth validates and atomically consumes this continuation after identity verification. The backend, not the browser, compares normalized Google email to the invitation recipient and activates membership.

### Required changes

1. Select and implement one configured mail-provider adapter behind `ParentInvitationEmailSender`, including a development-safe disabled/fake implementation and actionable delivery failure semantics.
2. Add authorized create/resend/revoke operations and a public, token-consuming entry endpoint; rate-limit resend/redeem and return no account-enumeration detail from public endpoints.
3. On `GET /invite/parent/{token}`, validate only server-side, set/bind the temporary browser-flow context, and `303` to token-free `/invite/parent`; never render or hydrate the token-bearing path in Svelte.
4. Persist a random OAuth continuation nonce with invitation ID, browser binding, issued/expiry time, and intended local path. Callback consumption must be a conditional single-use database operation; a valid signed OAuth state from a different browser/session is rejected.
5. Have the token-free landing route start the existing Google OAuth flow if needed and resume the exact server-side continuation after login.
6. Reject email mismatch, expired/revoked/used token, absent Google verified email, callback without initiated continuation, replayed/cross-browser/swapped continuation, and unsafe continuation paths without issuing an unintended membership/session.
7. Test email payload construction without exposing a token in logs, callback state integrity, nonce replay/cross-browser/invite-swap failures, mismatch rejection, successful activation, and idempotent post-success refresh.

### Out of scope

- Password login redesign or automatic account merging.
- Telegram parent-invitation changes.
- Browser push notifications.

### Acceptance criteria

- An authorized family admin can send, resend, and revoke an email invitation; resend cannot flood the mailbox and does not create multiple active invitations.
- An anonymous recipient opening the link sees a no-store invitation flow and, after Google sign-in with the invited address, can explicitly accept the family role.
- The raw invite token is removed from the browser-visible URL before Google OAuth begins and never appears in the OAuth continuation, browser route data, analytics, or client-side telemetry.
- A callback can redeem its continuation once only when the initiating browser binding, invitation ID, expiry, and allow-listed relative path match; replay, another browser, or an invitation A/B swap is rejected.
- Signing in with a different Google email does not activate the invitation and gives a safe recovery path.
- A successful acceptance creates exactly one active membership with the invited permission and can be revisited without duplicating it.
- Invalid, expired, revoked, or previously used links reveal no personal data and cannot authenticate a user.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=ParentInvitationServiceImplTest,AuthGoogleResourceTest test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/invitation apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity apps/backend/src/main/resources/db/migration apps/backend/src/main/resources/application.properties apps/backend/src/test apps/web/src/routes/invite/parent .env.example
git commit -m "feat(backend): redeem parent invitations with Google"
```

## TASK-WM-004: Make child magic-link invitations revocable and invite-grade

**Status:** DONE
**Priority:** P0
**Depends on:** -

**Exact scope:**

Replace the shareable child token lifecycle exposed through `FamilyParentAccessResource` and `ChildMagicLinkResource` with a persisted, bounded child invitation lifecycle while preserving the single child-session mechanism.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V44__add_child_magic_link_invitations.sql` (use the next sequential Flyway number if it differs at execution time).
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/invitation/ChildMagicLinkInvitationEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/invitation/ChildMagicLinkInvitationService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyParentAccessResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/application/auth/AuthService.java` and `AuthServiceImpl.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/CookieBuilder.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResourceTest.java`.

**Goal:**

A permitted parent can issue or revoke a child invitation link; the child can use it once within its expiry to start only that child’s session.

### Outcome

Child magic links are safe to share through a parent-controlled channel and cannot remain indefinitely valid after loss or device handoff.

### Architectural decision

The invitation service owns CSPRNG token generation, HMAC digest lookup, expiry, conditional consumption, and revocation. `AuthService` remains the sole issuer of the child session payload/cookies after a valid invitation has been atomically consumed. `CookieBuilder` rotates the entire bearer credential set with a fresh per-issuance session identifier and clears any inherited role/family/child cookies before setting the child context. Do not use the persistent child login token or Telegram child invitation data for this browser flow.

### Required changes

1. Add one-time opaque child invitation storage and an admin-only issue/revoke/list-status contract scoped to the active family and child, using a 256-bit CSPRNG token and configured HMAC digest.
2. Change the public `/login-child/{token}` endpoint to atomically consume the credential server-side, clear prior authentication cookies, set a fully fresh child credential set, and `303` to token-free `/workspace` before Svelte runs.
3. Ensure token issue/revoke cannot cross family boundaries and avoid returning the raw token after initial generation.
4. Add a per-issuance session identifier to signed auth/refresh credentials so a parent-to-child, child-A-to-child-B, or anonymous-to-child transition cannot retain an earlier effective session.
5. Provide deterministic behavior for expiry, replay, revocation, inactive child, concurrent use, and an already-authenticated different child.
6. Retire or explicitly migrate the legacy get/regenerate child token calls and their client API wrappers in the same change, without weakening existing Telegram child linking.

### Out of scope

- Email delivery to children or child Google accounts.
- Parent email invitations.
- Parent/child workspace visual changes beyond the success redirect.

### Acceptance criteria

- Only an authorized family manager can issue or revoke a link for a child in their family.
- A new link signs in the intended active child once and redirects to token-free `/workspace`; the raw token never reaches Svelte, browser route data, or telemetry. A replay, expired, revoked, or foreign-family token never creates a session.
- Parent-to-child, child-A-to-child-B, and anonymous-to-child use each produce a new credential/session identifier and remove all previous role, family, child, refresh, and CSRF context; replaying the pre-rotation cookies cannot restore the previous principal.
- Revoking a link immediately prevents future use but does not delete an already-established child session.
- Invalid responses and redirect pages send `Cache-Control: no-store` and do not echo a raw token.
- Existing Telegram child connection/invite flow continues to work independently.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=ChildMagicLinkResourceTest,AuthServiceImplTest test
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration apps/backend/src/main/java/com/sashplatonov/earnit/kids/family apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/CookieBuilder.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/auth
git commit -m "feat(backend): secure child magic link invitations"
```

## TASK-WM-005: Deliver responsive shared workspace access and invitation UI

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-WM-001, TASK-WM-003, TASK-WM-004

**Exact scope:**

Expose the browser parent/child workspace, email parent invitation lifecycle, and child magic-link controls through shared workspace components. Continue the incremental migration of general components out of `components/telegram`.

**Files:**

- Modify `apps/web/src/routes/invite/parent/+page.svelte`.
- Create `apps/web/src/lib/features/workspace/access/ParentInvitationFlow.svelte`.
- Create `apps/web/src/lib/features/workspace/access/ChildMagicLinkControls.svelte`.
- Create `apps/web/src/lib/features/workspace/family/ParentAccessPanel.svelte`.
- Modify `apps/web/src/lib/features/workspace/ParentWorkspaceShell.svelte`.
- Modify `apps/web/src/lib/services/api.ts`.
- Modify `apps/web/src/lib/auth/googleOAuth.ts`.
- Modify `apps/web/src/lib/i18n/messages/en/auth.ts` and `apps/web/src/lib/i18n/messages/ru/auth.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentAccess.svelte` and `TelegramParentFamily.svelte` to consume shared access controls rather than a duplicate flow.
- Create `apps/web/tests/e2e/workspace-invitations.spec.ts`.

**Goal:**

Parents have one clear Access area in web and Mini App to invite/manage adults and children; recipients can complete the appropriate browser flow on desktop or a 320px-wide mobile viewport.

### Outcome

The information architecture groups account access, family membership, and child access by meaning rather than by host. UI wording distinguishes a sent parent email invitation, a pending invite, and an active family member.

### Architectural decision

The shared access components consume normalized API types from `services/api.ts`; host shells decide presentation (browser page/panel vs Telegram sheet). Do not introduce a second invitation store, direct fetch calls inside both hosts, or host-specific translations for the same state.

### Required changes

1. Implement the parent invitation landing states: loading, sign in with Google, email mismatch, explicit accept, accepted, expired/revoked, and retryable delivery/transport errors.
2. Replace the current email “add parent” immediate-access UI with send/resend/revoke/status controls, retaining the existing Telegram invite as a visibly separate Telegram-only option.
3. Add child magic-link issue/copy/revoke/status controls to the family/child management surface and explain the one-time/expiry behavior without displaying a token after the initial issue action.
4. Model the currently active family separately from the identity's available memberships and permissions. Browser/Telegram UI may switch only through the authenticated server selection contract and must refresh the shared workspace state without using a family ID from arbitrary UI/request state as authorization.
5. Consolidate general parent, child, family, access, and notifications modules under `features/workspace` as each is touched; keep Telegram icons/SDK/safe-area styling under `features/telegram`.
6. Add keyboard focus management, screen-reader status announcements, visible focus, 44px minimum touch targets, loading/empty/error/retry states, and 320px overflow assertions.

### Out of scope

- Replacing existing task/reward business APIs.
- Browser push consent and service-worker registration.
- Native Capacitor packaging.

### Acceptance criteria

- Desktop and 320px browser users can navigate `/workspace` without horizontal scrolling and reach all primary access controls with keyboard or touch.
- Parent invitation controls report pending/sent/expired/revoked/active states accurately after reload and never imply email delivery succeeded when the API failed.
- The parent invitation page handles Google sign-in continuation and all token failure states without exposing personal data.
- A family admin can issue/revoke a child magic link from both browser and Telegram-hosted workspace without duplicated state logic.
- A parent in multiple families sees the server-authorized active family and permission after switching; browser state cannot preserve admin-only controls when the selected membership is a viewer.
- General feature imports use `features/workspace`; Telegram-only code is named and located as Telegram-specific.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test -- tests/unit/googleOAuth.test.ts tests/unit/workspaceAccess.test.ts && npm run test:e2e -- tests/e2e/workspace-invitations.spec.ts tests/e2e/telegram-parent.spec.ts --project=desktop --project=compact-mobile
```

### Commit

```bash
git add apps/web/src/routes/invite apps/web/src/lib/features apps/web/src/lib/services/api.ts apps/web/src/lib/auth/googleOAuth.ts apps/web/src/lib/i18n/messages apps/web/src/lib/components/telegram/TelegramParentAccess.svelte apps/web/src/lib/components/telegram/TelegramParentFamily.svelte apps/web/tests
git commit -m "feat(web): add shared workspace invitation flows"
```

## TASK-WM-006: Persist and deliver browser push notifications

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-WM-002

**Exact scope:**

Add a Web Push subscription and delivery subsystem that consumes existing family notification preferences without altering Telegram delivery behavior.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V45__add_web_push_subscriptions.sql` (use the next sequential Flyway number if it differs at execution time).
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushSubscriptionEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushDeliveryEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushSubscriptionRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushDeliveryRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushOutboxProcessor.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/api/PushResource.java` (replace its authenticated placeholder operations; retain the `/api/push` ownership).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/notification/FamilyNotificationServiceImpl.java` only to expose a shared event decision, not browser transport details.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/outbox/ApplicationOutboxEventEntity.java` and `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/outbox/ApplicationOutboxEventRepository.java` to coordinate more than one transport without marking an event complete when only Telegram is terminal.
- Modify `.env.example` and `apps/backend/src/main/resources/application.properties` for VAPID/public push configuration.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushServiceTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/api/PushResourceTest.java`.

**Goal:**

An authenticated parent or child can register a browser subscription for their own identity, and enabled family notification preferences result in privacy-safe Web Push delivery.

### Outcome

Web push delivery is reliably planned from committed domain events, deduplicated per subscription, removes permanently invalid endpoints, and never leaks one family’s event to another recipient.

### Architectural decision

The subscription is bound to the authenticated role/parent-account-or-child identity and family context at registration time; client payload cannot select a recipient. Endpoint identity is not permanent account identity: re-registering the same endpoint under a different authenticated actor atomically detaches/rebinds it, and logout/next authenticated bootstrap removes or safely rebinds stale ownership. Event eligibility stays in the existing family notification preference layer. Existing `ApplicationOutboxEventEntity` is committed with the originating domain mutation; `WebPushOutboxProcessor` plans and sends delivery rows after commit, while an `event_id + subscription_id + transport` database constraint is the idempotency boundary.

### Required changes

1. Add schema constraints and endpoints to upsert/remove the standard browser `PushSubscription` under CSRF-protected authenticated requests.
2. Validate endpoint and key payload shape/size, bind it to the authenticated actor, and prevent a child/parent from registering another account’s endpoint. For the same endpoint under a new authenticated actor, atomically detach/rebind ownership; do not leave it permanently bound to the prior account after logout/account switching.
3. Reuse committed `ApplicationOutboxEventEntity` records as the source for Web Push planning. Extend the existing outbox coordination so Telegram and Web Push can each create terminal delivery rows and the event is complete only after every enabled transport has finished its idempotent plan.
4. Map only existing enabled event types to compact title/body/deep-link payloads; reuse preference decisions rather than creating a web-only preference list.
5. Handle delivery timeout, transaction-safe claim lease, transient retry policy, `404`/`410` endpoint deletion, and payload/log redaction in `WebPushOutboxProcessor`, never from the originating business transaction.
6. Record push subscription create/rebind/remove and terminal delivery failures in the shared security audit contract without endpoint keys or secrets.
7. Test role/family authorization, post-commit planning, delivery uniqueness, preference-off suppression, account-switch rebinding, invalid endpoint cleanup, and response contracts.

### Out of scope

- Client consent prompt, service-worker registration, or notification UI.
- Modifying Telegram outbox delivery or changing event preference defaults.
- Native mobile push.

### Acceptance criteria

- Registering the same subscription twice is idempotent; unregistering it prevents later delivery.
- A valid subscription is bound to the currently authenticated parent or child and cannot be created/removed across family boundaries.
- The same browser endpoint registered after parent A logs out and parent B logs in is atomically rebound (or detached then rebound); later A events cannot be delivered through B's active registration.
- Disabled family preferences produce no Web Push job; enabled ones produce at most one persisted delivery per active subscription/event, enforced by a database uniqueness constraint.
- A domain change and its eligible outbox event commit together; Web Push planning/sending occurs only from committed events and cannot send a notification for a rolled-back mutation.
- Permanent push-provider endpoint failures remove the subscription; transient failures are observable and do not fail the user’s task/reward mutation.
- VAPID private material is absent from API responses, logs, and committed configuration.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=WebPushServiceTest,PushResourceTest,FamilyNotificationServiceImplTest test
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/api/PushResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/notification/FamilyNotificationServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/outbox apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/outbox apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/security apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/webpush apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/api/PushResourceTest.java .env.example apps/backend/src/main/resources/application.properties
git commit -m "feat(backend): deliver browser push notifications"
```

## TASK-WM-007: Complete the PWA lifecycle and shared notification consent UI

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-WM-005, TASK-WM-006

**Exact scope:**

Register the existing service worker from the application, harden its update/offline/click handling, and add a shared workspace notification consent/settings component.

**Files:**

- Create `apps/web/src/lib/features/workspace/notifications/BrowserPushControls.svelte`.
- Create `apps/web/src/lib/features/workspace/notifications/browserPush.ts`.
- Create `apps/web/src/lib/features/workspace/pwa/registerServiceWorker.ts`.
- Modify `apps/web/src/routes/+layout.svelte`.
- Modify `apps/web/static/sw.js`.
- Modify `apps/web/static/manifest.json`.
- Modify `apps/web/src/lib/services/api.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramNotifications.svelte` to host the shared controls when supported.
- Create `apps/web/tests/unit/browserPush.test.ts`.

**Goal:**

Eligible browser users can install the workspace as a PWA and knowingly enable, disable, or recover browser notifications; Telegram WebView users get a clear unsupported explanation instead of a broken prompt.

### Outcome

PWA installation, service-worker update, browser permission, push subscription, notification click deep-linking, and offline fallback are explicit, tested states rather than dormant static assets.

### Architectural decision

The shared browser-push component owns feature detection and UI state; `browserPush.ts` owns browser APIs and calls the server API wrapper. The service worker remains transport-only and does not contain family/business authorization. The Telegram adapter supplies capability information rather than duplicating consent code.

### Required changes

1. Register `/sw.js` once from the authenticated application lifecycle, report update availability accessibly, and avoid caching authenticated API responses or invitation URLs.
2. Validate manifest start/scope/display/icon behavior for `/workspace` and installed mobile browsers without changing Telegram deep-link behavior.
3. Implement permission states `unsupported`, `default`, `granted`, `denied`, subscription pending/error, subscribed, and unsubscribed; request permission only after a user gesture.
4. Convert the browser `PushSubscription` safely and use the new authenticated server endpoints; remove legacy `registerPushTokenOnServer` placeholders if they are not the real contract.
5. Open the server-provided same-origin notification deep link on click, preserve focus, and provide an offline page/state that never exposes stale protected data.

### Out of scope

- Background synchronization or offline task/reward mutations.
- Notification content policy beyond the backend event mapping.
- App-store/native push support.

### Acceptance criteria

- In a supported installed/normal browser, enabling notifications only prompts after an explicit control activation and persists the resulting subscription after reload.
- Denied, unsupported, network-failed, and expired subscriptions give an actionable state without repeated automatic permission prompts.
- Telegram WebView does not attempt unsupported Push APIs and continues to show its existing family preference controls.
- The service worker does not cache `/api/` authenticated responses, `/invite/parent/`, `/login-child/`, or OAuth callback URLs.
- Notification click opens/focuses the intended same-origin workspace location; at 320px the controls remain visible, keyboard reachable, and at least 44px tall.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test -- tests/unit/browserPush.test.ts && npm run build
```

### Commit

```bash
git add apps/web/src/lib/features/workspace apps/web/src/routes/+layout.svelte apps/web/static/sw.js apps/web/static/manifest.json apps/web/src/lib/services/api.ts apps/web/src/lib/components/telegram/TelegramNotifications.svelte apps/web/tests
git commit -m "feat(web): enable PWA browser notifications"
```

## TASK-WM-008: Prove browser, mobile, Telegram, and PWA behavior end to end

**Status:** DONE
**Priority:** P2
**Depends on:** TASK-WM-005, TASK-WM-007

**Exact scope:**

Add end-to-end coverage for all new access paths and host parity, including a real browser service-worker lifecycle test. Keep existing Telegram fixture tests active.

**Files:**

- Create `apps/web/tests/e2e/workspace-access.spec.ts`.
- Create `apps/web/tests/e2e/pwa-browser-push.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-auth.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Modify `apps/web/playwright.config.ts`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/security/WorkspaceAccessAuthorizationTest.java`.
- Search anchor: `telegramSdkFixture` in `apps/web/tests/e2e/telegramSdkFixture.ts` identifies the existing controlled Telegram host fixture.

**Goal:**

Automated browser tests demonstrate that the same workspace is reachable and usable from normal web, mobile web, Telegram fixture, and PWA contexts.

### Outcome

Regression coverage distinguishes local browser proof from production Google, email, VAPID-provider, and Telegram-client proof, while validating all controllable contracts.

### Architectural decision

Use deterministic test doubles for Google/email/push provider boundaries and the existing Telegram SDK fixture. Browser tests verify client/service-worker behavior; backend tests remain responsible for authorization and delivery decisions. Do not make live external accounts a CI prerequisite.

### Required changes

1. Add normal-browser parent and child `/workspace` path tests, parent invitation accept/mismatch/expired flows, and child magic-link replay/revocation/session-rotation checks using test fixtures.
2. Verify the same role-specific product headings/actions in browser and Telegram fixture, and ensure host-only bootstrap controls do not appear in shared UI.
3. Add 320px geometry checks using bounding boxes and `elementFromPoint` for primary access, child-link, and notification controls.
4. Add service-worker registration/update/offline exclusion and mocked push-permission/subscription/click coverage in a browser context that supports service workers.
5. Add backend integration authorization attack-matrix coverage for cross-family invitation/action access, viewer/admin escalation attempts, anonymous status enumeration, OAuth continuation expiry/replay/cross-browser/invite-swap, child-link session transitions, and PushSubscription account rebinds.
6. Retain active Telegram visual/layout tests and migrate selectors to shared semantic/ARIA contracts only where the DOM moves.

| Actor | Attack | Expected result |
| --- | --- | --- |
| Parent in family A | Revoke or inspect an invitation in family B | `403`/non-enumerating `404`; no state change |
| Viewer parent | Create an admin invitation or child link | Denied before delivery/token generation |
| Child A | Register/remove Child B's subscription | Denied; no ownership change |
| Parent or Child A | Consume Child B magic link | Fresh child-B session or explicitly denied according to the endpoint contract; never mixed credentials |
| Invited email A | Complete Google OAuth as email B | No membership; safe audit rejection |
| Anonymous | Probe invitation status | Indistinguishable invalid response, no personal data |
| Expired/replayed/cross-browser OAuth state | Redeem a valid invitation | Rejected, no membership |
| Revoked invite and concurrent callback | Race redemption | Exactly one deterministic terminal result |

### Out of scope

- Live delivery to a production mailbox, Google account, push endpoint, or physical Telegram client.
- Load testing, penetration testing, and native-device testing.

### Acceptance criteria

- CI-local E2E covers successful and failed parent/child invitations, browser workspace role routing, and Telegram fixture routing.
- At 320px, every new primary action is inside the viewport, unobscured, and clickable at its center; keyboard focus order and Escape dialog handling are tested.
- The PWA test confirms service worker control and proves protected/invitation/OAuth URLs are not served from cache while offline behavior is user-safe.
- Existing Telegram parent, child, and layout test coverage remains enabled and passes against the migrated DOM.
- Backend integration tests execute the documented authorization matrix, including two-family parent permissions, nonce replay/swapping, session rotation, and shared-browser push subscription rebinding.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=WorkspaceAccessAuthorizationTest test && cd ../web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/workspace-access.spec.ts tests/e2e/pwa-browser-push.spec.ts tests/e2e/telegram-auth.spec.ts tests/e2e/telegram-parent.spec.ts
```

### Commit

```bash
git add apps/web/tests/e2e apps/web/playwright.config.ts apps/backend/src/test/java/com/sashplatonov/earnit/kids/security/WorkspaceAccessAuthorizationTest.java
git commit -m "test(web): cover workspace and PWA access paths"
```

## TASK-WM-009: Document operations and run final quality gates

**Status:** DONE
**Priority:** P2
**Depends on:** TASK-WM-008

**Exact scope:**

Document the completed web/Mini App access architecture, required external configuration, release checks, and verification boundaries; then run the relevant full local gates.

**Files:**

- Create `docs/operations/web-miniapp-access.md`.
- Modify `docs/architecture.md` only if its active route/auth diagram is now inaccurate; otherwise document the decision in the new operations runbook.
- Modify `.env.example` only to ensure all variables implemented by the preceding tasks are represented without secrets.
- Modify `apps/web/README.md` only if it is the established location for local PWA/test setup; otherwise do not create duplicate instructions.

**Goal:**

Operators can configure Google OAuth, email delivery, VAPID, public origins, and Telegram Mini App independently, and can tell local verification from deployed-provider proof.

### Outcome

The release has a concise source-of-truth runbook and passing local quality gates, with explicit follow-up checks for real providers and device/client behavior.

### Architectural decision

Keep operational configuration in one runbook and `.env.example`; do not duplicate implementation details in component docs. Runtime routes and settings are documented only after source verification from the completed tasks.

### Required changes

1. Record the `/workspace`, `/telegram`, parent-email invite, child magic-link, PWA, and push ownership boundaries plus no-`/app` compatibility decision.
2. List non-secret configuration variable names, redirect/allow-list requirements, VAPID key rotation considerations, email sender/domain setup, and disable/failure behavior.
3. Provide a production checklist for Google consent screen/redirect URI, DNS/HTTPS, email deliverability, VAPID browser verification, Telegram BotFather Mini App URL, and CORS preflight.
4. Run backend and web full local gates after all previous commits, fix any failures within their owning task, and record only the actual results.
5. State that local tests do not prove remote CI, a deployed provider configuration, a delivered email, a push on a real device, or an official Telegram client launch.

### Out of scope

- Deploying, changing production secrets, BotFather configuration, or sending production invitations.
- New product functionality.

### Acceptance criteria

- The runbook names every implemented configuration input and the exact observable deployment verification for each integration.
- It contains no secrets, raw invitation links, unsupported routes, or stale `/app` instructions.
- Backend verify and web lint/test/build pass locally after the final implementation.
- The handoff clearly separates local/static proof from remote CI and deployed email/Google/VAPID/Telegram proof.

### Targeted validation

```bash
git diff --check && cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify && cd ../web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add docs/operations/web-miniapp-access.md docs/architecture.md .env.example apps/web/README.md
git commit -m "docs: document web and miniapp access operations"
```
