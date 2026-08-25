# EarnIt Kids Backend Architecture

<a name="top"></a>

## Table of Contents
- [🧭 Scope](#-scope)
- [📦 Package Structure](#-package-structure)
- [🧩 Layer Responsibilities](#-layer-responsibilities)
- [🗄️ Database Overview](#️-database-overview)
- [🔐 Authentication and Authorization](#-authentication-and-authorization)
- [🧾 API Versioning Strategy](#-api-versioning-strategy)
- [📘 Runtime API Docs](#-runtime-api-docs)
- [🧪 Verification](#-verification)

## 🧭 Scope

`apps/backend` is a Quarkus service that owns the authoritative business state for EarnIt Kids.

- Authentication, cookies, CSRF, and role scope
- Parent, child, and super-admin API flows
- Catalog, history, requests, analytics, and super-admin operations
- Flyway migrations and database integration

[↩ Back to toc](#table-of-contents)

## 📦 Package Structure

The production source uses semantic modules for feature ownership and keeps
only genuinely cross-cutting packages at the service root:

```text
com.sashplatonov.earnit.kids/
  identity/{api,application,domain,infrastructure}
  family/{api,application,domain,infrastructure}
  admin/{api,application,infrastructure}
  telegram/
    api/{resource,request,response}
    application/{auth,bot,callback,connection,identity,invitation,notification}
    config/
    domain/model/
    infrastructure/persistence/
  platform/{api,application,domain,infrastructure,realtime}
  shared/api/response/
  config/ exception/ i18n/ util/
  dto/response/ resource/common/ service/event/
```

`identity`, `family`, `admin`, `telegram`, and `platform` own their API,
application, domain, and infrastructure code as shown above. The final root
packages are limited to security/configuration, exception and locale support,
dependency-free utilities, shared response contracts, and a small set of
cross-feature compatibility types. No Telegram or admin production class is
owned by the obsolete global feature packages.

The package map changes Java ownership and imports only. REST routes, JSON,
CDI, persistence, migrations, callback encoding, signed init-data, cookies,
and CSRF contracts remain unchanged.

Boundary ownership decisions:

- `identity` owns authentication/session lifecycle, parent accounts, Google
  sign-in, and identity-only token utilities. Cookie/JWT filters remain in
  root `config` as transport/security infrastructure.
- `family` owns family, child, membership, notification preference, friend,
  task, shop, request, and history state and their APIs. Telegram calls these
  established business services rather than duplicating ownership or balance
  rules.
- `telegram` owns Telegram identity, invitations, callbacks, delivery, audit,
  and webhook-update state; its resources, orchestration, entities, and
  repositories stay separated by API, application, domain, and infrastructure
  layers.
- `admin` owns super-admin HTTP, application, and analytics query
  infrastructure. It is not a Telegram integration.
- `platform` owns database health/base data, outbox publishing, HTTP metrics,
  bounded application diagnostics, push, WebSocket delivery, and structured
  operational logging. Root `exception`, `i18n`, and dependency-free `util`
  remain genuinely cross-cutting.

The existing thin `FamilyServiceImpl` facade remains a compatibility boundary.
The bounded `TelegramQuickActionServiceImpl`,
`TelegramAccountConnectionServiceImpl`, and `TelegramOutboxProcessor` also
remain intact. The only confirmed SRP extractions are the multi-metric admin
analytics repository and the multi-workflow family and Telegram Bot resources.

### Characterized observable contracts

The pre-move test safety net covers the following public boundaries:

| Boundary | Characterization | Existing focused coverage |
| --- | --- | --- |
| Identity | Successful and rejected parent/child login, family selection, session cookies, logout, password and auth configuration responses | `identity/api/resource/auth/AuthResourceTest`, `config/auth/*Test` |
| Family | Family-scoped reads and mutations, validation failures, child ownership, parent membership permissions, and websocket response behavior | `family/api/resource/*Test`, `family/application/*Test` |
| Admin | Super-admin authorization, period normalization, invalid input status, and dashboard response contract | `admin/api/resource/AdminDashboardResourceTest`, `admin/application/*Test` |
| Telegram auth | Feature gating, signed init-data authentication, scoped session creation, and rejected verification | `telegram/api/resource/TelegramMiniAppAuthResourceTest`, `telegram/application/auth/TelegramMiniAppAuthServiceTest`, `telegram/application/identity/TelegramInitDataVerifierTest` |
| Telegram webhook and callbacks | Webhook route/secret, duplicate update suppression, invalid or expired callback rejection, and callback acknowledgement before processing failures | `telegram/api/resource/TelegramWebhookResourceTest`, `telegram/infrastructure/persistence/TelegramWebhookUpdateRepositoryTest`, `telegram/application/bot/TelegramBotServiceImplTest`, `telegram/application/callback/TelegramCallbackServiceTest` |
| Cross-channel state | Web and Telegram share family ownership, request status, balances, history, and outbox state across fresh reads | `integration/TelegramCrossChannelIntegrationTest` |
| Platform errors | Stable expected error mapping and operational filter/readiness contracts | `platform/api/ClientErrorResourceTest`, `config/observability/*Test` |

These tests intentionally assert paths, statuses, payload fields, ownership,
and state transitions rather than current Java package names.

## 🧩 Layer Responsibilities

The runtime contract is resource → service → repository.

- Resources parse the request, verify role/scope, and map the result to HTTP.
- Services enforce family ownership, transactional rules, and business invariants.
- Repositories own entity persistence and query composition.

Practical rules:

- Do not trust child scope from frontend body or query values.
- Prefer DTO records for request and response payloads.
- Keep persistence mutations behind service methods rather than resource classes.
- Keep OpenAPI annotations updated when public endpoint behavior changes.

Current split notes:

- Dashboard query uses a thin facade plus `FamilyDashboardScopeLoader`, `FamilyDashboardCatalogLoader`, `FamilyDashboardHydrator`, `FamilyDashboardMapper`, and `FamilyDashboardResponseAssembler`.
- Auth uses a thin facade plus `AuthSupportService`, `AuthMembershipService`, `AuthAdminAuthService`, `AuthChildAuthService`, and `AuthLifecycleService`.
- Telegram Bot update dispatch, callbacks, identity, invitations, account
  connections, and notifications are separate application subdomains under
  `telegram/application/`.
- Admin analytics is split into metric-specific repositories under
  `admin/infrastructure/persistence/`, consumed by admin application services.
- Platform owns WebSocket delivery, push, database health, HTTP metrics, and
  bounded operational diagnostics under its semantic layers. Application logs
  are structured stdout collected by the deployment logging platform; the
  backend does not expose a local log-file feed through the family API.
- File size, method count, and cyclomatic complexity guardrails now live in Checkstyle, while class-level design debt checks are enforced through PMD for the refactored facade classes.

[↩ Back to toc](#table-of-contents)

## 🗄️ Database Overview

Core tables:

- `families`: family identity, admin credentials, verification flags, last selected child, family rules
- `children`: per-child profile, token, balance, limits, theme
- `tasks`: child-scoped task catalog
- `shop_items`: child-scoped reward catalog
- `history`: earned and spent balance events
- `requests`: approval queue for child actions
- `friends`: child-to-child friend relations
- `device_push_tokens`: web or mobile push registrations

Migration model:

- PostgreSQL migrations live in `src/main/resources/db/migration/`.
- Test-specific H2 migrations live in `src/test/resources/db/migration/`.
- Add new sequential migrations instead of modifying merged migration history.

Composite indexes are added only for measured repository predicates. The current
set covers child/family paging, analytics windows, and pending-request limit
checks; when adding a new index, keep the predicate shape and the expected
query plan documented together.

[↩ Back to toc](#table-of-contents)

## 🔐 Authentication and Authorization

Auth is cookie-based and handled centrally.

- `AuthResource` manages login, logout, registration, reset, and verification flows.
- `CookieBuilder` and `JwtService` mint and validate auth cookies.
- `AuthFilter` converts cookies into `AuthContext` for downstream resources.
- Roles are `admin`, `child`, and `super_admin`.

### Parent Access Model

Parent identity is account-based, not family-row-based. A parent email can belong to multiple families through membership rows.

- `parent_accounts` table stores unique email, password hash, verification state, and reset token state.
- `family_parent_memberships` table links parent accounts to families with permission levels.
- Permission levels: `viewer` (read-only), `editor` (read/write family data), `family_admin` (full access including membership management).
- At least one `family_admin` must remain in every family.
- Login with multiple memberships triggers a family chooser before entering the app.
- Session state carries both parent account identity and active family membership permission.

Authorization rules:

- Family data must always remain isolated by family ownership.
- Child sessions are restricted to the authenticated child server-side.
- Super-admin endpoints live under `/api/super/*` and must not bleed into family endpoints.
- `viewer` can read family data but cannot mutate it.
- `editor` can read and write family data but cannot manage parent memberships.
- `family_admin` can read/write family data and manage parent memberships.

### Bulk actions and import contract

The current dashboard save path remains snapshot-based for everyday edits, but
bulk actions and CSV import should be modeled as explicit commands instead of
overloading `POST /api/data`.

- Bulk task/shop actions should accept an explicit action plus a list of entity IDs.
- Bulk mutations must validate `family_id` and `child_id` before applying any write.
- Delete flows should continue to use soft-delete semantics where the entity model already supports them.
- CSV import should validate required columns and return line-level validation errors instead of silently skipping invalid rows.
- Command endpoints should return a refreshed family snapshot so the web client can rehydrate its store from server truth.

[↩ Back to toc](#table-of-contents)

## 🧾 API Versioning Strategy

The current API surface is intentionally unversioned under `/api/*`.

- Rationale: the app currently has one primary first-party client behind a same-origin web edge.
- Policy: non-breaking additions may extend the current surface in place.
- Future breaking changes should introduce `/api/v2/*` in parallel instead of rewriting existing routes in place.

[↩ Back to toc](#table-of-contents)

## 📘 Runtime API Docs

OpenAPI is generated at runtime from Quarkus annotations.

- OpenAPI document: `/api/openapi.yaml`
- Swagger UI: `/q/swagger-ui`

The service currently exposes runtime docs rather than committing a generated OpenAPI artifact into the repository.

[↩ Back to toc](#table-of-contents)

## 🧪 Verification

Use these commands from `apps/backend/`:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw verify
```

Failure modes to watch:

- `test` passing without `verify` is not sufficient because static analysis runs later.
- `verify` now includes Checkstyle, PMD, JaCoCo, and SpotBugs. PMD is scoped to the SRP facade classes, and SpotBugs runs in fail-fast mode.
- If a frontend payload changes, verify the backend DTO names before assuming data corruption.
- After migration work, validate both PostgreSQL migrations and the H2 test baseline.

[↑ Back to top](#top)
