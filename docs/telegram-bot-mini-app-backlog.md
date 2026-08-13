# Telegram Bot and Mini App - Implementation Backlog

## Goal

Make Telegram the primary mobile surface while retaining the current SvelteKit
application as a legacy-compatible reference. The bot is for seeing state and
performing frequent, bounded actions; the Mini App is for creating, editing,
and managing. Parents manage children, tasks, rewards, coins, invitations, and
requests. Linked children see their own grouped tasks/rewards, balance, request
state, and recent activity. Dashboard and statistics are explicitly excluded.

## Architectural decisions

- **Canonical state and adapter boundary.** PostgreSQL family data and the
  current application layer remain the source of truth. Web, Mini App, and bot
  use the same `FamilyActionService` mutations and existing repositories;
  Telegram adapters never write `children.balance`, `history`, `requests`,
  tasks, or rewards directly. Family actions publish neutral application events
  such as `TaskApproved`, `RewardPurchased`, `RewardApproved`, and
  `BalanceAdjusted`; Telegram is an outbox subscriber, not a dependency of
  `FamilyActionService`.
- **Request state machine.** A request has exactly
  `PENDING -> APPROVED | REJECTED`; terminal states are immutable. A single
  conditional, transactional service operation decides it, creates the current
  balance/history effects only for `APPROVED`, and returns an explicit stale or
  already-resolved result for repeated/conflicting decisions.
- **Consistency contract.** One transaction persists the canonical domain
  mutation, matching balance/history effects, and the neutral application
  outbox event. Only after commit does the initiating channel receive a
  refreshed server snapshot/read model and asynchronous Telegram delivery begin.
  Clients reconcile from server state after a command, `DATA_UPDATED`,
  foreground/resume, or stale-action response. No channel owns an optimistic
  balance, request status, or history source of truth.
- **Reliable delivery.** A neutral `ApplicationOutboxEvent` is one immutable
  business fact and may fan out to several Telegram deliveries. A delivery is
  one recipient plus attempt/retry/message state and owns its idempotency key.
  An atomic planning claim with a recoverable lease lets only one planner fan
  out an event at a time; `planningCompletedAt` closes planning after all rows
  are persisted. A unique `(event_id, recipient_identity_id)` constraint still
  protects a reclaim after a crashed planner. Recipient resolution happens
  before/while creating deliveries; an identity unlinked before delivery is
  marked skipped, not messaged. The event has no competing retry state: it is
  complete when every planned delivery is terminal (`SENT`, `SKIPPED`, or
  terminal failure). If planning finds no eligible recipient, it marks the event
  terminal as `NO_RECIPIENTS` immediately. The scheduler retries delivery with
  bounded backoff. This gives effectively-once behaviour in this application;
  an ambiguous external Telegram API timeout can never prove absolute
  exactly-once delivery. Transport failure never rolls back a committed domain
  transaction.
- **Identity lifecycle.** Telegram identity mappings are additive, unique, and
  auditable. Link, unlink, re-link, child invite acceptance, revocation, and
  conflict resolution happen server-side after verified Telegram `initData`.
  Existing password/cookie login and child magic links remain compatible.
  Security lifecycle audit (link, unlink, re-link, conflict, invite
  acceptance/revocation) is distinct from short-lived operational delivery and
  deduplication records, with separate retention policies.
- **Bot versus Mini App.** Inline keyboards, not a command catalogue, drive
  bot navigation. Bot quick actions are read state, child task request, parent
  decision, and parent coin adjustment. Catalog CRUD, archive/delete, groups,
  full filtered history, invitations, identity settings, and complex forms are
  Mini App-only. A bot flow must finish in one to three taps; a long list, form,
  multiple fields, or deep navigation opens the Mini App.
- **Safe callbacks and message lifecycle.** Navigation callbacks use compact,
  signed stateless payloads with action, actor reference/context, issued-at,
  menu-version, bounded TTL, and signature; only mutations use opaque, expiring,
  single-use server references. Every callback resolves the linked actor,
  permission, child scope, and current target state again. Menu/navigation and
  confirmation screens edit their originating message; business notifications
  are new messages. Callback data is never authority.
- **Balance concurrency.** Every balance change is an atomic signed delta
  operation (or equivalent optimistic/pessimistic locking) that writes its
  matching history row in the same transaction. Concurrent Web, Mini App, and
  bot adjustments must not lose updates.
- **Controlled release.** Bot, Mini App, and notifications are independently
  flaggable. Initial production enablement is limited to an allow-listed test
  family; flags change channel availability, never domain rules or data.
- **Observability and privacy.** Existing Micrometer, structured logging, trace
  MDC, and scheduler infrastructure are extended with correlation/event IDs,
  aggregate delivery metrics, and redacted errors. Do not log bot tokens, raw
  `initData`, Telegram user/chat IDs, callback payloads, notes, or task text.
- **Bounded implementation.** The neutral outbox is sufficient for this
  product. Do not add a generic event-handler framework, Kafka, a pluggable
  channel registry, a universal notification abstraction, or event sourcing in
  this delivery.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TG-001 | P0 | - | Lock down the shared domain state machine and reconciliation contract. |
| 2 | TG-002 | P0 | TG-001 | Add secure identity, invitation, callback, and audit persistence. |
| 3 | TG-003 | P0 | TG-002 | Make verified Mini App authentication the common Telegram entry gate. |
| 4 | TG-004 | P0 | TG-002, TG-003 | Establish gated webhook and adapter infrastructure before feature actions. |
| 5 | TG-005 | P0 | TG-004 | Deliver bounded parent/child inline navigation and quick actions. |
| 6 | TG-006 | P0 | TG-001, TG-004, TG-005 | Add transactionally reliable notifications and retries. |
| 7 | TG-007 | P1 | TG-001, TG-003 | Build parent Mini App management-only flows. |
| 8 | TG-008 | P1 | TG-001, TG-003 | Build child Mini App flows and request reconciliation. |
| 9 | TG-009 | P2 | TG-007, TG-008 | Add compact activity/history without analytics. |
| 10 | TG-010 | P1 | TG-004, TG-006 | Add flags, staged rollout controls, and Telegram telemetry. |
| 11 | TG-011 | P0 | TG-001--TG-010 | Prove one state across Web, Mini App, and bot; release safely. |
| 12 | TG-012 | P2 | TG-002, TG-006, TG-010 | Retain operational evidence only as long as necessary. |

