# Environment Examples and Docker Cleanup - Implementation Backlog

## Goal

Make the checked-in environment examples and both Docker Compose modes describe only settings that are consumed by the current runtime, build, or supported maintenance scripts. Operators should have one clear Compose contract without dead settings, ineffective overrides, or documentation for removed push infrastructure.

## Architectural decisions

- The root `.env.example` is the canonical input contract for the root Docker Compose stacks and the backend maintenance scripts that deliberately load root `.env`; it must not claim to configure values that Compose always replaces.
- `docker-compose.yml` and `docker-compose.native.yml` own container wiring. They must pass only the runtime values required by the web edge, Quarkus, PostgreSQL, and build stages; application code retains its documented compatibility fallbacks for direct local runs.
- `apps/backend/.env.example` is a standalone backend-oriented reference, not an alternative Compose source. Keep only properties that Quarkus or backend maintenance scripts actually consume.
- Remove obsolete mobile push/FCM/VAPID and Clarity configuration references rather than retaining inert feature flags. Do not restore the removed push API, schema, or client functionality as part of this cleanup.
- Preserve supported operational settings such as Telegram, Google Identity, database, cache, New Relic, and native build configuration when a current consumer exists, even if the default value is normally sufficient.
- `APP_URL` remains the public origin, not a duplicate of a port setting. The Compose host port is configurable through `WEB_PORT`; the web process keeps its fixed container port (`3000`) in Compose and Dockerfile wiring, so `WEB_INTERNAL_PORT` is not an operator-facing setting.
- Browser New Relic settings use the `VITE_` prefix only because `apps/web/src/lib/observability/newrelic.ts` consumes them through `import.meta.env`. Do not rename them to non-`VITE_` values and silently disable client instrumentation; remove the browser integration and its build arguments as one bounded task if it is no longer a product requirement.
- The legacy super-admin authority, email verification/password recovery, and Google OAuth are separate authentication and authorization surfaces. Their removal must cover routes, UI, JWT/session contracts, configuration, tests, translations, and persisted columns where applicable, while retaining the Telegram dashboard and its Telegram-ID-based authorization.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-ENV-001 | P2 | - | Establish the single effective Compose and example-variable contract before editing explanatory documentation. |
| 2 | TASK-ENV-002 | P2 | TASK-ENV-001 | Remove stale operator guidance only after the surviving configuration names and responsibilities are final. |
| 3 | TASK-ENV-003 | P2 | TASK-ENV-001 | Remove the redundant configurable container web-port setting without conflating public origin, host port, and service port. |
| 4 | TASK-ENV-004 | P1 | TASK-ENV-001 | Remove the legacy super-admin authorization surface before deleting its configuration. |
| 5 | TASK-ENV-005 | P1 | TASK-ENV-004 | Remove email verification and password-recovery persistence before removing their API and UI flows. |
| 6 | TASK-ENV-006 | P1 | TASK-ENV-005 | Remove the remaining legacy email-auth auxiliary endpoints, UI, and feature flags. |
| 7 | TASK-ENV-007 | P1 | TASK-ENV-006 | Remove Google OAuth as a separate external authentication integration. |
| 8 | TASK-ENV-008 | P2 | TASK-ENV-007 | Retire browser New Relic instrumentation so no Vite-prefixed public build configuration remains. |
| 9 | TASK-ENV-009 | P2 | TASK-ENV-003, TASK-ENV-004, TASK-ENV-006, TASK-ENV-007, TASK-ENV-008 | Publish the final example in priority-ordered, clearly optional groups. |

## TASK-ENV-001: Reduce environment examples and Compose wiring to live contracts

**Status:** DONE
**Priority:** P2
**Depends on:** -

**Exact scope:**

Audit each entry in the two environment examples and the `web`/`backend` Compose environment blocks against a current runtime, build-time, or supported maintenance-script consumer. Remove entries that have no such consumer or are unconditionally overridden by Compose, then make the JVM and native Compose files expose the same intended configuration contract where their backend mode does not require a difference.

**Files:**

- Modify `.env.example`.
- Modify `apps/backend/.env.example`.
- Modify `docker-compose.yml`.
- Modify `docker-compose.native.yml`.
- Search anchors: `loadAppConfig` in `apps/web/src/lib/server/config.ts`, `resolveProxyContext` in `apps/web/scripts/proxy-context.mjs`, `app.telegram` in `apps/backend/src/main/resources/application.properties`, and `loadEnvFile` in `apps/backend/scripts/lib/db.js`.

