# EarnIt Kids Architecture

<a name="top"></a>

## Table of Contents

- [🏗️ System Shape](#️-system-shape)
- [☕ Backend Layers](#-backend-layers)
- [🔐 Authentication Flow](#-authentication-flow)
- [📡 REST Surface](#-rest-surface)
- [⚙️ Configuration Model](#️-configuration-model)
- [🧪 Testing and Verification](#-testing-and-verification)
- [🚧 Extension Notes](#-extension-notes)

---

## 🏗️ System Shape

EarnIt Kids is split into a Quarkus backend and a Node.js web edge.

- `apps/backend/` owns authentication, family/child operations, analytics, persistence, and Flyway migrations.
- `apps/web/` serves the web experience, static assets, and UI-oriented tests.
- `mobile/` contains Capacitor packaging and mobile platform support assets.

The backend is the source of truth for session cookies, family state, child state, analytics, and approval workflows.

[↩ Back to toc](#table-of-contents)

---

## ☕ Backend Layers

The Quarkus backend is organized into a conventional service stack.

- `resource/`: JAX-RS endpoints for auth, family dashboard, session bootstrap, push, child magic links, super-admin, and WebSocket token
- `service/`: business rules and orchestration (`AuthServiceImpl`, `FamilyServiceImpl`, `BaseDataService`, `SuperAdminService`, `SystemDashboardService`, `DatabaseBackupService`)
- `repository/`: persistence and aggregation access over Panache/JPA-style repositories plus custom SQL/data fetches
- `domain/model/`: database entities and domain objects
- `dto/request/`: immutable request records validated at the REST boundary
- `dto/response/`: immutable response records, including problem-style errors and analytics payloads
- `config/`: auth filter, cookie builder, JWT compatibility helpers, and grouped config mappings
- `exception/`: REST exception mappers for validation and fallback error handling

Current backend conventions:

- constructor injection for application beans
- immutable request/response DTOs via Java records
- grouped configuration via `@ConfigMapping`
- RFC-7807-style error payloads via `ErrorResponse`
- OpenAPI annotations on REST endpoints for generated API docs

[↩ Back to toc](#table-of-contents)

---

## 🔐 Authentication Flow

Authentication is cookie-based and centered around a compatibility JWT.

1. `AuthResource` accepts parent/admin login, child token login, registration, password reset, and email verification requests.
2. `AuthServiceImpl` validates credentials and returns an `AuthPayload`.
3. `CookieBuilder` signs the `app_auth` token and companion cookies such as `csrf_token`, `family_id`, and `child_id`.
4. `AuthFilter` reads `app_auth`, verifies it through `JwtService`, and places an `AuthContext` on the request.
5. Downstream resources read `AuthContext` from `ContainerRequestContext` instead of reparsing cookies.
6. `JwtCompatVerifier` exposes a read-only session snapshot through `SessionPageDataResource` for page bootstrapping.

Child magic links use `ChildMagicLinkResource`, which authenticates a child token and redirects with the normal auth cookies already set.

[↩ Back to toc](#table-of-contents)

---

## 📡 REST Surface

The main backend entrypoints are:

### AuthResource (`/api`)
- `POST /api/login` — parent/admin login, sets auth cookies
- `POST /api/login-child` — child login by magic token
- `POST /api/logout` — clears session cookies
- `POST /api/register` — family registration
- `POST /api/forgot-password` — triggers password reset email
- `POST /api/reset-password` — applies password reset token
- `POST /api/verify-email` — confirms email verification token
- `POST /api/change-password` — changes password for authenticated admin
- `GET /api/auth-config` — returns auth feature flags (password recovery, email verification enabled)

### FamilyResource (`/api`)
- `GET /api/data` — dashboard payload for family or child session
- `POST /api/data` — persist dashboard mutations, returns refreshed payload
- `GET /api/base-data` — static task and reward catalog
- `POST /api/children` — create a child profile (admin only)
- `DELETE /api/children/{childId}` — delete a child profile (admin only)
- `PUT /api/children/{childId}/nickname` — rename child (admin only)
- `PUT /api/children/{childId}/settings` — update child limits (admin only)
- `PUT /api/children/{childId}/theme` — update child theme
- `GET /api/children/{childId}/link` — get child login token (admin only)
- `POST /api/children/{childId}/regenerate-token` — rotate child login token (admin only)
- `POST /api/update-nickname` — rename the authenticated child
- `GET /api/search-user` — search child profiles by nickname
- `POST /api/add-friend` — add another child as a friend
- `GET /api/friends-list` — list friends for authenticated child
- `GET /api/analytics` — analytics snapshot for family or child
- `GET /api/history` — paginated history entries
- `GET /api/requests` — paginated approval requests
- `POST /api/preferences` — persist family UI preference

### SuperAdminResource (`/api/super`)
Protected by `super_admin` role.
- `GET /api/super/families` — list all families
- `GET /api/super/family/{familyId}/data` — full family detail
- `POST /api/super/family/{familyId}/block` — toggle family block status
- `POST /api/super/family/{familyId}/regenerate-token` — rotate all family tokens
- `POST /api/super/child/{childId}/regenerate-token` — rotate single child token
- `GET /api/super/base-data` — read task/reward catalog
- `POST /api/super/base-data` — overwrite task/reward catalog
- `GET /api/super/system/overview` — JVM/OS metrics snapshot
- `GET /api/super/system/db` — database ping health check
- `GET /api/super/system/http-metrics` — per-route HTTP request counters
- `GET /api/super/system/logs` — tail application log entries
- `GET /api/super/db-backup` — download a pg_dump archive
- `POST /api/super/db-restore` — restore from a pg_dump archive

### Other resources
- `SessionPageDataResource` (`GET /api/session`) — session snapshot for page bootstrap derived from cookies
- `PushResource` (`POST /api/push/register`, `POST /api/push/unregister`) — push notification subscription
- `ChildMagicLinkResource` — browser redirect entrypoint for child magic links
- `WsTokenResource` (`GET /api/ws-token`) — short-lived token for WebSocket authentication
- `ClientErrorResource` (`POST /api/client-error`) — frontend error reporting

📝 Errors returned by JSON API endpoints use `ErrorResponse` with fields: `type`, `title`, `status`, `detail`, `errorCode`.

[↩ Back to toc](#table-of-contents)

---

## ⚙️ Configuration Model

Runtime configuration is centered around `application.properties` and grouped interfaces.

- `AppConfig` maps the `app.*` namespace
- `JwtCompatibilityConfig` maps the `compat.jwt.*` namespace

Important config domains:

- `app.production`: secure-cookie behavior
- `app.super-admin.*`: optional privileged login
- `app.email-verification.enabled`: registration/email verification flow toggle
- `app.password-recovery.enabled`: forgot/reset password flow toggle
- `compat.jwt.secret`: compatibility JWT signing secret

[↩ Back to toc](#table-of-contents)

---

## 🧪 Testing and Verification

Backend verification is Maven-driven and validated against Java 25.

Primary command:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/backend
./mvnw test
```

Current backend test coverage includes:

- unit tests for auth, JWT handling, cookies, services, and exception mappers
- resource-layer tests for auth, family, push, magic-link, and session bootstrap behavior
- repository smoke coverage via Quarkus test bootstrapping

[↩ Back to toc](#table-of-contents)

---

## 🚧 Extension Notes

When extending the backend:

- add new REST payloads as request/response records instead of `Map<String, Object>` where practical
- prefer `OperationResult` to keep success/failure contracts explicit in services
- keep auth-sensitive resource methods behind `AuthContext`
- update OpenAPI annotations when endpoints or payloads change
- add unit or resource tests for any changed behavior before closing the task

[↑ Back to top](#top)