## TG-001: Formalize request transitions and cross-channel consistency

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** -

### Outcome

Web, Mini App, and bot have one documented and tested state contract: a request
can be decided exactly once, and every channel reconciles from the same
server-owned result after mutation or stale state.

### Architectural decision

Extend the existing `PurchaseRequestStatus`, `FamilyActionRequestService`,
`FamilyActionService`, and `PurchaseRequestRepository`; do not introduce a
Telegram-specific request status or balance/history writer. The repository must
make the `pending` precondition atomic rather than relying on an in-memory read
followed by `setStatus`.

### Files

- Modify `docs/architecture.md`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/PurchaseRequestStatus.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ChildRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionRequestService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionService.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionServiceImplTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/family/FamilyResourceTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramConsistencyContractTest.java`.

### Work

1. Document the state diagram, initiator response, stale-response semantics,
   and reconciliation triggers in `docs/architecture.md`.
2. Make approve/reject conditional on `PENDING` inside the existing transaction;
   distinguish a missing/foreign target from an already decided request without
   leaking cross-family data.
3. Make every coin mutation, including parent adjustment, an atomic delta plus
   matching history write in the same transaction; do not calculate a new
   balance from a stale loaded entity.
4. Keep history/balance mutations and the refreshed response in that transaction.
   Specify that Web, Mini App, and bot re-read/reconcile after success, stale
   response, resume, and `DATA_UPDATED`.

### Acceptance criteria

- Concurrent approve/reject attempts result in one terminal transition and,
  where applicable, one balance/history effect.
- Repeated or conflicting decisions return a deterministic already-resolved
  result and never create duplicate history or notifications.
- Concurrent `+20` and `-10` adjustments from different parents/channels leave
  the balance at the arithmetic result and create one history row per delta.
- Child scope and parent permission are verified regardless of caller channel.
- Existing web request/approval regression tests still pass unchanged.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=FamilyActionServiceImplTest,FamilyResourceTest,TelegramConsistencyContractTest test
git diff --check
```

### Commit

```bash
git add docs/architecture.md apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/PurchaseRequestStatus.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ChildRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionRequestService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionService.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/family/FamilyResourceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramConsistencyContractTest.java
git commit -m "fix(backend): Guard request state transitions"
```

## TG-002: Persist Telegram identity lifecycle and secure references

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** TG-001

### Outcome

Parents can link, unlink, and re-link Telegram; a parent can issue/revoke a
single-use child invitation; and bot callbacks can resolve a safe, expiring
server action reference. Each lifecycle change is audit-recorded.

### Architectural decision

Add Telegram-only persistence that references existing parent/child rows:
identity mapping, child invite, mutation callback action, webhook update
deduplication, and security-audit record. Navigation callback payloads are
signed/stateless and create no database rows. Store invite secrets only as
digests. These records carry no catalogue, balance, or request truth and cannot
replace current auth/magic-link contracts.

### Files

- Create `apps/backend/src/main/resources/db/migration/V26__add_telegram_identity_lifecycle.sql`.
- Create `apps/backend/src/test/resources/db/migration/V26__add_telegram_identity_lifecycle.sql`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramIdentityEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramChildInvitationEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramCallbackActionEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramWebhookUpdateEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramSecurityAuditEventEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramIdentityRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramChildInvitationRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramCallbackActionRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramWebhookUpdateRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramSecurityAuditEventRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityServiceImpl.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityServiceImplTest.java`.

### Work

1. Add PostgreSQL and H2 migrations with foreign keys, uniqueness, expiry,
   revocation, consumption, and lookup indexes; never edit prior migrations.
2. Implement lifecycle rules for parent link/unlink/re-link, child invite issue,
   revoke/expire/accept, and identity conflicts. A Telegram ID may map to one
   active parent or child identity only.
3. Create opaque, single-use/expiring callback references only for approve,
   reject, and confirmed coin adjustment; use signed/stateless payloads for
   Back, Balance, Tasks, and other read/navigation buttons. Persist update-ID
   idempotency and security lifecycle audit type/time/actor reference without
   secrets or content.
4. Include action, actor reference/context, issued-at, menu version, bounded
   TTL, and signature in stateless navigation callbacks. Reject expired,
   signature-invalid, or unsupported-version payloads by editing the current
   menu to a refresh action without creating a callback row.

### Acceptance criteria

- Expired, revoked, consumed, and foreign-family invites cannot bind a child.
- Linking a Telegram identity already attached elsewhere fails safely; unlink
  revokes future Telegram access but preserves family history and web login.
- Re-link requires fresh verified identity and leaves an auditable transition.
- Navigation through an inline menu creates no callback-action database row;
  a mutation callback is single-use and cannot act after expiry.
- A stateless callback becomes invalid after its configured TTL or a menu-version
  change and cannot be replayed as a mutation.
- PostgreSQL migration and H2 baseline pass; parent accounts and child magic
  links remain valid.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramIdentityServiceImplTest test
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
git diff --check
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration/V26__add_telegram_identity_lifecycle.sql apps/backend/src/test/resources/db/migration/V26__add_telegram_identity_lifecycle.sql apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramIdentityEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramChildInvitationEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramCallbackActionEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramWebhookUpdateEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramSecurityAuditEventEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramIdentityRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramChildInvitationRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramCallbackActionRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramWebhookUpdateRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramSecurityAuditEventRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityServiceImplTest.java
git commit -m "feat(backend): Add Telegram identity lifecycle"
```