**Goal:**

A developer copying the root example can render either Compose stack without supplying inert settings, and every setting retained in either example has an identified active consumer.

### Outcome

The root example no longer contains root-only web values that Compose replaces (`PORT`, `BACKEND_URL`, `LOG_LEVEL`, and the duplicate root `QUARKUS_HTTP_PORT`) or removed push/FCM/VAPID and Clarity settings. Compose no longer injects redundant aliases when the selected runtime path already has a single canonical value.

### Architectural decision

Keep the current application compatibility aliases in code for direct and existing deployments, but do not inject several aliases with identical values from Compose. Retain `DB_PORT` where backend maintenance scripts consume it; do not mistake it for a dead Compose-only setting.

### Required changes

1. Build a variable-to-consumer inventory from Compose interpolation, Docker build arguments, Quarkus property expansion/config mappings, web server configuration, and `apps/backend/scripts/lib/db.js`; classify each example entry as required, optional-but-live, defaulted-and-omittable, overridden, or obsolete.
2. Remove the confirmed obsolete root-example entries: `ENABLE_PUSH_NOTIFICATIONS`, `FCM_PROJECT_ID`, `FCM_SERVICE_ACCOUNT_PATH`, `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_CONTACT`, and `CLARITY_PROJECT_ID`; remove `PORT`, `BACKEND_URL`, `LOG_LEVEL`, and root `QUARKUS_HTTP_PORT` only after preserving the effective Compose values in their owning service blocks.
3. Reconcile `apps/backend/.env.example` with the same inventory. Keep direct-backend and maintenance-script inputs that are live; remove only entries that no current backend consumer reads.
4. Simplify each Compose service environment block so its explicitly injected values are necessary for that service and do not duplicate a lower-priority alias with the same value. Preserve required service-to-service DNS URLs, health checks, `DATA_DIR`, read-only/tmpfs behavior, profiles, volumes, and the JVM/native image distinction.
5. Update comments next to surviving variables and port mappings so they describe the effective owner and do not imply host-loopback database access from a container.

### Out of scope

- Reintroducing push notifications, FCM, VAPID, Clarity, or their database/API/mobile implementations.
- Renaming supported environment variables or removing compatibility fallback handling from application code.
- Changing credentials in local `.env`, production secrets, Dokploy configuration, image versions, network topology, health-check semantics, or database schema.

### Acceptance criteria

- Every variable retained in `.env.example` and `apps/backend/.env.example` is read by a current Compose interpolation, Docker build/runtime command, application configuration path, or supported backend maintenance script.
- The obsolete push/FCM/VAPID/Clarity entries and the four ineffective root Compose overrides named above are absent from `.env.example`.
- Both Compose files preserve a reachable web-to-backend URL using service DNS, retain `DB_HOST`/`DB_INTERNAL_PORT` for container database access, and do not introduce `localhost` or `127.0.0.1` as a backend-to-database host.
- JVM and native Compose files differ only where their backend image/runtime requirements differ; equivalent web, database, and shared runtime settings have consistent names and values.
- Rendering each stack with the sanitized root example succeeds without unresolved-variable warnings or Compose schema errors.

### Targeted validation

```bash
docker compose --env-file .env.example --profile db config --quiet
docker compose -f docker-compose.native.yml --env-file .env.example --profile db config --quiet
! rg -n "ENABLE_PUSH_NOTIFICATIONS|FCM_PROJECT_ID|FCM_SERVICE_ACCOUNT_PATH|VAPID_PUBLIC_KEY|VAPID_PRIVATE_KEY|VAPID_CONTACT|CLARITY_PROJECT_ID|^PORT=|^BACKEND_URL=|^LOG_LEVEL=|^QUARKUS_HTTP_PORT=" .env.example
git diff --check
```

### Commit

```bash
git add .env.example apps/backend/.env.example docker-compose.yml docker-compose.native.yml
git commit -m "refactor(docker): remove stale environment wiring"
```

## TASK-ENV-002: Remove stale environment documentation and align operator references

