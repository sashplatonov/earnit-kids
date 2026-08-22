# Java Backend Semantic Package Refactor - Implementation Backlog

## Goal

Refactor the entire Quarkus Java backend into feature-oriented modules with
explicit layer boundaries, without changing persisted data, HTTP routes,
response payloads, authorization, or integration behaviour. Telegram is one
required top-level module, not the scope of this backlog: identity/auth, family
and its task/shop/request/history domains, super-admin analytics, platform
operations, realtime, and external integrations are all included. Confirmed
SRP violations must be split into focused collaborators; existing thin facades
and single-purpose services must not be split merely because they are involved
in the migration.

## Architectural decisions

- **Target package model:** retain only genuinely cross-cutting root packages:
  `config`, `exception`, `i18n`, and `util`. Move every feature-owned HTTP
  contract, resource, application service, entity, and repository underneath
  a top-level module with `api`, `application`, `domain`, and
  `infrastructure` subpackages. Tests mirror the production package paths.
- **Telegram ownership:** `telegram.domain` owns Telegram identity, invitation,
  callback, delivery, audit, and webhook-update entities. `telegram.infrastructure.persistence`
  owns only their Panache repositories; `telegram.infrastructure.botapi` owns
  outbound Bot API transport. `telegram.application` owns orchestration, and
  `telegram.api` owns JAX-RS resources and HTTP DTO records. A resource may
  call an application service; an application service may call a repository;
  no layer is bypassed.
- **Core business boundaries:** family, task, shop, request, history, account,
  and parent-membership state remain the authoritative core domain. Telegram
  calls those established service interfaces for business actions and must not
  duplicate balances, catalog state, authorization, or family ownership rules.
- **Identity ownership:** authentication/session lifecycle, parent accounts,
  Google sign-in, password/token utilities used only for identity, and account
  settings form the `identity` module. Cookie/JWT filters remain root `config`
  cross-cutting transport/security infrastructure; they depend on identity
  application contracts, not vice versa.
- **Family ownership:** the `family` module owns family, child, membership,
  notification preference, friend, task, shop, request, and history state plus
  its command/query APIs. Its existing dashboard/action/command collaborators
  remain semantic application subpackages instead of being flattened.
- **Platform ownership:** database health/base-data work, outbox event
  publishing, HTTP metrics, UI logs, push, WebSocket notification delivery,
  and operational diagnostics form `platform` subdomains. They do not become
  a generic dumping-ground for family, identity, admin, or Telegram logic.
- **Super-admin analytics is not Telegram integration:** the current
  `resource/telegram/admin` and `service/telegram/admin` placement is a
  presentation-era location, while `AdminAnalyticsRepository` queries platform
  data. Move it to a top-level `admin` module, preserving `/api/super/*` routes
  and the existing response records. Do not create Telegram-specific copies of
  analytics DTOs or queries.
- **Compatibility:** package relocation is source-only. Keep CDI bean names,
  REST paths, OpenAPI tags, JSON field names, database table names, migrations,
  callback-data encoding, signed-init-data verification, cookie/CSRF behaviour,
  and legacy email invitation support unchanged. Do not add a migration for a
  Java package move.
- **SRP criterion:** split a class only when it owns independently changing
  business workflows or persistence query families. The confirmed violations
  are the multi-metric `AdminAnalyticsRepository`, `FamilyResource` (task,
  shop, request, import, balance, and history commands), and
  `TelegramBotServiceImpl` (message/start versus callback update dispatch).
  Keep `FamilyServiceImpl` as the documented compatibility facade because it
  already delegates to bounded collaborators; keep
  `TelegramQuickActionServiceImpl`, `TelegramAccountConnectionServiceImpl`, and
  `TelegramOutboxProcessor` intact unless characterization tests expose a
  behaviour dependency that requires a narrower extraction.
- **Quality rules:** use records for HTTP contracts, constructor injection for
  refactored CDI services, `OperationResult` for expected failures, explicit
  mapping, parameterized Panache queries, and transactions at the existing
  write boundary. No `@SuppressWarnings`, quality-rule exclusions, Javadoc, or
  new dependencies are permitted.

## Target package map

```text
com.sashplatonov.earnit.kids/
  identity/{api,application,domain,infrastructure}
  family/{api,application,domain,infrastructure}
  admin/{api,application,infrastructure}
  telegram/
    api/{resource,request,response}
    application/{auth,bot,callback,connection,identity,invitation,notification}
    config/
    domain/{model}
    infrastructure/{botapi,persistence}
  platform/{api,application,domain,infrastructure,realtime}
  shared/api/{request,response}
  config/ exception/ i18n/ util/
```

This is a destination map for the whole Java service, not permission to
redesign public APIs. Existing small shared types stay in their current
cross-cutting package only when no feature owns them; each such exception is
listed in a task below.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-101 | P0 | - | Characterizes whole-backend contracts and locks the source-only scope. |
| 2 | TASK-111 | P1 | TASK-101 | Establishes the cross-cutting/platform boundary before feature moves. |
| 3 | TASK-112 | P1 | TASK-111 | Groups identity, auth, account, and Google workflows. |
| 4 | TASK-113 | P1 | TASK-101 | Moves family-owned domain state and persistence. |
| 5 | TASK-114 | P1 | TASK-113 | Moves family API contracts and query resources. |
| 6 | TASK-108 | P2 | TASK-114 | Splits the confirmed multi-workflow family command resource. |
| 7 | TASK-115 | P2 | TASK-113 | Groups family application workflows without breaking its facade. |
| 8 | TASK-106 | P1 | TASK-101 | Splits the confirmed multi-domain analytics repository. |
| 9 | TASK-107 | P2 | TASK-106 | Relocates super-admin analytics into its own module. |
| 10 | TASK-102 | P1 | TASK-101 | Creates the Telegram module boundary for persistence state. |
| 11 | TASK-103 | P1 | TASK-102 | Moves Telegram HTTP/configuration contracts without route drift. |
| 12 | TASK-104 | P1 | TASK-103 | Moves Telegram application workflows into semantic subdomains. |
| 13 | TASK-105 | P1 | TASK-104 | Splits Bot update dispatching from transport orchestration. |
| 14 | TASK-116 | P2 | TASK-111 | Groups realtime, UI logging, push, and external operational services. |
| 15 | TASK-109 | P2 | TASK-102, TASK-103, TASK-104, TASK-105, TASK-107, TASK-108, TASK-112, TASK-114, TASK-115, TASK-116 | Mirrors all moved tests and verifies no legacy feature placement remains. |
| 16 | TASK-110 | P3 | TASK-109 | Records the final architecture and proves the full quality gate. |