## TG-003: Authenticate the Mini App with verified Telegram init data

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** TG-002

### Outcome

Verified Telegram Mini App launches become the role-scoped existing session;
unlinked parents get a compact link/login route and unlinked children get an
invitation-required state.

### Architectural decision

`TelegramInitDataVerifier` validates raw signed `initData` and freshness.
`TelegramMiniAppAuthService` resolves TG-002 mappings and reuses `CookieBuilder`,
`JwtService`, `AuthContext`, same-origin `/api/*`, `api.ts`, and CSRF. Never
trust `initDataUnsafe`, a client role, or client child ID.

### Files

- Modify `.env.example`.
- Modify `apps/backend/src/main/resources/application.properties`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/telegram/TelegramConfig.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramInitDataVerifier.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMiniAppAuthService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramMiniAppAuthResource.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramFeatureGate.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramInitDataVerifierTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramMiniAppAuthResourceTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramFeatureGateTest.java`.
- Create `apps/web/src/lib/services/telegram.ts`.
- Create `apps/web/src/routes/telegram/+layout.svelte`.
- Create `apps/web/src/routes/telegram/+page.svelte`.
- Create `apps/web/tests/e2e/telegram-auth.spec.ts`.

### Work

1. Add named non-secret config documentation for bot, Mini App, init-data age,
   webhook settings, and a baseline closed-by-default Telegram feature gate; do
   not add secrets to the repository.
2. Validate HMAC, launch age, configured bot identity, and Telegram user before
   issuing a session; return generic unauthorized state for invalid/unlinked data.
3. Apply the baseline feature gate before session exchange or `/telegram` UI
   rendering. Initialize the SDK once and provide load/error/retry/non-Telegram
   browser states. The first `/telegram` route has no legacy dashboard shell.

### Acceptance criteria

- Valid linked parent/child launches receive the existing correctly scoped role
  session; tampered, stale, missing-user, or unknown identity receives none.
- At 320 px, loading, retry, link, and invitation-required states are readable
  without horizontal overflow and use safe-area padding.
- Existing browser login, session refresh, and proxy tests pass.
- The Mini App gate defaults closed, so TG-003 cannot independently expose a
  functional Telegram route before the later staged rollout configuration.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramInitDataVerifierTest,TelegramMiniAppAuthResourceTest,TelegramFeatureGateTest,AuthFilterTest test
cd apps/web && npm run lint && npm run test -- --run && npm run build
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-auth.spec.ts
git diff --check
```

### Commit

```bash
git add .env.example apps/backend/src/main/resources/application.properties apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/telegram/TelegramConfig.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramInitDataVerifier.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMiniAppAuthService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramMiniAppAuthResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramFeatureGate.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramInitDataVerifierTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramMiniAppAuthResourceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramFeatureGateTest.java apps/web/src/lib/services/telegram.ts apps/web/src/routes/telegram/+layout.svelte apps/web/src/routes/telegram/+page.svelte apps/web/tests/e2e/telegram-auth.spec.ts
git commit -m "feat(web): Authenticate Telegram Mini App"
```

## TG-004: Establish Telegram bot adapter infrastructure

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** TG-002, TG-003

### Outcome

The backend accepts authenticated Telegram updates, opens the Mini App, and
adapts them through a small Telegram boundary without duplicating domain logic.

### Architectural decision

`TelegramFeatureGate` is an injected baseline guard for webhook and Mini App
entry before delegation; it defaults closed. `TelegramWebhookResource` verifies
the webhook secret before delegation.
`TelegramBotService` parses `/start` and callback updates; `TelegramBotApiClient`
is the only Telegram HTTP adapter. Neither receives repositories for balance,
history, requests, or catalog mutation; it calls explicitly scoped application
services instead.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramWebhookResource.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImpl.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotApiClient.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramCallbackService.java`.
- Modify `.env.example`.
- Modify `apps/backend/src/main/resources/application.properties`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramWebhookResourceTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java`.

### Work

1. Implement disabled-by-default bot/Mini App feature gating, webhook secret
   authentication, update-id deduplication, `/start`,
   and a minimal inline entry keyboard with Open Mini App. Do not create CRUD
   slash commands.
2. Decode signed/stateless navigation callbacks with action, actor context,
   issued-at, menu version, TTL, and signature without persistence; resolve
   mutation callbacks through TG-002 references and verified identity/permission
   checks. Acknowledge malformed, expired, version-stale, duplicate, and
   unauthorized callbacks without exposing family data.
3. Define adapter-facing DTOs limited to Telegram transport; domain services
   remain the only source for business result/read models.

### Acceptance criteria

- Missing/invalid webhook secret cannot link an identity, read state, or mutate
  data; duplicate update is a no-op.
- Bot API transport is mockable behind one client, and a send failure does not
  alter existing domain state.
- The bot opens the configured Mini App and uses inline keyboard entry points.
- Disabled capability gates block new bot/Mini App access before any feature
  action; they do not change existing domain data or rules.
- No Telegram resource/service directly persists balance, history, request, or
  catalog entities.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramWebhookResourceTest,TelegramBotServiceImplTest test
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
git diff --check
```

### Commit

```bash
git add .env.example apps/backend/src/main/resources/application.properties apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramWebhookResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotApiClient.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramCallbackService.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramWebhookResourceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java
git commit -m "feat(backend): Add Telegram bot foundation"
```

## TG-005: Add role-specific inline menus and quick actions

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** TG-004

### Outcome

Children and parents can use concise inline menus for their frequent actions;
complex management remains absent from the bot and opens the Mini App instead.

### Architectural decision

Create a bounded Telegram query/action facade over existing family read/action
services. It returns only compact current projections, revalidates actor scope
on every callback, and invokes `FamilyActionService` for task requests, request
decisions, and coin adjustments. It does not add a bot-local balance or state.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramQuickActionService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramQuickActionServiceImpl.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/TelegramQuickActionResponse.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImpl.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramQuickActionServiceImplTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java`.

