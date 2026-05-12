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
- Catalog, history, requests, analytics, and backup operations
- Flyway migrations and database integration

[↩ Back to toc](#table-of-contents)

## 📦 Package Structure

Top-level packages under `src/main/java/com/sashplatonov/earnit/kids/`:

- `resource/`: JAX-RS HTTP resources
- `service/`: business orchestration and transactions
- `repository/`: persistence access and query helpers
- `domain/model/`: JPA entities
- `dto/request/`: validated inbound request records
- `dto/response/`: outbound response records
- `config/`: auth, headers, JWT, filters, config mappings
- `exception/`: exception mappers and API error translation
- `util/`: cross-cutting helper types such as `OperationResult`

[↩ Back to toc](#table-of-contents)

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
- If a frontend payload changes, verify the backend DTO names before assuming data corruption.
- After migration work, validate both PostgreSQL migrations and the H2 test baseline.

[↑ Back to top](#top)