## TASK-101: Characterize package-move compatibility and SRP boundaries

**Status:** DONE
**Priority:** P0  
**Depends on:** -

**Exact scope:**

Add characterization coverage for the current identity, family, admin,
Telegram, and platform public boundaries, and record the approved whole-service
target package map in the backend architecture document. The task establishes
the safety net for subsequent source moves; it does not move production classes
or alter behaviour.

**Files:**

- Modify `apps/backend/docs/ARCHITECTURE.md`.
- Modify existing focused tests under `apps/backend/src/test/java/com/sashplatonov/earnit/kids/{resource,service,repository,config,integration}/` only when an observable identity, family, admin, Telegram, or platform contract is currently untested.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java` only when the current cross-channel assertion lacks a route, authorization, or state-transition guard.
- Search anchors: `@Path` in `resource/telegram/TelegramWebhookResource.java`; `handleUpdate` in `service/telegram/TelegramBotServiceImpl.java`; `authenticate` in `service/telegram/TelegramMiniAppAuthService.java`.

**Goal:**

The existing identity, family, admin, Telegram, and platform boundaries are
observable in tests before imports and packages change.

### Outcome

An executor can move a class knowing which request/response, authentication,
authorization, family ownership, callback acknowledgement, operational, and
cross-channel state contracts must remain unchanged. The architecture document
explains the full module boundary and limited confirmed SRP extractions.

### Architectural decision

Tests remain consumers of observable contracts, never tests of private package
names. The architecture document is the sole repository documentation source
for the package map; do not create a competing design document.

### Required changes

1. Characterize successful and rejected identity login/session, family
   ownership/mutation, super-admin authorization, signed Telegram init-data,
   webhook idempotency, callback acknowledgement, and platform error contracts
   where current tests do not already prove them.
2. Document the whole-service target map and the rationale that super-admin
   analytics is a platform module, not a Telegram integration concern.
3. Record that the candidate review retained the existing thin `FamilyServiceImpl`
   facade and the bounded Telegram connection, quick-action, and outbox services.

### Out of scope

- Moving Java packages, changing CDI wiring, or changing a REST endpoint.
- Schema changes, Flyway migrations, or new test libraries.

### Acceptance criteria

- Existing identity, family, admin, Telegram, and platform endpoints retain
  their paths, status/error shape, and JSON fields for both success and expected
  authentication/validation failures.
- A repeated webhook update and an invalid/expired callback retain their current
  safe behaviour, including early callback acknowledgement where applicable.
- The document identifies the target owner and layer for Telegram and admin
  analytics without claiming a database or API change.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test
```

### Commit

```bash
git add apps/backend/docs/ARCHITECTURE.md apps/backend/src/test/java/com/sashplatonov/earnit/kids
git commit -m "test(backend): Characterize semantic module contracts"
```

## TASK-111: Establish shared and platform module boundaries

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-101

**Exact scope:**

Classify and relocate non-feature operational code into `platform` and generic
wire contracts into `shared.api`, while retaining root-only cross-cutting code.
This task covers database health/base data, HTTP metrics, UI logs, application
logs, generic persistence/result helpers, and shared error response contracts;
it does not move identity, family, admin, or Telegram business code.

**Files:**

- Move `api/UiLog*.java`, `service/database/*.java`, `service/http/*.java`, `service/observability/*.java`, `repository/PanachePagination.java`, and `service/common/{PageRequest,Pagination,ServiceResults}.java` to their matching `platform/{api,application,infrastructure}/` or `util/` package according to their existing dependency direction.
- Move `domain/model/CreatedAtEntity.java` to `platform/domain/persistence/` and `dto/response/{ErrorResponse,SimpleResponse,WebSocketEventResponse}.java` to `shared/api/response/` only after updating their framework/feature-neutral imports.
- Keep `config/{auth,locale,observability,security}/`, `exception/*.java`, `i18n/*.java`, and dependency-free `util/*.java` at root and update imports only as necessary.
- Search anchors: `CreatedAtEntity`, `PanachePagination`, `ServiceResults`, `ApplicationOutboxEvent`, `HttpRequestMetrics`, `UiLogResource`, and `DatabaseHealthService`.

**Goal:**

Operational code has a semantic home without turning core framework/configuration
types into feature code.

### Outcome

Platform operations are discoverable independently from product features;
shared errors, localization, result types, and request filters remain one
cross-cutting implementation.

### Architectural decision

`OperationResult`, exception mappers, locale context, and HTTP/security filters
remain root shared infrastructure because multiple modules consume them. Generic
HTTP records live in `shared.api`, not in a feature module. Outbox event
entities/repositories are not moved here yet: TASK-113 assigns their owner after
tracing the family write path.

### Required changes

1. Move the named operational/shared types and mirror their tests.
2. Preserve health paths, log/error payloads, metrics names, scheduler/filter
   registration, trace propagation, and transaction/error boundaries.
3. Ensure platform services do not acquire direct feature repositories or make
   product authorization decisions.

### Out of scope

- Changing logging/metrics vendors, health semantics, configuration values, or
  public operational routes.

### Acceptance criteria

- Database health, UI log ingestion, application log reads, and HTTP metrics
  expose the same authorized response/error contracts.
- Root/shared packages contain no feature-specific entity, DTO, resource, or
  application service after the move.