### Work

1. Child menu: current balance, up to five active tasks in existing
   server-returned catalogue order, up to five rewards, `✅ I did it` request,
   recent requests/operations, and Open Mini App. If more tasks exist, show
   `More tasks -> Mini App`; do not add a bot catalogue browser or multi-page
   task selection.
2. Parent main menu has at most six actions: `Child`, `Requests`, `Balance`,
   `Coins`, `Recent`, and `Open Mini App`. Tasks/rewards are a second-level
   compact read screen. Every parent screen displays the current child name.
   The bot retains selected child only within the active menu conversation; a
   fresh `/start` auto-selects and visibly names the only authorised child;
   with two or more children it shows a picker; with none it offers `Add child
   -> Mini App`. It never uses a hidden family-wide preference.
3. `Child` is the explicit second-level entry to compact `Tasks` and `Rewards`
   read screens as well as child switching. `Recent` remains recent operations;
   the main menu never leaves a route to Tasks/Rewards implicit.
4. Coin adjustment offers fixed bounded deltas. Before every delta, especially
   a negative one, edit the menu into `Add/remove <delta> coins for <child>?`
   with Confirm/Cancel; the mutation callback repeats child and delta and then
   revalidates both.
5. Use `editMessageText`/`editMessageReplyMarkup` for menus, navigation,
   confirmations, and post-action menu refresh. Send a new message only for a
   business notification or when Telegram cannot edit the original message.
6. Put pagination, full history, catalogue/group management, invitations, and
   linking/settings behind Mini App entry buttons. Resolve selection and action
   callbacks again against current state, not only their original menu.
7. After a parent decision, edit the request card to a terminal state and remove
   Approve/Reject: `✅ Approved by you` plus signed delta/resulting balance, or
   `❌ Rejected by you`. If another channel already decided it, show
   `Already approved` or `Already rejected` with the current server result and
   no enabled decision button.

### Acceptance criteria

- Child actions expose only their own state and may submit only a valid task
  request; they cannot choose a sibling, adjust coins, or decide requests.
- Parent actions respect membership permissions and selected-child ownership;
  viewer has no enabled mutation action.
- The parent main screen has no more than six actions; every child-scoped screen
  and confirmation visibly names the child, and child switching cannot confirm
  an action for a previously selected child.
- `/start` visibly auto-selects the only child, shows a picker for multiple
  children, and offers only an Add-child Mini App CTA when no child exists.
- `Child` has explicit compact Tasks and Rewards entry buttons, so those reads
  are reachable without adding more main-menu actions.
- Child task list contains at most five active rows and uses `✅ I did it`;
  further browsing opens Mini App rather than bot pagination.
- After `✅ I did it`, the task card changes to `⏳ Waiting for parent` and
  cannot submit again. After rejection it shows `❌ Not approved` and offers
  `🔄 Try again` only when the refreshed server state permits a new request.
- A parent decision edits its action card to `Approved by you` or `Rejected by
  you`, includes the server-derived balance for approval, and removes both
  decision buttons. A cross-channel winner yields an already-resolved card with
  no mutation control.
- A coin adjustment has explicit Confirm/Cancel before mutation, including a
  repeated child and signed delta; Cancel writes nothing.
- Navigating, confirming, and refreshing edits the original menu message; only
  business events create new notification messages.
- Every bot list is bounded and has an empty/unavailable state; each quick
  action reports canonical result or stale state and offers refresh.