**Status:** DONE
**Priority:** P2  
**Depends on:** TASK-ENV-001

**Exact scope:**

Align environment documentation with the completed variable contract, including removal of mobile FCM guidance that references backend endpoints and flags no longer present in the application.

**Files:**

- Modify `README.md`.
- Modify `apps/web/README.md`.
- Modify `apps/web/docs/ARCHITECTURE.md`.
- Modify `apps/mobile/README.md`.
- Delete `apps/mobile/README-firebase-fcm.md` if no remaining current mobile/backend feature links to it.
- Search anchors: `Environment Variables` in `README.md`, `Environment Variables` in `apps/web/README.md`, and `Background push notifications` in `apps/mobile/README.md`.

**Goal:**

Documentation tells operators which live variable owns each behavior and no longer instructs them to enable infrastructure that the repository cannot use.

### Outcome

The root and web environment references match the final samples and Compose contract. Mobile documentation no longer claims that `/api/push/register`, FCM configuration, or `ENABLE_PUSH_NOTIFICATIONS` are supported.

### Architectural decision

Documentation mirrors the canonical example and configuration consumers from TASK-ENV-001; it does not become a second variable registry. The web documentation may still describe compatibility aliases that application code supports, but must label them as such and not require Compose to inject all aliases.

### Required changes

1. Update the root README environment table and Compose notes to describe only live, effective root-example variables and the container-network database contract.
2. Reconcile web README and architecture tables with `loadAppConfig` and `resolveProxyContext`, distinguishing preferred names, compatibility aliases, and values Compose owns.
3. Remove obsolete push configuration snippets, endpoint claims, troubleshooting steps, and references from mobile documentation; delete the standalone FCM guide when it has no supported consumer or inbound link.
4. Search the maintained documentation for the removed variable names and correct or remove every active-reference result without changing historical backlog records.

### Out of scope

- New mobile notification design or provider integration.
- Editing completed backlog files solely to erase historical context.
- Changes to application behavior, test fixtures, deployment secrets, or Docker images beyond TASK-ENV-001.

### Acceptance criteria

- A new operator can follow the README Compose quick start using only the sanitized root example and understands that application containers reach PostgreSQL at the Compose service host, normally `db`.
- No maintained documentation instructs users to configure `ENABLE_PUSH_NOTIFICATIONS`, FCM, VAPID, Clarity, or a removed push-registration endpoint.
- Web documentation states the live preferred configuration names and describes aliases only where they remain supported by current code.
- All documentation paths and links changed by the task resolve to existing files; a removed FCM guide has no remaining active repository link.

### Targeted validation

```bash
! rg -n --glob '*.md' --glob '!docs/*backlog*.md' "ENABLE_PUSH_NOTIFICATIONS|FCM_|VAPID_|CLARITY_PROJECT_ID|/api/push/register" README.md apps docs
! rg -n "apps/mobile/README-firebase-fcm.md|README-firebase-fcm" README.md apps docs .github
git diff --check
```

### Commit

```bash
git add README.md apps/web/README.md apps/web/docs/ARCHITECTURE.md apps/mobile/README.md apps/mobile/README-firebase-fcm.md
git commit -m "docs: remove obsolete environment guidance"
```

## TASK-ENV-003: Make only the web host port configurable

**Status:** DONE
**Priority:** P2
**Depends on:** TASK-ENV-001

**Exact scope:**

Remove `WEB_INTERNAL_PORT` from the root example and use the fixed web-container port already owned by `apps/web/Dockerfile`; keep `WEB_PORT` solely as the host-publishing override and `APP_URL` solely as the public origin.

**Files:**

- Modify `.env.example`.
- Modify `docker-compose.yml`.
- Modify `docker-compose.native.yml`.
- Modify `README.md`.
- Search anchors: `WEB_INTERNAL_PORT` in both Compose files and `ENV PORT=3000` in `apps/web/Dockerfile`.

**Goal:**

Changing `WEB_PORT` changes the published host port without presenting a second, normally identical web-port setting to operators.

### Outcome

The example exposes `APP_URL` and `WEB_PORT` with distinct documented meanings; Compose uses the fixed port `3000` for the web process, ports, expose, and health check.

### Architectural decision

Do not derive `APP_URL` from `WEB_PORT`: production origin can be HTTPS or behind a reverse proxy. Do not change backend port configurability in this task.

