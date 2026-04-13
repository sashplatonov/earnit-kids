# EarnIt Kids Architecture

## Table of Contents
- [🏗️ System Shape](#️-system-shape)
- [☕ Backend Layers](#-backend-layers)
- [🔐 Authentication Flow](#-authentication-flow)
- [📡 REST Surface](#-rest-surface)
- [⚙️ Configuration Model](#️-configuration-model)
- [🧪 Testing and Verification](#-testing-and-verification)
- [🚧 Extension Notes](#-extension-notes)

## 🏗️ System Shape

EarnIt Kids is split into a Quarkus backend and a Node.js web edge.

- `backend/` owns authentication, family/child operations, analytics, persistence, and Flyway migrations.
- `web/` serves the web experience, static assets, and UI-oriented tests.
- `mobile/` contains Capacitor packaging and mobile platform support assets.

The backend is the source of truth for session cookies, family state, child state, analytics, and approval workflows.

[↩ Back to toc](#table-of-contents)

## ☕ Backend Layers

The Quarkus backend is organized into a conventional service stack.

- `resource/`: JAX-RS endpoints for auth, family dashboard, session bootstrap, push placeholders, and child magic links
- `service/`: business rules and orchestration (`AuthServiceImpl`, `FamilyServiceImpl`, `BaseDataService`)
- `repository/`: persistence and aggregation access over Panache/JPA-style repositories plus custom SQL/data fetches
- `domain/model/`: database entities and domain objects
- `dto/request/`: immutable request records validated at the REST boundary
- `dto/response/`: immutable response records, including problem-style errors and analytics payloads
- `config/`: auth filter, cookie builder, JWT compatibility helpers, and grouped config mappings
- `exception/`: REST exception mappers for validation and fallback error handling

The current backend conventions are:

- constructor injection for application beans
- immutable request/response DTOs via Java records
- grouped configuration via `@ConfigMapping`
- RFC-7807-style error payloads via `ErrorResponse`
- OpenAPI annotations on REST endpoints for generated API docs

[↩ Back to toc](#table-of-contents)

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

## 📡 REST Surface

The main backend entrypoints are:

- `AuthResource`: login, child login, logout, registration, password recovery, email verification, auth feature flags
- `FamilyResource`: dashboard data, child CRUD-ish operations, nickname/theme/settings updates, friendships, analytics, history, requests, preferences, child token operations
- `SessionPageDataResource`: session snapshot derived from cookies
- `PushResource`: placeholder register/unregister endpoints guarded by auth
- `ChildMagicLinkResource`: browser redirect entrypoint for child magic links

Errors returned by JSON API endpoints should use `ErrorResponse` so the frontend sees a stable shape:

- `type`
- `title`
- `status`
- `detail`
- `errorCode`

[↩ Back to toc](#table-of-contents)

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

## 🧪 Testing and Verification

Backend verification is Maven-driven and currently validated against Java 25.

Primary command:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw test
```

Current backend test coverage includes:

- unit tests for auth, JWT handling, cookies, services, and exception mappers
- resource-layer tests for auth, family, push, magic-link, and session bootstrap behavior
- repository smoke coverage via Quarkus test bootstrapping

[↩ Back to toc](#table-of-contents)

## 🚧 Extension Notes

When extending the backend:

- add new REST payloads as request/response records instead of `Map<String, Object>` where practical
- prefer `OperationResult` to keep success/failure contracts explicit in services
- keep auth-sensitive resource methods behind `AuthContext`
- update OpenAPI annotations when endpoints or payloads change
- add unit or resource tests for any changed behavior before closing the task

[↑ Back to top](#earnit-kids-architecture)