- Bot navigation is chiefly inline keyboard based, not a list of slash commands.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramQuickActionServiceImplTest,TelegramMenuBuilderTest,TelegramBotServiceImplTest,FamilyActionServiceImplTest test
git diff --check
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramQuickActionService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramQuickActionServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/TelegramQuickActionResponse.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramQuickActionServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java
git commit -m "feat(backend): Add Telegram quick actions"
```

## TG-006: Add transactional outbox and business notifications

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** TG-001, TG-004, TG-005

### Outcome

Telegram recipients reliably receive one meaningful notification after a
committed domain event. Task/reward requests notify eligible linked parents;
approved/rejected requests and balance outcomes notify the child. Delivery
retries safely and never changes the committed business result.

### Architectural decision

`ApplicationOutboxEventEntity` is a neutral immutable application fact and
`TelegramDeliveryEntity` is one resolved Telegram recipient with its retry and
message lifecycle. A neutral application-event publisher records the event in
the existing domain transaction; the Telegram outbox subscriber creates one or
more deliveries and the scheduler sends them through `TelegramBotApiClient`.
The delivery owns its idempotency key. This avoids a Telegram import/call in
`FamilyActionService`; the application outbox is not a second domain log.

### Files

- Create `apps/backend/src/main/resources/db/migration/V27__add_telegram_outbox.sql`.
- Create `apps/backend/src/test/resources/db/migration/V27__add_telegram_outbox.sql`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ApplicationOutboxEventEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ApplicationOutboxEventType.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramDeliveryEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ApplicationOutboxEventRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramDeliveryRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/event/ApplicationEventPublisher.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramDeliveryPlanner.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionRequestService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionBalanceService.java`.
- Modify `apps/backend/src/main/resources/application.properties`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessorTest.java`.

### Work

1. In the same transaction as a domain change, publish neutral facts only for:
   `TaskRequestCreated`, `RewardRequestCreated`, `TaskApproved`,
   `TaskRejected`, `RewardPurchased` (the existing direct purchase operation,
   where debit happens immediately), `RewardApproved` (a pending reward request
   becomes approved, where debit happens exactly at approval), `RewardRejected`,
   and `BalanceAdjusted`. A request creation never also emits a balance event.
2. Plan parent deliveries for a child request only to linked eligible parents;
   plan one child delivery for its resolved request/balance event. A parent who
   just acted through the bot receives an edited result card, not a second
   notification. Other eligible parents with an existing action card for that
   request get that card edited to its resolved/stale state rather than a new
   alert. One business fact produces at most one user-facing delivery per
   recipient; do not follow a reward approval with a second balance notification.
3. Child copy includes signed, server-derived resulting balance, for example
   `Task approved -> +20 -> Balance: 145`, `Reward purchased -> -50 -> Balance:
   95`, and `Parent adjusted balance -> +30 -> Balance: 125`. Rejected requests
   have no balance delta.
4. Atomically claim an unplanned/reclaimable event for delivery planning with
   `planningClaimedAt` and an expiring planning lease; set
   `planningCompletedAt` only after all eligible delivery rows (or the
   `NO_RECIPIENTS` terminal result) are persisted. A crashed planner may be
   reclaimed after lease expiry, but cannot duplicate a recipient because the
   migration enforces unique `(event_id, recipient_identity_id)`.
5. Claim due deliveries safely, resolve delivery recipient identity at planning
   time and skip an identity unlinked before delivery. Retry retryable transport
   failures with bounded exponential backoff and record an effectively-once
   application attempt; an externally ambiguous timeout may still make absolute
   exactly-once Telegram delivery unknowable.
6. Mark an application event complete only after every planned delivery is
   terminal (`SENT`, `SKIPPED`, or terminal failure); delivery retries live only
   on `TelegramDeliveryEntity`, never in a second event retry state.
   Mark an event terminal as `NO_RECIPIENTS` immediately when planning yields
   zero eligible recipients.
7. Persist the Telegram chat/message reference on every successful actionable
   delivery, keyed by its event/request and recipient, so a later decision can
   edit the exact message text/keyboard. A failed or skipped delivery has no
   editable-message claim.
8. Format child notifications in visual order: a plain-language event first,
   signed coin delta second, resulting balance third—for example
   `✅ Room cleaned approved`, `+20 🪙`, `Balance: 145 🪙`—rather than exposing
   transport or transaction wording.

### Acceptance criteria

- A successful domain transaction creates one neutral event; a rejected or
  rolled-back action creates none. One event may create several recipient
  deliveries, each with its own retry state and idempotency key.
- Two concurrent planners can claim an event only once while its planning lease
  is active; a planner crash is recoverable after lease expiry, and the unique
  event/recipient constraint prevents duplicate delivery rows during re-planning.
- An event becomes terminal only when all planned deliveries are terminal; a
  sent delivery and an unlinked-recipient skipped delivery therefore complete
  the same parent-fan-out event without a competing event retry state.
- An event with zero eligible recipients becomes `NO_RECIPIENTS` immediately,
  and a successful actionable delivery persists the chat/message reference
  needed to disable or replace its inline keyboard later.
- Telegram send failure leaves request status, balance, and history committed;
  retries provide effectively-once application delivery and record ambiguous
  external outcomes rather than claiming impossible absolute exactly-once send.
- Approved task/reward and parent adjustment notification copy has signed coin
  delta and server-derived resulting balance, never an optimistic value, with
  human event -> delta -> balance hierarchy.
- Scheduler/retry tests cover planning-claim contention, crash/reclaim, unique
  event/recipient fan-out, delivery claim contention, restart, backoff, terminal
  failure, an unlinked recipient, and no duplicate parent/child notification for
  one business fact without leaking Telegram identifiers.
- A request decided by another parent/channel edits any existing actionable bot
  card to resolved; it does not leave a second parent an apparently actionable
  stale card or send them a second notification.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramOutboxProcessorTest,FamilyActionServiceImplTest test
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
git diff --check
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration/V27__add_telegram_outbox.sql apps/backend/src/test/resources/db/migration/V27__add_telegram_outbox.sql apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ApplicationOutboxEventEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ApplicationOutboxEventType.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramDeliveryEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ApplicationOutboxEventRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramDeliveryRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/event/ApplicationEventPublisher.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramDeliveryPlanner.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionRequestService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionBalanceService.java apps/backend/src/main/resources/application.properties apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessorTest.java
git commit -m "feat(backend): Add Telegram delivery outbox"
```

## TG-007: Build the parent Mini App management workspace

**Status:** ✅ Completed
**Priority:** P1
**Depends on:** TG-001, TG-003

### Outcome

Parent Mini App offers child switching, task/reward create-edit-archive,
group management, invitations, linking/settings, and detailed management flows
without desktop density, dashboard, or analytics.

### Architectural decision

The route is only an authenticated role gate: server session ->
`TelegramRoleResolver` -> parent or child composition. Client state never picks
a role, and `+page.svelte` does not become a cross-role coordinator. Parent
composition reuses `appStore`, `bootstrap.ts`, `api.ts`, `serverContract.ts`,
task/shop payload helpers, and existing command routes; it never forks
save/normalization/business rules.

### Files

- Create `apps/web/src/lib/components/telegram/TelegramParentShell.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramRoleResolver.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramChildPicker.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramParentTasks.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramParentRewards.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramCatalogEditor.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramChildInvite.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramLinkSettings.svelte`.
- Create `apps/web/src/lib/services/telegramViewState.ts`.
- Modify `apps/web/src/routes/telegram/+page.svelte`.
- Create `apps/web/tests/e2e/telegram-parent.spec.ts`.

### Work

1. Use four parent navigation destinations: `Requests`, `Tasks`, `Rewards`, and
   `Child`. `Child` contains child switch, invitation, Telegram linking/settings,
   and related management; do not put them in bottom navigation.
2. On normal parent launch, land on `Requests` when the refreshed server-scoped
   snapshot contains pending requests; otherwise land on `Tasks`. Do not restore
   an unrelated old client tab.