### Required changes

1. Replace web internal-port interpolation with the fixed process port consistently in both Compose modes.
2. Remove `WEB_INTERNAL_PORT` and revise the root README table/comments so host port, container port, and public URL cannot be confused.
3. Preserve the existing web Dockerfile port, service-DNS backend URL, and health-check behavior.

### Out of scope

- Changing `BACKEND_INTERNAL_PORT`, reverse-proxy routing, or public domains.
- Renaming `APP_URL` or modifying web URL-normalization code.

### Acceptance criteria

- `.env.example` has no `WEB_INTERNAL_PORT` entry.
- Both rendered Compose models publish `${WEB_PORT}:3000`, make the web process listen on `3000`, and keep the `/healthz` check on that port.
- `APP_URL` remains independently configurable and is not treated as a port alias.

### Targeted validation

```bash
docker compose --env-file .env.example --profile db config --quiet
docker compose -f docker-compose.native.yml --env-file .env.example --profile db config --quiet
! rg -n "WEB_INTERNAL_PORT" .env.example docker-compose.yml docker-compose.native.yml README.md
git diff --check
```

### Commit

```bash
git add .env.example docker-compose.yml docker-compose.native.yml README.md
git commit -m "refactor(docker): remove configurable web container port"
```

## TASK-ENV-004: Remove the legacy super-admin authority surface

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-ENV-001

**Exact scope:**

Remove configuration-based super-admin elevation and its system-management API. Preserve family-admin permissions and the Telegram dashboard's independent Telegram-ID authorization.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/AppConfig.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/auth/AuthContext.java` and `JwtCompatVerifier.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/common/ResourceAuthSupport.java` and authentication services using `isSuperAdmin`.
- Delete `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/system/SuperAdminResource.java`, `SystemDashboardResource.java`, `service/system/SuperAdminService.java`, and their super-admin-only DTOs when no non-legacy consumer remains.
- Modify or delete matching backend tests, web super-admin labels, and `.env.example`/Compose `SUPER_ADMIN_EMAIL` wiring.
- Search anchors: `isSuperAdmin`, `requireSuperAdmin`, `/api/system`, and `SUPER_ADMIN_EMAIL`.

**Goal:**

No user can obtain a hidden privileged mode by matching an email address from environment configuration, and no super-admin-only system endpoint is exposed.

### Outcome

The JWT/session payload contains no super-admin flag; the application retains ordinary parent/family permissions and Telegram-admin dashboard access only.

### Architectural decision

The source of truth for family management remains parent memberships and permissions. Do not replace the removed authority with another email allow-list, feature flag, or duplicate dashboard role.

### Required changes

1. Remove `superAdmin` configuration, email comparison, session/JWT claims, authorization helpers, resources, services, DTOs, OpenAPI entries, UI labels, and tests that exist only for the feature.
2. Remove `SUPER_ADMIN_EMAIL` from examples and Compose without changing normal parent login or Telegram `TELEGRAM_ADMIN_USER_IDS` authorization.
3. Update authentication payload construction and compatibility parsing so legacy `isSuperAdmin` claims are ignored/rejected according to the existing token-security policy and cannot grant access.
4. Add or adjust regression coverage proving family-admin operations still work and super-admin-only routes are absent or return the established not-found behavior.

### Out of scope

- Removing `/api/admin/*` Telegram dashboard analytics or Telegram ID allow-list checks.
- Changing family permission models, migrations already merged, or parent invitation behavior.

### Acceptance criteria