- Trace/security/locale filters retain their current request lifecycle behaviour.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*Database*Test,*Http*Test,*Observability*Test,*UiLog*Test,*Exception*Test,*Locale*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/api apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/{database,event,http,observability} apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform apps/backend/src/test/java/com/sashplatonov/earnit/kids/{api,service,config,exception,i18n}
git commit -m "refactor(backend): Establish platform module boundary"
```

## TASK-112: Group identity, authentication, account, and Google workflows

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-111

**Exact scope:**

Move parent account state, authentication/session application services, account
settings, Google identity verification, and their feature-exclusive HTTP
contracts/resources/repository into an `identity` module. Preserve central
auth filters and family membership ownership.

**Files:**

- Move `domain/model/ParentAccountEntity.java`, `repository/ParentAccountRepository.java`, `resource/account/AccountResource.java`, `resource/auth/*.java`, `service/account/*.java`, `service/auth/*.java`, and `service/google/*.java` to `identity/` layer packages.
- Move feature-exclusive auth/account request and response records selected by usages of `LoginRequest`, `RegisterRequest`, `ChangePasswordRequest`, `GoogleLoginRequest`, `UpdateAccountEmailRequest`, `AuthPayload`, `AuthResponse`, `AuthConfigResponse`, `AccountConnectionResponse`, `SessionPageDataResponse`, and `TokenResponse`.
- Search anchors: `ParentAccountRepository`, `AuthService`, `GoogleIdentityVerifier`, and `/api/auth`.

**Goal:**

Identity code has one owner while cookie/JWT filtering and parent family
membership compatibility continue to work unchanged.

### Outcome

Login, registration, session selection, password/account updates, and Google
sign-in are grouped as one identity module with explicit API/application/domain/
persistence boundaries.

### Architectural decision

`AuthFilter`, `JwtService`, `CookieBuilder`, and password hashing stay in root
`config.auth` as transport/security infrastructure. The identity application
module consumes them; they must not depend on identity resources. Family
membership remains in the family module and is accessed through its existing
service/repository boundary.

### Required changes

1. Move the listed identity types and only DTOs proven exclusive to this flow.
2. Preserve cookie names/attributes, CSRF checks, JWT compatibility fallback,
   login error shape, redirects, role selection, and Google verification.
3. Convert touched CDI services to constructor injection and keep expected
   failures modeled with existing `OperationResult` contracts.

### Out of scope

- Changing passwords/tokens, introducing a new identity provider, changing
  parent-membership storage, or moving Telegram Mini App authentication.

### Acceptance criteria

- All existing auth/account/Google routes retain methods, paths, response JSON,
  cookies, authorization, and expected validation/failure status codes.
- A parent account can still select an authorized family and cannot access a
  membership it does not own.
- No feature-exclusive identity entity, resource, service, repository, or DTO
  remains in a global mixed layer package.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*Auth*Test,*Account*Test,*Google*Test,SessionPageDataResourceTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/{domain,repository,resource,service,dto} apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity apps/backend/src/test/java/com/sashplatonov/earnit/kids/{domain,repository,resource,service,dto} apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity
git commit -m "refactor(backend): Group identity application module"
```

## TASK-113: Move family-owned domain state and persistence into the family module

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-101

**Exact scope:**

Move all non-Telegram, non-identity family-owned entities, enums, repositories,
repository commands/projections, and cache into `family.domain` and
`family.infrastructure.persistence`. Establish explicit subpackages for child,
membership, catalog, request, history, social, notification, and outbox state.

**Files:**

- Move `domain/model/{Family,Child,Friend,History,PurchaseRequest,ShopItem,Task,FamilyNotificationPreference,FamilyParentMembership,ApplicationOutboxEvent}*.java` and their supporting statuses/enums to `family/domain/model/` subpackages.
- Move `repository/{Family,Child,Friend,History,PurchaseRequest,ShopItem,Task,FamilyNotificationPreference,FamilyParentMembership,ApplicationOutboxEvent}*.java`, `repository/cache/FamilyDbIdCache.java`, `repository/command/*.java`, and `repository/projection/*.java` to `family/infrastructure/persistence/`.
- Keep `ParentAccountEntity`/repository for TASK-112 and all `Telegram*` types for TASK-102.
- Search anchor: `domain.model.(Family|Child|Task|ShopItem|PurchaseRequest|History|Friend|ApplicationOutbox)` and the matching repository imports.

**Goal:**

The whole family business state is co-located by semantic area without changes
to tables, query behaviour, or ownership constraints.

### Outcome

Family data persistence can be found by domain instead of by global technical
layer, and no migration is required for a Java package relocation.

### Architectural decision

Family owns outbox events because they originate from family state transitions;
platform consumes the published events. Repositories remain Panache persistence
adapters and retain all current family-id predicates, indexes, transactions,
and query projections.

### Required changes

1. Perform a package-only move for the named state/repository groups and update
   imports across identity, Telegram, platform, admin, and tests.
2. Preserve entity annotations, table/column names, queries, pagination,
   transactional writes, and DTO mapping ownership.
3. Keep scalar foreign-key design and prevent repositories from assembling HTTP
   responses or applying business authorization.

### Out of scope

- Flyway changes, schema/index changes, query redesign, or moving Telegram and
  identity entities.

### Acceptance criteria

- H2 and PostgreSQL migration baselines start with the same schema and all
  existing family persistence tests retain their results/order.
- Every family repository query still scopes data by family/child ownership.
- No core family entity or repository remains in global `domain/model` or
  `repository` after the task, except explicitly retained shared infrastructure.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*Family*Test,*Child*Test,*History*Test,*Task*Test,*ShopItem*Test,*PurchaseRequest*Test,*Friend*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure apps/backend/src/test/java/com/sashplatonov/earnit/kids/{domain,repository} apps/backend/src/test/java/com/sashplatonov/earnit/kids/family
git commit -m "refactor(backend): Isolate family persistence module"
```

## TASK-114: Move family API contracts and read-side resources by domain

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-113

**Exact scope:**

Move feature-exclusive family DTO records and the existing read, child settings,
parent access, notification, and social resources into `family.api`; keep the
multi-workflow command resource for the SRP split in TASK-108.

**Files:**

- Move family request/response records identified by `CreateChildRequest`, `UpdateChildSettingsRequest`, `Update*`, `AddFriendRequest`, `AddParentMembershipRequest`, `ParentInviteAcceptRequest`, `Bulk*`, `Import*`, `Family*Response`, `Child*`, `History*`, `ParentMembershipDto`, `RequestDto`, `ShopItemDto`, `TaskDto`, and pagination types to `family/api/{request,response}/`.
- Move `resource/family/FamilyReadResource.java`, `FamilyChildSettingsResource.java`, `FamilyParentAccessResource.java`, `FamilyNotificationResource.java`, `FamilySocialResource.java`, and `FamilyResourceSupport.java` to `family/api/resource/`.
- Search anchor: `@Path` under `resource/family/` and each listed DTO type under `dto/`.

**Goal:**

Family clients use the same HTTP contracts, now grouped with their owning
domain; request/response types are not scattered globally.

### Outcome

Family read/settings/membership/notification/social APIs have a single module
home and retain their established service boundaries.

### Architectural decision

Move only feature-exclusive DTOs. `ErrorResponse`, `SimpleResponse`, and
`WebSocketEventResponse` move through TASK-111 as generic shared contracts;
`AccountConnectionResponse` stays with identity and Telegram-specific responses
including `ChildTelegramConnectionResponse` move through TASK-103. Resources
use existing auth/result helpers rather than creating family-specific copies.

### Required changes

1. Move records/resources and their tests, updating imports in web-facing,
   Telegram, identity, and WebSocket callers.
2. Preserve routes, OpenAPI annotations, `@Valid`, role checks, CSRF filtering,
   deterministic pagination, and the standard error response.
3. Retain parent membership/account authorization and child server-side scope.

### Out of scope

- Moving/splitting `FamilyResource` command routes (TASK-108), changing DTO
  field names, or adding API versions.

### Acceptance criteria

- Family read/settings/social/notification/parent-access endpoints retain route,
  body, status, pagination, ownership, and error behaviour.
- Existing web and Telegram callers compile against one canonical family DTO,
  with no duplicate record introduced.
- A child/parent cannot access another family through a moved resource.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*Family*ResourceTest,*Family*Service*Test,*Child*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api apps/backend/src/test/java/com/sashplatonov/earnit/kids/{dto,resource/family} apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api
git commit -m "refactor(backend): Move family API module"
```

## TASK-115: Group family application services by bounded workflow

**Status:** DONE
**Priority:** P2  
**Depends on:** TASK-113

**Exact scope:**

Move the existing family, analytics, imports, and WebSocket family-notification
collaborators into semantic `family.application` subpackages, retaining the
documented `FamilyServiceImpl` facade and existing action/command/dashboard
splits.

**Files:**

- Move `service/family/**/*.java`, `service/analytics/*.java`, and `service/imports/ImportRowValidator.java` to `family/application/` subpackages that mirror `action`, `command`, `dashboard`, `history`, `membership`, `notification`, `social`, and `analytics` responsibilities.
- Move `resource/family/FamilyWebSocket.java` only after its service dependency is in `family.application`; retain it in `family/api/realtime/` with its current endpoint contract.
- Search anchors: `FamilyServiceImpl`, `FamilyActionServiceImpl`, `FamilyCommandServiceImpl`, `FamilyDashboardQueryServiceImpl`, and `AnalyticsServiceImpl`.

**Goal:**

Family business orchestration is grouped by use case while its existing facade
and shared ownership guards keep caller compatibility.

### Outcome

The current already-extracted family action/command/dashboard collaborators are
preserved as intentional semantic subdomains, rather than being mixed with
unrelated account, Telegram, analytics, or platform services.

### Architectural decision

`FamilyServiceImpl` remains the compatibility façade defined in architecture
documentation; it delegates but is not a new God class. Do not extract shared
business rules into generic utilities when an existing family collaborator owns
them. Family analytics is distinct from super-admin analytics in TASK-106.

### Required changes

1. Move services/interfaces/tests with their bounded workflow packages.
2. Preserve `OperationResult`, family/child guards, transaction boundaries,
   outbox publication, dashboard hydration/mapping, and import validation.
3. Convert any touched field injection to constructor injection without adding
   new dependencies or changing service public contracts.

### Out of scope

- Redesigning dashboard payloads, business rules, transactions, WebSocket
  messages, or splitting the planned command resource before TASK-108.

### Acceptance criteria

- Dashboard/read, task/shop/request mutations, imports, history, social,
  notification, and family analytics retain their current observable results.
- Family service callers retain one canonical interface and do not access a
  repository directly after the move.
- No family application service remains in global `service/family`,
  `service/analytics`, or `service/imports` packages.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*Family*Test,*Analytics*Test,*Import*Test,*WebSocket*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/{family,analytics,imports} apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyWebSocket.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/realtime apps/backend/src/test/java/com/sashplatonov/earnit/kids/{service,resource} apps/backend/src/test/java/com/sashplatonov/earnit/kids/family
git commit -m "refactor(backend): Group family application workflows"
```

## TASK-116: Isolate realtime, push, and external integration adapters

**Status:** DONE
**Priority:** P2  
**Depends on:** TASK-111

**Exact scope:**

Move application-wide WebSocket session/notification, push, and external HTTP
adapter types into explicit platform realtime/infrastructure packages, leaving
Telegram Bot transport in its Telegram module and Google identity in `identity`.

**Files:**

- Move `service/websocket/*.java`, `resource/common/PushResource.java`, and their tests to `platform/{realtime,api}/`.
- Move `service/http/HttpResponsePayloadEstimator.java` with the remaining platform HTTP types if TASK-111 did not already move it.
- Keep `resource/common/ClientErrorResource.java` with root exception/error infrastructure unless usage analysis shows it is platform-only.
- Search anchors: `WebSocketNotificationService`, `WebSocketSessionInfo`, `PushResource`, and `HttpResponsePayloadEstimator`.

**Goal:**

Realtime/push/external operational adapters have one platform owner and do not
get mixed into family, Telegram, identity, or generic resource/service folders.

### Outcome

Cross-feature delivery infrastructure is separate from product-domain state,
while all existing socket and push consumers retain their contracts.

### Architectural decision

This task draws adapter boundaries only. It does not merge WebSocket and
Telegram delivery: Telegram-specific Bot API/outbox remains `telegram`, while
general realtime delivery is platform infrastructure.

### Required changes

1. Move the named types and mirror their tests/imports.
2. Preserve endpoint/socket paths, authentication checks, targeted session
   delivery, error mapping, and no-cross-family broadcast guarantees.
3. Keep business decisions in family/identity/Telegram services rather than in
   push or socket adapters.

### Out of scope

- New WebSocket protocol, push provider, reactive migration, or changing
  Telegram delivery scheduling.

### Acceptance criteria

- Authenticated socket/push paths retain current connection, authorization,
  message/error, and disconnect behaviour.
- Platform delivery cannot broadcast data beyond the authenticated family/user
  scope and does not depend on Telegram-specific transport classes.
- The final package map distinguishes general realtime from Telegram Bot API.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*WebSocket*Test,*Push*Test,*Http*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/{service/websocket,resource/common,service/http} apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform apps/backend/src/test/java/com/sashplatonov/earnit/kids/{service/websocket,resource/common,service/http} apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform
git commit -m "refactor(backend): Isolate realtime platform adapters"
```

## TASK-102: Move Telegram domain state and persistence behind the Telegram module

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-101

**Exact scope:**

Relocate all Telegram-owned JPA entities and Panache repositories from the
global mixed packages into `telegram.domain.model` and
`telegram.infrastructure.persistence`, then update only their direct imports.

**Files:**

- Move `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/Telegram*.java` to `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/domain/model/`.
- Move `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/Telegram*.java` to `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/infrastructure/persistence/`.
- Modify imports in current Telegram services/resources and the two known core consumers: `service/account/AccountServiceImpl.java` and `service/family/FamilyParentAccessServiceImpl.java`.
- Search anchor: `domain.model.Telegram` and `repository.Telegram` under `apps/backend/src/main/java/`.

**Goal:**

Telegram persistence has one discoverable owner without changing Hibernate
metadata, table access, lookup semantics, or transactions.

### Outcome

All Telegram entity/repository imports resolve from the module, while core
account and family services use those repositories only for the existing
cross-domain projections they already require.

### Architectural decision

Entities remain JPA entities with their current table/column annotations;
repositories remain persistence-only Panache adapters. This is a package move,
not a new bounded database or a migration.

### Required changes

1. Move the eight `Telegram*Entity` types and eight `Telegram*Repository` types
   as one persistence boundary, updating package declarations and imports.
2. Preserve repository query predicates, parameterization, pagination, write
   transactions, table names, and entity identifiers byte-for-byte except for
   import/package changes needed to compile.
3. Update account/family imports without moving their authoritative account or
   membership state into Telegram.
4. Move matching repository tests with their source package only if their
   package declaration otherwise no longer mirrors production.

### Out of scope

- Changing entity fields, constraints, indexes, SQL/JPQL, or Flyway migrations.
- Moving non-Telegram family, account, outbox-event, request, or child entities.

### Acceptance criteria

- Hibernate starts with the same Telegram tables and no schema migration is
  added or modified.
- Telegram identity, invitation, callback, delivery, audit, and webhook-update
  repository operations yield the same results for valid and invalid inputs.
- Core family and account flows still resolve linked Telegram profile data
  without N+1 replacement queries or ownership bypasses.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='TelegramWebhookUpdateRepositoryTest,*TelegramIdentity*Test,*TelegramParentInvitation*Test,*TelegramAccountConnection*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/Telegram*.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/Telegram*.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/account/AccountServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyParentAccessServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids
git commit -m "refactor(backend): Isolate telegram persistence module"
```

## TASK-103: Move Telegram HTTP contracts, resources, and configuration

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-102

**Exact scope:**

Move Telegram-specific request/response records, six Telegram JAX-RS resources,
and `TelegramConfig` into `telegram.api` and `telegram.config`. Keep resources
thin and retain every current external route.

**Files:**

- Move `dto/request/CreateTelegramParentInviteRequest.java` and `TelegramLinkCompletionRequest.java` to `telegram/api/request/`.
- Move `dto/response/ChildTelegramConnectionResponse.java`, `TelegramAccountConnectionResponse.java`, `TelegramLinkLaunchResponse.java`, and `TelegramQuickActionResponse.java` to `telegram/api/response/`.
- Move `config/TelegramConfig.java` to `telegram/config/TelegramConfig.java`.
- Move `resource/telegram/TelegramAccountConnectionResource.java`, `TelegramChildConnectionResource.java`, `TelegramMiniAppAuthResource.java`, `TelegramParentInviteAcceptResource.java`, `TelegramParentInviteResource.java`, and `TelegramWebhookResource.java` to `telegram/api/resource/`.
- Search anchor: `TelegramConfig`, `CreateTelegramParentInviteRequest`, and `TelegramLinkLaunchResponse` under `apps/backend/src/{main,test}/java/`.

**Goal:**

All transport-facing Telegram types are co-located without any client-visible
HTTP, validation, authentication, CSRF, or OpenAPI change.

### Outcome

The root `dto` and `resource` packages no longer contain Telegram-only public
contracts, while each original endpoint remains reachable at its existing URL.

### Architectural decision

HTTP DTO records belong to the Telegram API layer, not to persistence or
application services. Resources continue to obtain identity from `AuthContext`,
validate request bodies, and delegate to application services; no Telegram
credential validation moves into a resource.

### Required changes

1. Relocate the listed records, config mapping, resources, and corresponding
   test packages/imports.
2. Preserve all bean-validation annotations, `@Valid` usage, OpenAPI metadata,
   error mapping through `OperationResultResponses`, and mutating-endpoint CSRF
   filtering.
3. Keep `ParentMembershipDto` in the family API and `AccountConnectionResponse`
   in the identity API, despite their optional Telegram presentation fields;
   update imports rather than creating Telegram copies.
4. Use constructor injection for resources touched by the move when changing
   their injection code; do not introduce field injection.

### Out of scope

- Renaming routes, JSON properties, OpenAPI tags, cookie names, or auth filters.
- Moving super-admin analytics resources; that occurs in TASK-107.

### Acceptance criteria

- Every existing Telegram REST path returns the same status and response shape
  for authorized, unauthorized, invalid-body, and expected business-failure
  requests.
- Signed init-data is still verified before a Mini App session or parent invite
  acceptance can succeed; raw init-data and tokens never appear in logs.
- No globally packaged Telegram-only request, response, resource, or config
  type remains after the task, apart from explicitly documented shared contracts.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='TelegramAccountConnectionResourceTest,TelegramChildConnectionResourceTest,TelegramMiniAppAuthResourceTest,TelegramParentInviteAcceptResourceTest,TelegramWebhookResourceTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/TelegramConfig.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram
git commit -m "refactor(backend): Move telegram API boundary"
```

## TASK-104: Group Telegram application services by workflow

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-103

**Exact scope:**

Relocate the existing Telegram application classes into semantic subpackages,
keeping their current public service contracts and behaviour. The move covers
identity, invitation, connection, Mini App auth, callbacks, notification/outbox,
and menu/quick-action workflows.

**Files:**

- Move `service/telegram/TelegramIdentityService*.java`, `TelegramInitDataVerifier.java`, `TelegramTokenDigest.java`, and `TelegramSecurityAuditWriter.java` to `telegram/application/identity/`.
- Move `TelegramChildInvitationOperations.java`, `TelegramParentInvitationService*.java`, and `TelegramInviteToken.java` to `telegram/application/invitation/`.
- Move `TelegramAccountConnectionService*.java`, `TelegramChildConnectionService*.java`, `TelegramFeatureGate.java`, and `TelegramFeatureSupport.java` to `telegram/application/connection/`.
- Move `TelegramMiniAppAuthService.java` to `telegram/application/auth/`; move `TelegramCallback*.java`, `TelegramMutationCallbackOperations.java`, and `TelegramActionIdParser.java` to `telegram/application/callback/`.
- Move `TelegramDeliveryPlanner.java`, `TelegramNotificationComposer.java`, `TelegramObservability.java`, `TelegramOutboxProcessor.java`, and `TelegramRetention*.java` to `telegram/application/notification/`.
- Move menu, copy, reply-keyboard, quick-action, child/parent action-handler, deep-link, and view-support classes to `telegram/application/bot/`.
- Search anchor: `package com.sashplatonov.earnit.kids.service.telegram` in production and test sources.

**Goal:**

Each Telegram workflow has a discoverable owner, while bot actions continue to
delegate to the existing family/account service interfaces rather than gaining
their own business state.

### Outcome

The former flat Telegram service directory is replaced by bounded application
workflows. `TelegramIdentityServiceImpl` remains a narrow identity facade over
the existing invitation and mutation collaborators, rather than accumulating
new responsibilities during the move.

### Architectural decision

This task is a semantic package migration, not a rewrite. `OperationResult`,
transactional methods, audit events, callback encoding, and outbound delivery
semantics retain their current owner. Application services use constructor
injection when touched; helper classes remain concrete only when stateless and
not a service boundary.

### Required changes

1. Apply the workflow package map and update imports in resources, core family/
   account services, schedulers, and tests.
2. Preserve delegation from quick actions to `FamilyService`/
   `FamilyActionService`, and preserve account/family ownership checks as the
   source of truth.
3. Convert touched field-injected Telegram CDI services to explicit constructor
   injection without changing their dependencies or CDI scope.
4. Ensure writes retain their existing atomic transaction boundary; no callback,
   invitation, connection, or outbox workflow may silently become non-atomic.

### Out of scope

- Splitting `TelegramAccountConnectionServiceImpl`, `TelegramQuickActionServiceImpl`,
  or `TelegramOutboxProcessor` solely by line count.
- Changing Bot API requests, retry policy, callback-data format, feature flags,
  or scheduling cadence.

### Acceptance criteria

- Parent/child linking, invitation issue/accept/revoke, Mini App authentication,
  callback consumption, and outbox delivery retain their current success and
  expected failure behaviour.
- A Telegram action cannot mutate another family or child and does not create a
  duplicate source of truth for balance, requests, or memberships.
- All refactored CDI services use constructor injection; no new warning
  suppression or quality exclusion is introduced.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*Telegram*Test,TelegramCrossChannelIntegrationTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/account apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java
git commit -m "refactor(backend): Group telegram application workflows"
```

## TASK-105: Split Telegram Bot update dispatch from bot transport orchestration

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-104

**Exact scope:**

Extract the independently changing update-routing responsibilities from
`TelegramBotServiceImpl` into focused bot application collaborators, retaining
one thin `TelegramBotService` entry point for webhook delivery.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotService.java` and `TelegramBotServiceImpl.java` after TASK-104.
- Create `telegram/application/bot/TelegramMessageUpdateHandler.java`.
- Create `telegram/application/bot/TelegramCallbackUpdateHandler.java`.
- Create `telegram/application/bot/TelegramStartCommandHandler.java` only if start-command ownership is not naturally contained by the message handler.
- Modify `telegram/application/bot/TelegramChildActionHandler.java`, `TelegramParentRequestHandler.java`, `TelegramReplyKeyboardNavigator.java`, and `telegram/infrastructure/botapi/TelegramBotApiClient.java` only as needed for constructor wiring.
- Modify the relocated `TelegramBotServiceImplTest.java` and `TelegramBotBoundaryTest.java`.

**Goal:**

Webhook processing has one public orchestrator and distinct handlers for
message/start and callback workflows, making callback acknowledgement and role
checks independently testable.

### Outcome

`TelegramBotServiceImpl` parses the update envelope, routes it, and owns the
unexpected-failure boundary. Handlers own their respective update semantics;
the Bot API client remains a transport adapter only.

### Architectural decision

Do not create a second webhook resource or a second callback codec. The
existing callback verification/consumption service is the sole callback-data
authority. `TelegramBotService` remains the scheduling/webhook-facing service
contract to preserve CDI and resource wiring.

### Required changes

1. Extract message/start and callback routing with constructor-injected
dependencies and explicit typed inputs rather than passing business decisions
through raw JSON across multiple services.
2. Keep immediate callback acknowledgement before longer mutation/navigation
   work, including invalid or expired callbacks.
3. Preserve error logging without raw token, init-data, or personally
   identifying payload leakage; retain the request trace context.
4. Add focused unit tests for route selection, invalid update rejection, valid
   child/parent callback handling, and acknowledgement ordering.

### Out of scope

- New Telegram Bot API endpoints, polling, asynchronous execution, or changes
  to menu copy and keyboard layout.

### Acceptance criteria

- A valid message, `/start` command, and callback reach the same observable bot
  response as before; an unsupported update is handled safely.
- Callback acknowledgement occurs once and before any slow or failing mutation
  path; invalid/expired callback data never performs a mutation.
- Webhook request handling still has one public entry point and no handler
  performs direct family/repository persistence outside the established service
  path.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='TelegramBotServiceImplTest,TelegramBotBoundaryTest,TelegramCallbackServiceTest,TelegramReplyKeyboardNavigatorTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot
git commit -m "refactor(backend): Split telegram bot update handlers"
```

## TASK-106: Split super-admin analytics persistence by metric family

**Status:** DONE
**Priority:** P1  
**Depends on:** TASK-101

**Exact scope:**

Replace the 1,012-line multi-domain `AdminAnalyticsRepository` with focused
repositories for overview/activation, coin economy, tasks/rewards, parent and
child behaviour, retention, and trends. Preserve the service-level public
analytics contracts.

**Files:**

- Replace `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/AdminAnalyticsRepository.java` with repositories under `apps/backend/src/main/java/com/sashplatonov/earnit/kids/admin/infrastructure/persistence/`.
- Modify the existing admin analytics services currently under `service/telegram/admin/` only to inject the focused repository that owns each metric family.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/admin/AdminAnalyticsServicesTest.java` and `AdminDashboardServiceTest.java`.
- Search anchors: `getOverview`, `getCoinMetrics`, `getTaskMetrics`, `getParentBehaviorMetrics`, `getChildBehaviorMetrics`, `getActivationFunnel`, and `getTrendPoints`.

**Goal:**

Each analytics query family has a focused persistence owner, avoiding a
God-class repository without duplicating aggregation logic.

### Outcome

Super-admin analytics returns the same metrics, ordering, null/zero handling,
and period boundaries through smaller repositories with clear query ownership.

### Architectural decision

Repositories stay persistence-only and return the existing response projections
or primitives currently consumed by the service layer. Do not move calculation
rules into resource classes, duplicate SQL across repositories, or change the
database schema. Shared period parsing remains with the existing application
service/`AdminAnalyticsPeriod` contract.

### Required changes

1. Partition the current methods by the metric families named in scope, with
   each query method owned exactly once.
2. Preserve native-query parameterization, period inclusivity, ranking order,
   median behaviour, and zero-denominator semantics.
3. Update only the dependent service constructors and unit mocks; resources
   keep their existing API/service boundary.
4. Add tests that prove every focused repository collaborator supplies the
   same service response for representative empty and populated periods.

### Out of scope

- Changing metric definitions, response DTO fields, `/api/super/*` paths,
  indexes, caching, or analytics UI behaviour.

### Acceptance criteria

- Every currently exposed analytics response preserves field names, values,
  deterministic ranking/trend order, and period-scoped behaviour.
- Empty datasets return the current safe zero/empty results and never expose
  persistence exceptions as ad-hoc HTTP errors.
- No replacement repository mixes unrelated metric families or copies a query
  already owned by another focused repository.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='AdminAnalyticsServicesTest,AdminDashboardServiceTest,AdminAnalyticsPeriodTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/AdminAnalyticsRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/admin/infrastructure/persistence apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/admin
git commit -m "refactor(backend): Split admin analytics repositories"
```

## TASK-107: Move super-admin analytics into the admin module

**Status:** DONE
**Priority:** P2  
**Depends on:** TASK-106

**Exact scope:**

Move the existing super-admin analytics resources and services from their
Telegram-named locations into `admin.api` and `admin.application`; update tests
to mirror the new module after repository extraction.

**Files:**

- Move `resource/telegram/admin/*.java` to `admin/api/resource/`.
- Move `service/telegram/admin/*.java` to `admin/application/`.
- Move matching `src/test/java/.../resource/telegram/admin/` and `service/telegram/admin/` tests to the mirrored `admin/` packages.
- Search anchor: `@Path("/api/super` in `resource/telegram/admin/`.

**Goal:**

Super-admin operations are discoverable as a platform module, not embedded in
Telegram integration, while all existing administration routes remain stable.

### Outcome

Admin analytics, access, retention, and dashboard services have one semantic
home and continue to use the repositories established in TASK-106.

### Architectural decision

The `admin` module is independent from Telegram transport and cannot import
Telegram identity, bot, callback, or delivery services. Existing response DTOs
remain at their current shared API location until a separate contract-ownership
task proves they are feature-exclusive.

### Required changes

1. Move production and test packages, then update CDI/resource imports.
2. Preserve `@Path`, OpenAPI annotation content, access checks, error responses,
   period validation, and response serialization.
3. Use only `admin.application` services from admin resources; no resource may
   query the focused repositories directly.

### Out of scope

- Changing super-admin permissions, introducing API versioning, or moving
  family/admin DTOs solely for folder symmetry.

### Acceptance criteria

- Every `/api/super/*` endpoint retains the same authorization and observable
  response for valid, invalid-period, and unauthorized requests.
- The admin module has no dependency on Telegram Bot API, webhook, callback,
  signed-init-data, or Telegram persistence types.
- Admin test packages mirror their production package paths.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='Admin*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/telegram/admin apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/admin apps/backend/src/main/java/com/sashplatonov/earnit/kids/admin apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/telegram/admin apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/admin apps/backend/src/test/java/com/sashplatonov/earnit/kids/admin
git commit -m "refactor(backend): Move super admin analytics module"
```

## TASK-108: Split family command resources by bounded workflow

**Status:** DONE
**Priority:** P2  
**Depends on:** TASK-114

**Exact scope:**

Split `FamilyResource`, which currently owns twelve unrelated task, shop,
request, import, history, and balance command routes, into focused family API
resources while preserving its shared URL prefix and service delegation.

**Files:**

- Replace the `FamilyResource` location established by TASK-114 with focused resources under `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/`.
- Create at minimum `FamilyTaskActionResource.java`, `FamilyShopActionResource.java`, `FamilyRequestActionResource.java`, `FamilyImportResource.java`, `FamilyBalanceResource.java`, and `FamilyHistoryResource.java`.
- Modify `resource/family/FamilyResourceSupport.java` only if it is the existing shared non-business resource helper; do not create duplicate auth/result helpers.
- Search anchors: `/tasks/`, `/shop/`, `/requests/`, `/history/`, and `/balance/adjust` in the current `FamilyResource.java`.
- Modify or move the corresponding resource tests under `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/family/`.

**Goal:**

Each family command route has a resource dedicated to one workflow and remains
identical to existing web and Telegram callers.

### Outcome

Resources have focused responsibilities, but every URL, body contract,
authorization check, response status, and transaction outcome is preserved.

### Architectural decision

The family application services remain the business owner. New resources reuse
`ResourceAuthSupport`, `FamilyResourceSupport`, and `OperationResultResponses`
where those existing helpers fit; they must not copy authorization, response
mapping, request validation, or family ownership logic.

### Required changes

1. Assign each current route to exactly one focused resource and retain the
   class-level/base path needed to keep the full URI unchanged.
2. Preserve role extraction, viewer/editor restrictions, `@Valid` request
   handling, operation-result status mapping, and OpenAPI reachable responses.
3. Keep task/shop/import/request/history/balance business operations in their
   current application services and transactions.
4. Add route-level regression tests for one successful and one rejected request
   per extracted workflow, including cross-family/child ownership rejection.

### Out of scope

- Changing `FamilyServiceImpl`, task/shop/request domain state, DTO JSON, or
  adding routes, migrations, frontend changes, or parallel command services.

### Acceptance criteria

- Every prior family command URL accepts the same method/path/body and yields
  the same success payload, HTTP status, and standardized error shape.
- A viewer or cross-family/child request remains unable to mutate data after
  the resource split.
- Each resource has one workflow responsibility and contains no direct
  repository call or duplicated result/auth helper.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*Family*ResourceTest,*FamilyAction*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResourceSupport.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/family apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource
git commit -m "refactor(backend): Split family command resources"
```

## TASK-109: Complete source/test package migration and enforce boundary absence

**Status:** DONE
**Priority:** P2  
**Depends on:** TASK-102, TASK-103, TASK-104, TASK-105, TASK-107, TASK-108, TASK-112, TASK-114, TASK-115, TASK-116

**Exact scope:**

Move remaining affected tests to mirror every production module, delete empty
legacy package directories through Git, and add lightweight architecture tests
that prevent reintroduction of feature-owned types into global mixed packages.

**Files:**

- Move remaining tests under the former global `domain`, `dto`, `repository`, `resource`, and `service` trees to their mirrored `identity`, `family`, `admin`, `telegram`, or `platform` paths.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java` and other integration tests only for required package/import moves.
- Create module boundary tests under `src/test/java/com/sashplatonov/earnit/kids/{identity,family,admin,telegram,platform}/` using existing test dependencies only.
- Search anchors: `com.sashplatonov.earnit.kids.(service|resource|repository|domain|dto)` under `apps/backend/src/{main,test}/java/`.

**Goal:**

The final source tree and tests make all ownership visible and detect accidental
return of feature-owned types to global mixed packages.

### Outcome

Production and test package trees mirror each other, and automated checks reject
legacy global feature placement while permitting explicitly retained shared
cross-channel contracts.

### Architectural decision

Use small repository-source/package assertions or reflection-based tests with
the project’s existing JUnit facilities; do not add ArchUnit solely for this
refactor. The allowlist is limited to the architectural decisions above and must
name each shared contract explicitly.

### Required changes

1. Finish moving tests and update package declarations/imports so test packages
   mirror the final production paths.
2. Add boundary tests with a precise allowlist for root cross-cutting and shared
   cross-channel contracts.
3. Verify searches find no legacy global feature package declarations or imports.
4. Remove only now-empty directories/files created by the migration; do not
   delete unrelated user changes.

### Out of scope

- New static-analysis dependencies, package rules beyond the explicitly named
  modules, or a second implementation of any feature.

### Acceptance criteria

- All feature-owned production/test types reside in their owning module, except
  named shared contracts in the documented allowlist.
- Boundary tests fail if a new feature entity, repository, resource,
  configuration type, or HTTP contract is placed back in a global mixed package.
- Existing cross-channel integration behaviour remains green after test moves.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*ModuleBoundaryTest,*Identity*Test,*Family*Test,Admin*Test,*Telegram*Test,*Platform*Test,TelegramCrossChannelIntegrationTest'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids apps/backend/src/test/java/com/sashplatonov/earnit/kids
git commit -m "test(backend): Enforce semantic module boundaries"
```

## TASK-110: Publish final semantic architecture and pass full backend quality gates

**Status:** IN_PROGRESS
**Priority:** P3  
**Depends on:** TASK-109

**Exact scope:**

Document the completed whole-service semantic module map and run the full
configured backend quality sequence. Repair only failures caused by this
refactor in the owning task/module; do not hide failures with exclusions or
broaden the refactor.

**Files:**

- Modify `apps/backend/docs/ARCHITECTURE.md`.
- Modify only files implicated by a reproducible quality failure from TASK-102 through TASK-109.
- Search anchor: `Package Structure` and `Current split notes` in `apps/backend/docs/ARCHITECTURE.md`.

**Goal:**

The repository documents the delivered module boundaries and proves the
refactor meets the existing Java 25/Quarkus quality gates.

### Outcome

Maintainers can find each module’s API/application/domain/infrastructure owner,
and the full backend build verifies compilation, tests, Checkstyle, PMD,
JaCoCo, SpotBugs, and Quarkus wiring.

### Architectural decision

Quality failures are corrected at their root owner. No new suppressions,
Checkstyle/PMD/SpotBugs excludes, lowered coverage thresholds, or test skips are
allowed. This task does not change deployment state; local verification is not
remote CI or production proof.

### Required changes

1. Update architecture documentation to reflect only the final, implemented
   package map and the resource-to-service-to-repository boundaries.
2. Run the full sequence below from `apps/backend`.
3. If a gate fails due to the refactor, create a narrowly scoped follow-up
   correction in the same task only when it does not introduce another
   architectural outcome; otherwise checkpoint the failure with its command and
   output for a new backlog item.

### Out of scope

- Deployment, Docker rebuilds, frontend work, database schema changes, or
  unrelated quality cleanup.

### Acceptance criteria

- The architecture document matches the actual source package declarations and
  does not retain obsolete global Telegram/admin locations.
- `validate`, `test`, `verify`, and `quarkus:build` pass with Java 25 preview
  enabled and without suppressions/exclusions added by this backlog.
- Verification is reported as local backend proof only, separately from CI or
  deployed-runtime proof.

### Targeted validation

```bash
cd apps/backend && export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" && export PATH="$JAVA_HOME/bin:$PATH" && ./mvnw validate && ./mvnw test && ./mvnw verify && ./mvnw quarkus:build
```

### CHECKPOINT

- Completed: updated `apps/backend/docs/ARCHITECTURE.md` with the implemented
  semantic module map, ownership boundaries, and current test locations.
- Remaining: restore the full JaCoCo line-coverage gate without adding
  exclusions, suppressions, lowered thresholds, or skipped tests.
- Changed files: `apps/backend/docs/ARCHITECTURE.md`.
- Verification: `validate` passed; `test` passed with 544 tests; `verify`
  reached Quarkus augmentation but failed at JaCoCo check with 0.72 coverage
  against the required 0.80; Checkstyle reported 0 violations.
- Confirmed blocker: semantic persistence classes moved out of the old
  `repository/**` JaCoCo exclusion are largely uncovered; the current gate
  therefore fails after TASK-102 through TASK-109.
- Next exact action: add focused coverage for the moved persistence owners,
  rerun the full validation sequence, then mark this task `DONE` only after
  `verify` and `quarkus:build` pass.

### Commit

```bash
git add apps/backend/docs/ARCHITECTURE.md apps/backend/src/main/java apps/backend/src/test/java
git commit -m "docs(backend): Document semantic module architecture"
```