3. Create full-screen mobile sheets for task/reward CRUD, archive/delete,
   groups, invite, and Telegram identity settings. Reuse existing API contracts.
4. Treat one primary intent per screen—not literally one action—as the rule:
   request decision has Approve/Reject and editor has Save/Cancel with clear
   visual hierarchy. Preserve selected child/context after reconciliation and
   stale-action refresh.
5. Omit dashboards, charts, analytics, bulk desktop tooling, and bot-only quick
   menus from the parent Mini App.

### Acceptance criteria

- At 320, 375, and 390 px, controls have 44 by 44 px targets, visible focus,
  loading/empty/error/retry states, safe-area padding, and no horizontal scroll.
- A permitted parent sees server-reconciled task/reward CRUD, groups, invite,
  and link/settings after reload; a viewer cannot mutate them.
- Switching child cannot submit stale child data. The UI explains a stale action
  and refreshes rather than silently overwriting a newer state.
- `/telegram` renders parent or child composition exclusively from the verified
  server role; client query/state changes cannot select the other role.
- Parent normal launch opens server-derived `Requests` if pending requests
  exist, otherwise `Tasks`; it does not restore an unrelated old tab.
- No parent Telegram navigation contains dashboard, statistics, or analytics.

### Verification

```bash
cd apps/web && npm run lint && npm run test -- --run && npm run build
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-parent.spec.ts
git diff --check
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramParentShell.svelte apps/web/src/lib/components/telegram/TelegramRoleResolver.svelte apps/web/src/lib/components/telegram/TelegramChildPicker.svelte apps/web/src/lib/components/telegram/TelegramParentTasks.svelte apps/web/src/lib/components/telegram/TelegramParentRewards.svelte apps/web/src/lib/components/telegram/TelegramCatalogEditor.svelte apps/web/src/lib/components/telegram/TelegramChildInvite.svelte apps/web/src/lib/components/telegram/TelegramLinkSettings.svelte apps/web/src/lib/services/telegramViewState.ts apps/web/src/routes/telegram/+page.svelte apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "feat(web): Add parent Telegram workspace"
```

## TG-008: Build the child Mini App task and reward flow

**Status:** ✅ Completed
**Priority:** P1
**Depends on:** TG-001, TG-003

### Outcome

A linked child sees their balance and grouped tasks/rewards, submits a task or
reward request, and gets honest pending/resolved/stale feedback backed by the
shared request state machine and notification/reconciliation contract.

### Architectural decision

The child composition selected by `TelegramRoleResolver` calls existing child-scoped task/shop request
commands through `api.ts`; server `AuthContext.childId` always wins. Dedicated
Telegram components are presentation-only and do not import legacy `AppShell`,
`TasksSection`, or `ShopSection` as the new mobile shell.

### Files

- Create `apps/web/src/lib/components/telegram/TelegramChildShell.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramBalanceHeader.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramGroupedCatalog.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramChildRewards.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramRequestSheet.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramActionStatus.svelte`.
- Modify `apps/web/src/routes/telegram/+page.svelte`.
- Modify `apps/web/src/lib/services/api.ts`.
- Create `apps/web/tests/e2e/telegram-child.spec.ts`.
- Create `apps/web/tests/e2e/telegram-layout.spec.ts`.

### Work

1. Land a child on `Tasks / Today` on every normal Mini App open, with balance
   above the list; do not restore an old History/Rewards tab after a long gap.
   Show a flat list when there is one meaningful group or
   only a few items; use expandable grouped cards only for multiple meaningful
   groups. Each card has one unambiguous request action; note is optional and
   submission is disabled in-flight.
2. Reconcile from returned snapshot and relevant data-update event; on stale
   action, refresh/re-render current request state instead of optimistic repair.
3. Render pending, approved, rejected, unavailable, insufficient-balance,
   error, and empty state in child-friendly copy; no coins are claimed before
   approval when current business rules require approval.

### Acceptance criteria

- A child cannot select a sibling, edit catalog, adjust coins, or bypass server
  child scope by altering browser state.
- Requests stay pending across reload and resolve once after parent Web, bot, or
  Mini App decision; balance/history do not duplicate.
- Normal child launch opens Tasks / Today with balance visible before any
  history/reward tab, regardless of a stale previously selected tab.
- At 320 px and mobile landscape, grouped content is readable, all actions meet
  44 by 44 px, focus/status semantics work, and Telegram safe areas do not clip.
- One group/few items renders a flat child list; multiple meaningful groups
  render an expandable grouped list without hiding all available tasks.
- No child screen contains dashboard, statistics, chart, or leaderboard UI.

### Verification

```bash
cd apps/web && npm run lint && npm run test -- --run && npm run build
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-child.spec.ts tests/e2e/telegram-layout.spec.ts
git diff --check
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildShell.svelte apps/web/src/lib/components/telegram/TelegramBalanceHeader.svelte apps/web/src/lib/components/telegram/TelegramGroupedCatalog.svelte apps/web/src/lib/components/telegram/TelegramChildTasks.svelte apps/web/src/lib/components/telegram/TelegramChildRewards.svelte apps/web/src/lib/components/telegram/TelegramRequestSheet.svelte apps/web/src/lib/components/telegram/TelegramActionStatus.svelte apps/web/src/routes/telegram/+page.svelte apps/web/src/lib/services/api.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "feat(web): Add child Telegram flows"
```

## TG-009: Add compact requests and activity history

**Status:** ✅ Completed
**Priority:** P2
**Depends on:** TG-007, TG-008

### Outcome

Parent sees pending/decided requests and child sees their own requests and
recent operations; the Mini App provides full bounded history/filtering without
analytics cards.

### Architectural decision

Reuse `/api/requests` and `/api/history`, `FamilyReadResource`,
`FamilyHistoryQueryService`, and repositories. Add an additive typed cursor/page
contract only if existing paging cannot render a bounded mobile list; sorting
and ownership stay server-side.

### Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyReadResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryServiceImpl.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java`.
- Create `apps/web/src/lib/components/telegram/TelegramRequestList.svelte`.
- Create `apps/web/src/lib/components/telegram/TelegramHistoryList.svelte`.
- Create `apps/web/src/lib/services/telegramActivity.ts`.
- Modify `apps/web/src/routes/telegram/+page.svelte`.
- Create `apps/web/tests/e2e/telegram-activity.spec.ts`.

### Work

1. Make newest-first order deterministic with a stable secondary ID and scope
   parent/child reads in the service layer, not client filters.
2. Parent can approve/reject via the existing decision operation; child sees
   status and their own history only. Bot stays limited to recent bounded rows.
3. Add loading, empty, failure/retry, and load-more states without totals/charts.

### Acceptance criteria

- Query tampering cannot widen family/child scope; page boundaries are stable
  and contain no duplicate rows.
- A bot, Web, or Mini App decision appears after reconciliation with identical
  terminal state and history in every channel.
- At 320 px, full activity/filter controls remain accessible and semantically
  labelled; no analytics/dashboard UI is added.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=FamilyResourceTest,FamilyActionServiceImplTest test
cd apps/web && npm run lint && npm run test -- --run
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-activity.spec.ts
git diff --check
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyReadResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java apps/web/src/lib/components/telegram/TelegramRequestList.svelte apps/web/src/lib/components/telegram/TelegramHistoryList.svelte apps/web/src/lib/services/telegramActivity.ts apps/web/src/routes/telegram/+page.svelte apps/web/tests/e2e/telegram-activity.spec.ts
git commit -m "feat(web): Add Telegram activity views"
```

## TG-010: Add feature flags, staged rollout, and observability

**Status:** ✅ Completed
**Priority:** P1
**Depends on:** TG-004, TG-006

### Outcome

The baseline closed gate from TG-003 becomes independent bot, Mini App, and
notification capability flags with an allow-listed test family, while operators
can observe webhook, callback, outbox, retry, and delivery health without
exposing personal Telegram data.

### Architectural decision

Extend current config/observability paths, `BackendKpiMetrics`, Micrometer, and
trace MDC. Feature evaluation occurs server-side per capability and active
family; it does not live in local storage or alter `FamilyActionService` rules.

### Files

- Modify `.env.example`.
- Modify `apps/backend/src/main/resources/application.properties`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/telegram/TelegramConfig.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/observability/BackendKpiMetrics.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramFeatureGate.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramObservability.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramWebhookResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramFeatureGateTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramObservabilityTest.java`.
- Modify `docs/monitoring/newrelic.md`.

### Work

1. Extend the TG-003 closed baseline to independent bot, Mini App, and
   notification flags plus a configured allow-listed family ID for staged
   rollout. The notification flag never suppresses the neutral
   `ApplicationOutboxEvent`: it only prevents `TelegramDeliveryPlanner` from
   creating a sendable delivery (record a terminal `SKIPPED_DISABLED` result).
   Denied access does not delete data or alter a business mutation.
2. Emit aggregate counters/timers for webhook accepted/rejected/deduplicated,
   callback outcome, outbox queued/claimed/retried/sent/failed, and feature gate
   outcome. Propagate an internal correlation/event ID through request, outbox,
   and delivery logs.
3. Redact identifiers/content and document dashboards/alerts for delivery age,
   retry exhaustion, authentication failures, and unexpected callback failures.

### Acceptance criteria

- Each capability can be enabled independently, with initial access limited to
  the configured test family; unflagged families cannot bypass it via URL or
  webhook callback.
- A disabled notification flag still commits the neutral application event but
  creates only a terminal disabled/skipped Telegram delivery; disabling bot/Mini
  App does not delete identity or domain data.
- Metrics/log tests prove counters and correlation IDs without raw secrets,
  Telegram identifiers, request notes, or task/reward text.
- Existing metrics/export smoke tests continue to pass.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramFeatureGateTest,TelegramObservabilityTest,NewRelicMetricsExportSmokeTest test
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
git diff --check
```

### Commit

```bash
git add .env.example apps/backend/src/main/resources/application.properties apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/telegram/TelegramConfig.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/observability/BackendKpiMetrics.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramFeatureGate.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramObservability.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/TelegramWebhookResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramFeatureGateTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramObservabilityTest.java docs/monitoring/newrelic.md
git commit -m "feat(backend): Add Telegram rollout controls"
```

## TG-011: Prove cross-channel integration and release readiness

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** TG-001--TG-010

### Outcome

Automated evidence proves Web, Mini App, and bot change/read one domain state
without divergence; remote CI, deployment configuration, and real Telegram
client validation are recorded as distinct release evidence.

The persistence scenario now exercises the real family action service,
Telegram identity/quick-action services, outbox publisher, delivery planner,
and repositories in one Quarkus test transaction.

### Architectural decision

Cross-channel tests use real service/persistence boundaries with mocked Telegram
transport where appropriate. Browser tests validate Mini App geometry; neither
local test success nor a build is claimed as BotFather or physical-client proof.

### Files

- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java`.
- Create `apps/web/tests/e2e/telegram-consistency.spec.ts`.
- Modify `apps/web/tests/e2e/smoke.spec.ts`.
- Modify `.github/workflows/quality.yml`.
- Modify `docker-compose.yml`.
- Modify `docker-compose.native.yml`.
- Modify `README.md`.
- Create `docs/telegram-release-verification.md`.

### Work

1. Test Web-created request -> bot parent decision -> Mini App/Web history and
   balance; Mini App request -> Web decision -> bot notification; and parent
   adjustment -> child bot/Mini App refreshed balance. Include duplicate/stale
   action and outbox retry assertions.
2. Add focused CI gates and Compose config checks using placeholder non-secret
   variables. Document webhook registration/rotation, rollback by flags, and
   manual staged-client checklist.