- No source, Compose, or environment example has a live `SUPER_ADMIN_EMAIL` consumer.
- Existing/new tokens cannot gain privileges through `isSuperAdmin`; ordinary family-admin authorization remains unchanged.
- Super-admin system endpoints and UI affordances are unavailable, while Telegram dashboard authorization still accepts configured Telegram administrator IDs.
- Backend quality verification passes with super-admin-only tests replaced by assertions for the remaining authorization model.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd ../.. && ! rg -n "SUPER_ADMIN_EMAIL|isSuperAdmin|requireSuperAdmin|SuperAdminResource|SystemDashboardResource" .env.example docker-compose.yml docker-compose.native.yml apps/backend/src/main apps/web/src
git diff --check
```

### Commit

```bash
git add .env.example docker-compose.yml docker-compose.native.yml apps/backend apps/web
git commit -m "refactor(backend): remove legacy super-admin access"
```

## TASK-ENV-005: Drop legacy verification and reset-token persistence

**Status:** TODO
**Priority:** P1
**Depends on:** TASK-ENV-004

**Exact scope:**

Remove database state used exclusively for email verification and password recovery from family and parent-account records, with a new forward-only Flyway migration and matching H2 test migration.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V41__drop_legacy_email_auth_columns.sql`.
- Create matching H2 migration under `apps/backend/src/test/resources/db/migration/` using the same version and behavior.
- Modify `domain/model/FamilyEntity.java`, `domain/model/ParentAccountEntity.java`, `repository/FamilyRepository.java`, and `repository/ParentAccountRepository.java`.
- Search anchors: `verificationToken`, `resetToken`, `isVerified`, and `V40__add_telegram_parent_profiles.sql`.

**Goal:**

The database and JPA model no longer retain verification or password-reset tokens after the legacy flows are retired.

### Architectural decision

Use immutable, sequential Flyway migrations to remove only columns exclusive to these flows. Retain email and password hashes still needed for parent credentials and membership identity.

### Required changes

1. Identify every family and parent-account column/index used only for verification or reset flows, then remove it in PostgreSQL and H2 migrations with compatible SQL.
2. Remove corresponding entity fields, repository queries/updates, DTO fields, and fixtures; do not edit historical migrations.
3. Ensure existing parent registration and parent-account linking create valid rows without the removed state.

### Out of scope

- Deleting parent emails, password hashes, family records, memberships, or normal change-password behavior.
- Removing API/UI routes; that is TASK-ENV-006.

### Acceptance criteria

- A fresh PostgreSQL and H2 test schema reaches the latest migration version without verification/reset columns or indexes.
- No runtime entity or repository references removed columns.
- Parent registration, membership linking, and standard authenticated password changes remain persistently valid.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java apps/backend/src/main/resources/db/migration apps/backend/src/test
git commit -m "refactor(backend): drop legacy email auth state"
```

## TASK-ENV-006: Remove email verification and password recovery flows

**Status:** TODO
**Priority:** P1
**Depends on:** TASK-ENV-005

**Exact scope:**

Remove the verification and reset-password public API, feature flags, login panels, standalone pages, translations, and tests after their persistence has been removed.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/auth/AuthResource.java`, `AuthLifecycleService.java`, `AuthSupportService.java`, and `dto/response/AuthConfigResponse.java`.
- Delete `resource/auth/AuthRecoveryResource.java`, `dto/request/ForgotPasswordRequest.java`, and `dto/request/ResetPasswordRequest.java` when unused.
- Modify `apps/backend/src/main/resources/application.properties` and `config/AppConfig.java`.
- Delete `apps/web/src/routes/reset-password/+page.svelte` and `apps/web/src/routes/verify/+page.svelte`; modify `apps/web/src/routes/login/+page.svelte`, i18n route configuration/messages, and affected tests.
- Search anchors: `passwordRecoveryEnabled`, `emailVerificationEnabled`, `/api/forgot-password`, `/api/reset-password`, and `/api/verify`.

**Goal:**

The active login experience offers only supported parent authentication paths and no unreachable recovery/verification UI or endpoints.

### Architectural decision

Remove the flows end-to-end rather than leaving permanently false flags or UI branches. Keep normal registration, login, child login, and authenticated parent password change intact.

### Required changes

1. Remove the configuration mappings and environment entries for `ENABLE_EMAIL_VERIFICATION` and `ENABLE_PASSWORD_RECOVERY`.
2. Remove API routes, services, request/response contracts, OpenAPI annotations, pages, login states, translations, route aliases, and tests that exist exclusively for those flows.
3. Update login configuration loading so it has no stale booleans and remains robust when calling the remaining auth configuration endpoint.
4. Verify deleted routes and proxy paths do not leave client links, sitemaps, or server contracts behind.

### Out of scope

- Parent sign-in, registration, child magic links, password change for an authenticated parent, or Telegram authentication.
- Google OAuth removal, which is TASK-ENV-007.

### Acceptance criteria