3. Require a fresh remote CI run and manual Telegram validation of parent link,
   child invite/revoke/expiry, quick actions, notifications, safe areas, and
   legacy web regression before production enablement.

### Acceptance criteria

- Each scenario ends with identical canonical request, balance, and history
  state regardless of initiating/deciding channel; delivery records demonstrate
  effectively-once application handling and explicitly cover ambiguous external
  Telegram outcomes.
- Browser tests cover 320/390 px touch geometry and stale refresh; backend tests
  cover transaction and persistence integration.
- Both Compose variants render successfully without printing secrets; CI reports
  the focused tests for the committed revision.
- Release evidence separates local checks, fresh remote CI, configured webhook,
  and actual Telegram client results.

### Verification

```bash
docker compose config --quiet
docker compose -f docker-compose.native.yml config --quiet
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramCrossChannelIntegrationTest test
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test -- --run && npm run build
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-auth.spec.ts tests/e2e/telegram-parent.spec.ts tests/e2e/telegram-child.spec.ts tests/e2e/telegram-activity.spec.ts tests/e2e/telegram-layout.spec.ts tests/e2e/telegram-consistency.spec.ts tests/e2e/smoke.spec.ts
git diff --check
```

### Commit

```bash
git add apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java apps/web/tests/e2e/telegram-consistency.spec.ts apps/web/tests/e2e/smoke.spec.ts .github/workflows/quality.yml docker-compose.yml docker-compose.native.yml README.md docs/telegram-release-verification.md
git commit -m "test(telegram): Add cross-channel release gate"
```

## TG-012: Apply Telegram retention and cleanup policy

**Status:** ✅ Completed
**Priority:** P2
**Depends on:** TG-002, TG-006, TG-010

### Outcome

Expired invitations, consumed callbacks, webhook deduplication records,
completed delivery records, and lifecycle audit records are retained only for
documented operational/security periods and are cleaned safely.

### Architectural decision

The existing Quarkus scheduler runs bounded, idempotent cleanup batches through
repositories. Retention configuration is explicit and conservative; cleanup
never removes canonical family/task/request/history data or records needed by
an active retry.

### Files

- Modify `.env.example`.
- Modify `apps/backend/src/main/resources/application.properties`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramRetentionService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramRetentionReport.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramChildInvitationRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramCallbackActionRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramWebhookUpdateRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ApplicationOutboxEventRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramDeliveryRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramSecurityAuditEventRepository.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramRetentionServiceTest.java`.
- Modify `docs/telegram-release-verification.md`.

### Work

1. Define configurable retention windows for expired/revoked invites, consumed
   callback/update-dedup records, and successful/terminal delivery records as
   operational data; set a distinct, longer security-audit retention for link,
   unlink, re-link, conflict, and invitation security events, with rationale and
   an operator dry-run/count report.
2. Delete/compact only eligible terminal rows in ordered batches. Skip pending
   or retryable outbox rows, active invitations, and any canonical domain data.
3. Add metrics/log summaries of counts and failures without row content or
   Telegram identity values.

### Acceptance criteria

- Cleanup is repeatable, bounded, and safe across restarts; it does not remove
  an active invite, a pending delivery, or an event eligible for retry.
- Retention never removes family, child, task, reward, request, balance, or
  history records.
- Security lifecycle audit uses a separately configured longer retention than
  operational invite/callback/update/delivery records; neither policy silently
  changes the other.
- Tests cover every eligibility boundary and confirm configuration defaults are
  documented; operational output contains counts only, not private payloads.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramRetentionServiceTest test
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
git diff --check
```

### Completion evidence

- Retention configuration, transactional bounded repository cleanup, count-only
  dry-run, and scheduler wiring are implemented.
- `TelegramRetentionServiceTest` covers dry-run reporting, batch bounds, and
  delivery-before-outbox ordering.

### Commit

```bash
git add .env.example apps/backend/src/main/resources/application.properties apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramRetentionService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramChildInvitationRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramCallbackActionRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramWebhookUpdateRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ApplicationOutboxEventRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramDeliveryRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramSecurityAuditEventRepository.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramRetentionServiceTest.java docs/telegram-release-verification.md
git commit -m "chore(backend): Add Telegram retention policy"
```

## Bot and Mini App capability boundary

| Capability | Bot inline menus | Mini App |
| --- | --- | --- |
| Child balance, short task/reward/activity/request lists | Yes | Yes |
| Child task completion request | Yes | Yes |
| Parent child selection, balance, pending decision, coin adjustment | Yes | Yes |
| Task/reward creation, editing, archive/delete, groups | No | Yes |
| Full history and filters | No, recent rows only | Yes |
| Child invitation and Telegram link/unlink/settings | No | Yes |
| Dashboard/statistics | No | No |

## Evidence boundaries

| Evidence | Proves | Does not prove |
| --- | --- | --- |
| Backend tests and `./mvnw verify` | State machine, persistence, outbox, security, static analysis | BotFather setup or a physical Telegram client |
| Web lint/tests/build and Playwright | Mini App rendering, proxy/session logic, browser geometry | Telegram-signed production launch context |
| Compose config | Interpolated topology | Container startup, reachable webhook, configured remote secrets |
| Fresh remote CI | Committed revision passed remote jobs | Telegram client UX |
| Manual staged client check | Bot launch, callbacks, delivery, safe areas | Long-term production reliability |

## Final backlog review

- Legacy `/app/*` is retained as a compatibility/reference client.
- The P0 path establishes shared state, secure identity, bot foundation, quick
  actions, reliable notifications, and cross-channel proof before rollout.
- Telegram is an adapter: it cannot mutate canonical balance/history directly.
- Every task is one intended commit with observable criteria and exact commands.
- The plan retains required tasks, rewards, requests, balances, and history;
  it deliberately excludes Telegram dashboard and statistics work.