- Root/backend examples, Compose, and application configuration contain no email-verification or password-recovery flag.
- `/api/forgot-password`, `/api/reset-password`, and `/api/verify` are not published; `/reset-password` and `/verify` have no active web route/link.
- Login, registration, normal parent credentials, and authenticated password change retain their current success and error behavior.
- Backend and web tests cover the remaining login contract without recovery/verification compatibility branches.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd ../web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add .env.example apps/backend/.env.example docker-compose.yml docker-compose.native.yml apps/backend apps/web
git commit -m "refactor(auth): remove legacy recovery flows"
```

## TASK-ENV-007: Remove legacy Google OAuth authentication

**Status:** TODO
**Priority:** P1
**Depends on:** TASK-ENV-006

**Exact scope:**

Remove Google OAuth configuration and the parent-login integration from backend and web. Parent email/password authentication remains the only browser login method.

**Files:**

- Delete `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/auth/AuthGoogleResource.java`, `service/google/GoogleOAuthService.java`, `GoogleIdentityVerifier.java`, `GoogleIdentity.java`, `GoogleTokenResponse.java`, and `dto/request/GoogleLoginRequest.java` when no other consumer remains.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/AppConfig.java`, `service/auth/AuthSupportService.java`, `service/auth/AuthService.java`, `AuthServiceImpl.java`, `AuthResource.java`, and `application.properties`.
- Delete `apps/web/src/lib/auth/googleOAuth.ts`; modify `apps/web/src/routes/login/+page.svelte`, auth messages, and `apps/web/tests/unit/googleOAuth.test.ts`.
- Modify `.env.example`, `apps/backend/.env.example`, `docker-compose.yml`, `docker-compose.native.yml`, and operator documentation.
- Search anchors: `GOOGLE_AUTH_`, `googleEnabled`, `/api/login-google`, and `GoogleOAuthService`.

**Goal:**

No Google client secret, redirect URI, OAuth callback, or Google sign-in control remains in the shipped application.

### Architectural decision

Google OAuth is removed as an external identity boundary, not merely disabled. Do not leave a dormant callback URL, feature flag, or public client identifier that could revive an incomplete flow.

### Required changes

1. Remove backend OAuth endpoints, state/callback handling, token exchange/identity verification, configuration mapping, login configuration fields, tests, and OpenAPI operations.
2. Remove web Google login requests, callback-state handling, controls, translations, tests, and any login-panel state used only by Google.
3. Delete all `GOOGLE_AUTH_*` example and Compose variables, including redirect URI interpolation, and remove active documentation references.
4. Confirm ordinary login/config responses remain deterministic without Google-specific optional fields.

### Out of scope

- Parent credential authentication, Telegram Mini App identity, or any future OAuth provider.
- Credential rotation in existing Google Cloud projects.

### Acceptance criteria

- No runtime route under `/api/login-google` or Google callback route is published.
- The login page has no Google control, pending state, error copy, or redirect handling.
- No active source, example, Compose file, or maintained document contains `GOOGLE_AUTH_` or Google OAuth client credentials.
- Existing parent login and registration tests pass without provider-specific branches.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd ../web && npm run lint && npm run test && npm run build
cd ../.. && ! rg -n "GOOGLE_AUTH_|login-google|GoogleOAuthService|googleEnabled" .env.example apps/backend/.env.example docker-compose.yml docker-compose.native.yml apps/backend/src/main apps/web/src
git diff --check
```

### Commit

```bash
git add .env.example apps/backend/.env.example docker-compose.yml docker-compose.native.yml apps/backend apps/web README.md
git commit -m "refactor(auth): remove legacy Google sign-in"
```

## TASK-ENV-008: Retire browser New Relic instrumentation

**Status:** TODO
**Priority:** P2
**Depends on:** TASK-ENV-007

**Exact scope:**

Remove client-side New Relic Browser initialization and its four Vite build arguments. Keep backend New Relic agent and metrics configuration unchanged.

**Files:**

- Delete `apps/web/src/lib/observability/newrelic.ts` when no other consumer remains.
- Modify web imports/call sites found through `initializeNewRelic` and the web test suite.
- Modify `apps/web/Dockerfile`, `docker-compose.yml`, `docker-compose.native.yml`, `.env.example`, and New Relic documentation.
- Search anchors: `VITE_NEW_RELIC_BROWSER_`, `newrelic.ts`, and `NEW_RELIC_AGENT_ENABLED`.

**Goal:**

The browser build no longer embeds New Relic settings, so operators do not need Vite-prefixed public environment variables.

### Architectural decision

Vite's `VITE_` convention is correct for browser-exposed values. The prefix disappears by removing the no-longer-required browser integration, while server-side New Relic remains a separate backend observability concern.

### Required changes

1. Remove browser instrumentation initialization, client event/error hooks, build-time Vite environment reads, and dedicated tests.
2. Remove all four browser build args and `.env.example` values: `VITE_NEW_RELIC_BROWSER_ENABLED`, `VITE_NEW_RELIC_BROWSER_INFO`, `VITE_NEW_RELIC_BROWSER_INIT`, and `VITE_NEW_RELIC_BROWSER_LOADER_CONFIG`.
3. Preserve backend agent/OTLP settings, image behavior, and their documentation; remove only browser-specific monitoring claims.

### Out of scope

- Removing backend New Relic Java agent, metrics export, logs, licensing, or health checks.
- Replacing New Relic Browser with another client telemetry vendor.

### Acceptance criteria

- Web source and Docker build have no `VITE_NEW_RELIC_BROWSER_` use or build arg.
- Web builds and page startup work without browser monitoring configuration.
- Backend New Relic runtime configuration is unchanged and documented separately from browser monitoring.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && npm run build
cd ../.. && docker compose --env-file .env.example --profile db config --quiet
! rg -n "VITE_NEW_RELIC_BROWSER_|import\.meta\.env.*NEW_RELIC" .env.example docker-compose.yml docker-compose.native.yml apps/web
git diff --check
```

### Commit

```bash
git add .env.example docker-compose.yml docker-compose.native.yml apps/web docs README.md
git commit -m "refactor(web): remove browser New Relic configuration"
```

## TASK-ENV-009: Publish a priority-ordered environment example

**Status:** TODO
**Priority:** P2
**Depends on:** TASK-ENV-003, TASK-ENV-004, TASK-ENV-006, TASK-ENV-007, TASK-ENV-008

**Exact scope:**

Reorder and annotate the final root `.env.example` after every removal. It must be scannable as an operator contract, with required local-stack inputs first and genuinely optional configuration clearly identified.

**Files:**

- Modify `.env.example`.
- Modify `apps/backend/.env.example` only where its grouping can match its direct-backend audience without duplicating the root Compose contract.
- Modify `README.md` only if its environment table no longer matches the final ordering/ownership.
- Search anchors: the section comments in `.env.example` and `Environment Variables` in `README.md`.

**Goal:**

An operator can identify the minimum local configuration at the top of the example and can safely skip optional integration, maintenance, and observability settings.

### Architectural decision

The root example is a curated Compose contract, not a dump of every property default. Use descriptive comment headings and `Optional:` labels; do not introduce a second source of defaults or copy values from application properties unnecessarily.

### Required changes

1. Put required/public stack inputs first: public origin, host port, JWT secret, and database connection/credentials required by the selected Compose profile.
2. Follow with clearly labelled optional groups in operational order: Telegram, monitoring, cache/performance tuning, and native build-only settings.
3. Add concise comments that state an option's owning runtime and when an operator should set it; mark empty optional values explicitly rather than presenting them as mandatory blanks.
4. Ensure all names reflect completed removal tasks and no duplicate/legacy section remains.

### Out of scope

- Adding new configuration capabilities or changing application defaults.
- Reordering local `.env` secrets, deployment secrets, or historical backlog files.

### Acceptance criteria

- The first section contains only the minimum required local Compose inputs and distinguishes `APP_URL` from `WEB_PORT`.
- Every nonessential group is headed and marked optional with a concise operational purpose.
- The final example has no removed super-admin, verification/recovery, Google OAuth, browser New Relic, push/FCM/VAPID, Clarity, or internal-web-port variables.
- Both Compose modes render successfully from the final example.

### Targeted validation

```bash
docker compose --env-file .env.example --profile db config --quiet
docker compose -f docker-compose.native.yml --env-file .env.example --profile db config --quiet
git diff --check
```

### Commit

```bash
git add .env.example apps/backend/.env.example README.md
git commit -m "docs: organize environment example by priority"
```
