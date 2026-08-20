# Backend Code Refactor & Cleanup — Implementation Backlog

## Goal

Refactor the `apps/backend` Java Quarkus service to remove code duplication across
layers and strip inline comments that do not explain a genuinely complex flow or
calculation algorithm. The outcome is a smaller, more maintainable backend where
shared behavior lives in one place, `OperationResult`→`Response` mapping is
consistent (no latent null-error-code bug), and only non-obvious logic carries an
`EXPLAIN:` comment.

No product behavior, API contract, database schema, or response shape changes.
All changes must be behavior-preserving refactors verified by the existing test
suite and quality gates.

## Architectural decisions

- **Source of truth for comments**: `config/checkstyle.xml` already enforces that
  inline comments must start with `EXPLAIN:` or `FIXME:`. The project policy
  (AGENTS.md + java-service-standards skill) allows comments only when the
  algorithm is genuinely complex, the flow is non-obvious, or a bug fix addresses
  a subtle issue. The 345 existing `EXPLAIN:` comments are currently the only
  comment style present, but many restate obvious behavior (e.g. `// EXPLAIN:
  Convenience constructor preserving the pre-status call signature.`) or document
  DTO field semantics that are clear from the record name. These must be removed;
  only comments explaining non-obvious flow or calculation survive.
- **No new comments**: Removed comments are not replaced. If a flow is genuinely
  non-obvious, the existing `EXPLAIN:` comment is kept as-is (it already conforms
  to policy).
- **Duplication extraction ownership**: Resource-layer helpers
  (`OperationResult`→`Response`, auth extraction) move to `resource/common/` or
  `util/`. Service-layer helpers (family/child ownership guards, `failure` factory,
  history hydration) move to `service/common/` or `service/family/` shared beans.
  Repository pagination moves to `repository/`. DTOs stay as records.
- **Layer boundaries preserved**: `resource → service → repository` is unchanged.
  Extracted helpers do not cross layers. `OperationResult` stays in `util/` and
  does not import `i18n/` (keeps it pure); the `failure(messageKey)` factory lives
  in a new `service/common/ServiceResults` to keep the i18n dependency in the
  service layer.
- **Backward compatibility**: All HTTP response shapes, status codes, error codes,
  and error messages remain byte-identical. The `FamilyActionBalanceService`
  inconsistency (null error code where other services emit `FAMILY_NOT_FOUND`) is
  fixed to match the majority — this is a bug fix, not a contract change, because
  the `errorCode` field is informational and clients do not branch on it.
- **No suppressions**: No `@SuppressWarnings`, Checkstyle/SpotBugs/PMD exclusions,
  or `@ts-ignore`-style escapes are introduced. Checkstyle's `MethodCount(max=20)`,
  `MethodLength(max=60)`, `CyclomaticComplexity(max=12)`, and `FileLength(max=450)`
  limits constrain extraction design — extracted utilities are split across files
  if needed rather than suppressed.
- **Rejected duplicate approaches**: Do not add a MapStruct mapper for the 2-3 site
  history-DTO mapping (skill standard: MapStruct only when repeated across 3+ service
  classes — `toHistoryDto` is at 3 sites, so a hand-written `HistoryDtoMapper` is
  used instead, consistent with "keep mapping explicit and local when short").
  Do not introduce Javadoc (forbidden by Checkstyle). Do not move `OperationResult`
  failure factory into `OperationResult` itself (would couple `util/` to `i18n/`).
- **Review follow-ups (2026-08-20)**: `V39__randomize_family_ids.sql` has already
  been applied in production and is immutable. Its first PostgreSQL-only statement
  prevents the H2 test application from booting, while its second update would
  hash the newly generated identifier a second time. A new migration/test-baseline
  strategy must make both PostgreSQL deployment and H2 validation deterministic.
  Quality rules remain enforcement, not reporting: existing SpotBugs and JaCoCo
  exclusions cannot substitute for fixing ownership leaks or adding meaningful
  coverage. The configured JaCoCo threshold must match this backlog's 0.80 goal.
- **Remaining response mapping**: Auth responses own cookies, redirects, and
  response DTO selection; `OperationResultResponses` owns the generic
  success/failure branching. Extend the existing utility with callback/status
  hooks instead of creating a second auth-specific mapper, and preserve every
  response body, status, header, and redirect target.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P0-4: Restore H2-compatible Flyway startup after V39 | P0 | - | Broken migration blocks two Quarkus tests and OpenAPI runtime proof |
| 2 | P0-5: Remove SpotBugs ownership-leak suppressions | P0 | P0-4 | A clean analyzer result must reflect fixed code, not exclusions |
| 3 | P0-6: Restore and enforce 80% JaCoCo line coverage | P0 | P0-4 | The current 62.8% result and 0.65 configured threshold violate P3-1 |
| 4 | P0-1: Extract `OperationResultResponses` utility | P0 | - | Fixes latent null-error-code bug; unblocks all resource-layer cleanup |
| 5 | P0-2: Extract `ResourceAuthSupport` base + role guards | P0 | P0-1 | Removes ~35 duplicated auth guards; standardizes unauthorized message |
| 6 | P0-3: Extract `ServiceResults` failure factory | P0 | - | Removes 11 duplicated `failure(errorCode, messageKey)` helpers |
| 7 | P1-1: Extract `FamilyOperationGuard` for family ownership | P1 | P0-3 | Removes ~15 duplicated family-db-id guards; fixes error-code inconsistency |
| 8 | P1-2: Extract `ChildOwnershipService` | P1 | P1-1 | Removes 6 duplicated `findFamilyChild` helpers |
| 9 | P1-3: Extract `HistoryDtoMapper` + `RelatedEntityHydrator` | P1 | - | Removes ~100 verbatim-duplicated lines across 2 service classes |
| 10 | P1-4: Extract `PageRequest` pagination helper | P1 | - | Removes 4 duplicated clamping blocks; fixes inconsistent max-page-size (100 vs 500) |
| 11 | P1-5: Extract `PanachePagination` repository helper | P1 | P1-4 | Removes 3 duplicated `.range().list()` blocks |
| 12 | P2-1: Remove noise `EXPLAIN:` comments — DTO layer | P2 | - | Low-risk; DTOs have no behavior to break |
| 13 | P2-2: Remove noise `EXPLAIN:` comments — service/telegram | P2 | P2-1 | Largest comment concentration (30 files); review per-file |
| 14 | P2-3: Remove noise `EXPLAIN:` comments — repository/remaining | P2 | P2-2 | AdminAnalyticsRepository (25 comments) + remaining packages |
| 15 | P2-4: Adopt `ResourceAuthSupport` across all resources | P2 | P0-2 | Migrate `FamilyReadResource`, `AccountResource`, telegram resources, admin resources |
| 16 | P2-5: Complete `OperationResult` response-mapping migration | P2 | P0-1 | Five resource-level switches still violate P0-1 acceptance criteria |
| 17 | P2-6: Finish removal of noise `EXPLAIN:` comments | P2 | P2-1, P2-2, P2-3 | 269 comments remain; P3-1 requires fewer than 20 |
| 18 | P3-1: Final quality gate + coverage check | P3 | All preceding tasks | Confirm `verify` passes, JaCoCo ≥ 0.80, no regressions |

## P0-1: Extract `OperationResultResponses` utility

**Status:** ✅ Completed
**Priority:** P0  
**Depends on:** -

### Outcome

A single utility maps `OperationResult<T>` to JAX-RS `Response` with null-safe error
codes, replacing 13 duplicated switch expressions and eliminating the latent
null-`errorCode` bug present in 8 call sites that pass `failure.errorCode()` raw.

### Architectural decision

The `util/` layer owns this pure mapping helper because it depends only on
`OperationResult` (already in `util/`), `ErrorResponse`, `SimpleResponse`, and
JAX-RS `Response` — no service or repository coupling. It is a static utility class,
not a CDI bean, because it carries no state. The canonical null-safe
`errorCodeOrBadRequest` logic from `FamilyResourceSupport` becomes the single
source of truth.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/util/OperationResultResponses.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResourceSupport.java` (delegate to utility, keep protected methods as thin wrappers for backward compat with subclasses).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyReadResource.java` (remove duplicated `toResponse`/`toVoidResponse`/`errorCodeOrBadRequest`, delegate to utility).
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/util/OperationResultResponsesTest.java`.

### Work

1. Create `OperationResultResponses` with static methods:
   - `Response toOk(OperationResult<T> result)` — success→`ok(value)`, failure→400 with `ErrorResponse.of(message, errorCodeOrBadRequest(code), 400)`.
   - `Response toVoidOk(OperationResult<Void> result)` — success→`ok(SimpleResponse.ok())`, failure→400 as above.
   - `Response toCreated(OperationResult<T> result, URI location)` — success→`201 Created` with `Location` header, failure→400.
   - `String errorCodeOrBadRequest(String errorCode)` — `errorCode != null ? errorCode : "BAD_REQUEST"`.
2. Add a `FailureStatusResolver` functional parameter overload `toOk(result, failureStatusResolver)` for call sites that need 404/500/conflict instead of 400 (currently hardcoded in `FamilyParentAccessResource` and `FamilyChildSettingsResource`).
3. Update `FamilyResourceSupport` protected methods to delegate to the utility (preserves subclass API; no call-site changes in subclasses yet).
4. Update `FamilyReadResource` to call the utility directly and delete its private `errorCodeOrBadRequest` and duplicated switch methods.
5. Write unit tests covering: success with value, success with void, failure with error code, failure with null error code (must default to `BAD_REQUEST`), created response with location, custom failure status (404).

### Acceptance criteria

- `OperationResultResponses.toOk` of a `Failure` with `errorCode == null` produces an `ErrorResponse` with `code = "BAD_REQUEST"` (not `null`).
- `OperationResultResponses.toOk` of a `Success(value)` produces `200 OK` with the value as entity.
- `OperationResultResponses.toVoidOk` of `Success(null)` produces `200 OK` with `SimpleResponse.ok()` entity.
- `OperationResultResponses.toCreated` of `Success(value)` produces `201 Created` with the supplied `Location` header.
- A `FailureStatusResolver` returning `404` produces a `404` response with the failure message and error code.
- `FamilyResourceSupport` subclasses compile unchanged (protected method signatures preserved).
- `FamilyReadResource` no longer declares `errorCodeOrBadRequest` or its own `toResponse`/`toVoidResponse`.
- All existing resource-layer tests pass without modification.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='OperationResultResponsesTest,*ResourceTest,*Resource*Test'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/util/OperationResultResponses.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResourceSupport.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyReadResource.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/util/OperationResultResponsesTest.java
git commit -m "refactor(backend): extract OperationResultResponses utility with null-safe error codes"
```

---

## P0-2: Extract `ResourceAuthSupport` base + role guards

**Status:** ✅ Completed
**Priority:** P0  
**Depends on:** P0-1

### Outcome

A single `ResourceAuthSupport` base class (or CDI `@ApplicationScoped` helper)
provides `authContext(ctx)`, `requireAuth(ctx)`, `requireAdmin(ctx)`,
`requireChild(ctx)`, `requireSuperAdmin(ctx)`, `unauthorized()`, `forbidden()`,
`badRequest(msg)`, and `resolveEffectiveChildId(auth, childId)`, replacing ~13
duplicated copies of the auth-extraction trio and ~35 duplicated per-endpoint
role-guard two-liners.

### Architectural decision

`resource/common/` owns the base class because the helpers read from
`ContainerRequestContext` (JAX-RS) and produce `Response` — they are resource-layer
concerns. `FamilyResourceSupport` already exists as a base for family resources;
the new `ResourceAuthSupport` is the generalized version that non-family resources
(`AccountResource`, `SuperAdminResource`, `SystemDashboardResource`, telegram
resources) extend or inject. `FamilyResourceSupport` extends
`ResourceAuthSupport` to inherit the auth helpers and keep its family-specific
`notifyDataUpdated`/`notifyChildDeleted` methods.

The `requireX` methods return `AuthContext` directly and throw a
`WebApplicationException` carrying the pre-built `unauthorized()`/`forbidden()`
response — JAX-RS maps this automatically, collapsing the 2-line guard to 1 line.
The unauthorized message is standardized to
`BackendMessages.message("errors.unauthorized")` everywhere (fixes
`AccountResource`/`FamilyNotificationResource`/telegram resources that hardcode
`"Authentication is required."`).

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/common/ResourceAuthSupport.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResourceSupport.java` (extend `ResourceAuthSupport`, remove duplicated `getAuthOrFail`/`unauthorized`/`badRequest`).
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/common/ResourceAuthSupportTest.java`.

### Work

1. Create `ResourceAuthSupport` with:
   - `AuthContext authContext(ContainerRequestContext ctx)` — extracts `AuthFilter.AUTH_CONTEXT_PROPERTY`, returns `null` if absent.
   - `AuthContext requireAuth(ContainerRequestContext ctx)` — throws `WebApplicationException(401)` if null.
   - `AuthContext requireAdmin(ContainerRequestContext ctx)` — `requireAuth` then `isAdmin()` check, else throw 403.
   - `AuthContext requireChild(ContainerRequestContext ctx)` — `requireAuth` then `isChild()` check, else throw 403.
   - `AuthContext requireSuperAdmin(ContainerRequestContext ctx)` — `requireAuth` then `isSuperAdmin()` check, else throw 403.
   - `Response unauthorized()`, `Response forbidden()`, `Response badRequest(String message)` — using `BackendMessages.message("errors.unauthorized")` for 401 and `errors.forbidden` for 403.
   - `OperationResult<Integer> resolveEffectiveChildId(AuthContext auth, Integer childId)` — admin session uses `childId` param, child session uses `auth.childId()`; returns failure if the resolved id is null.
2. Make `FamilyResourceSupport extends ResourceAuthSupport`; delete its `getAuthOrFail`, `unauthorized`, `badRequest` (now inherited).
3. Write unit tests for each `requireX` method covering: null auth (401), wrong role (403), correct role (returns auth), and `resolveEffectiveChildId` for admin-with-childId, admin-without-childId, child-session.

### Acceptance criteria

- `requireAuth(ctx)` with no `AuthContext` property throws `WebApplicationException` with status 401 and `ErrorResponse` entity using `errors.unauthorized` message.
- `requireAdmin(ctx)` with a child-role auth throws 403.
- `requireSuperAdmin(ctx)` with an admin-role auth throws 403.
- `resolveEffectiveChildId` with `auth.isChild()` returns `auth.childId()` as success.
- `resolveEffectiveChildId` with admin role and `childId == null` returns `Failure`.
- `FamilyResourceSupport` subclasses compile unchanged (inherited methods have same signatures).
- All existing family resource tests pass.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='ResourceAuthSupportTest,*FamilyResource*Test'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/common/ResourceAuthSupport.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResourceSupport.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/common/ResourceAuthSupportTest.java
git commit -m "refactor(backend): extract ResourceAuthSupport with role guards and effective-child resolution"
```

---

## P0-3: Extract `ServiceResults` failure factory

**Status:** ✅ Completed
**Priority:** P0  
**Depends on:** -

### Outcome

A single `ServiceResults` helper in `service/common/` provides
`failure(errorCode, messageKey)` and `failure(errorCode, messageKey, variables)`
factory methods, replacing 11 identical private static helpers copy-pasted across
service classes.

### Architectural decision

`service/common/` owns this because it couples `OperationResult` (util) with
`BackendMessages` (i18n) — a service-layer concern. Keeping it out of `util/`
preserves `OperationResult` purity (no i18n import). It is a `final` utility class
with static methods (no state, no CDI). The `variables` overload supports
`BackendMessages.message(key, variables)` for parameterized messages already used
in `FamilyChildManagementService`.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/common/ServiceResults.java`.
- Modify the 11 service classes that declare a private `failure(errorCode, messageKey)` helper to call `ServiceResults.failure(...)` instead:
  - `service/account/AccountServiceImpl.java`
  - `service/analytics/AnalyticsServiceImpl.java`
  - `service/family/command/FamilyCommandServiceImpl.java`
  - `service/family/dashboard/FamilyDashboardQueryServiceImpl.java`
  - `service/family/FamilyChildManagementService.java`
  - `service/family/FamilyFriendService.java`
  - `service/family/FamilyHistoryQueryServiceImpl.java`
  - `service/family/FamilyNotificationServiceImpl.java`
  - `service/family/FamilyPreferenceService.java`
  - `service/telegram/TelegramChildConnectionServiceImpl.java`
  - `service/telegram/TelegramParentInvitationServiceImpl.java`
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/common/ServiceResultsTest.java`.

### Work

1. Create `ServiceResults` with:
   - `static <T> OperationResult<T> failure(String errorCode, String messageKey)` — `OperationResult.failure(errorCode, BackendMessages.message(messageKey))`.
   - `static <T> OperationResult<T> failure(String errorCode, String messageKey, String... variables)` — `OperationResult.failure(errorCode, BackendMessages.message(messageKey, variables))`.
   - `static <T> OperationResult<T> failure(String messageKey)` — `OperationResult.failure(BackendMessages.message(messageKey))` (null error code overload, used by `FamilyActionBalanceService` — kept for compatibility until P1-1 fixes it).
2. In each of the 11 services, delete the private `failure` helper and replace call sites with `ServiceResults.failure(...)`. Handle the `variables` overload in `FamilyChildManagementService`.
3. Write unit tests: `failure("CODE", "key")` produces `Failure` with `errorCode="CODE"` and `message=BackendMessages.message("key")`; `failure("key")` produces `Failure` with `errorCode=null`.

### Acceptance criteria

- `ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound")` returns a `Failure` whose `errorCode()` equals `"FAMILY_NOT_FOUND"` and `message()` equals `BackendMessages.message("family.familyNotFound")`.
- `ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound", "arg")` passes the variable to `BackendMessages.message`.
- None of the 11 service classes declare a private `failure(errorCode, messageKey)` static method after migration.
- All existing service-layer tests pass without modification.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='ServiceResultsTest,*ServiceImplTest,*ServiceTest'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/common/ServiceResults.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/account/AccountServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/analytics/AnalyticsServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/command/FamilyCommandServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardQueryServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyChildManagementService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyFriendService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyNotificationServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyPreferenceService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramChildConnectionServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentInvitationServiceImpl.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/common/ServiceResultsTest.java
git commit -m "refactor(backend): extract ServiceResults failure factory across 11 services"
```

---

## P1-1: Extract `FamilyOperationGuard` for family ownership

**Status:** ✅ Completed  
**Priority:** P1  
**Depends on:** P0-3

### Outcome

A single `FamilyOperationGuard` bean provides
`OperationResult<Integer> requireFamilyDbId(String familyId)`, replacing ~15
duplicated 3-line family-db-id resolution guards and fixing the
`FamilyActionBalanceService` inconsistency where `FAMILY_NOT_FOUND` error code is
omitted.

### Architectural decision

`service/family/` owns this `@ApplicationScoped` CDI bean because it depends on
`FamilyRepository` (or `FamilyActionSupportService`) and produces
`OperationResult`. It is injected into every family-scoped service. The
`FamilyActionSupportService.getFamilyDbId` method already encapsulates the lookup;
the guard wraps it with the `isEmpty`→`Failure("FAMILY_NOT_FOUND")` branch so call
sites become one line: `var familyDbId = guard.requireFamilyDbId(familyId); if
(familyDbId.isFailure()) return familyDbId.asFailure();`.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyOperationGuard.java`.
- Modify `service/family/action/FamilyActionBalanceService.java` (4 guard sites — also fix to emit `FAMILY_NOT_FOUND` error code).
- Modify `service/family/action/FamilyActionBulkService.java` (1 site).
- Modify `service/family/action/FamilyActionRequestService.java` (5 sites).
- Modify `service/family/FamilyHistoryQueryServiceImpl.java` (2 sites).
- Modify `service/family/FamilyChildManagementService.java` (multiple sites).
- Modify `service/system/SuperAdminService.java` (1 site).
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/FamilyOperationGuardTest.java`.

### Work

1. Create `FamilyOperationGuard` as `@ApplicationScoped` with constructor-injected `FamilyActionSupportService` (or `FamilyRepository`).
2. Implement `OperationResult<Integer> requireFamilyDbId(String familyId)`:
   - `Optional<Integer> dbId = supportService.getFamilyDbId(familyId);`
   - `return dbId.<OperationResult<Integer>>map(OperationResult::success).orElseGet(() -> ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound"));`
3. In `FamilyActionBalanceService`, replace the 4 `OperationResult.failure(BackendMessages.message(...))` (no error code) guards with `guard.requireFamilyDbId(familyId)` — this fixes the error code to `FAMILY_NOT_FOUND`, matching all other services.
4. In the other 5 services, replace the `getDbId`+`isEmpty`+`failure("FAMILY_NOT_FOUND", ...)` blocks with the guard call.
5. Write unit tests: existing family → success with dbId; non-existent family → `Failure` with `errorCode="FAMILY_NOT_FOUND"`.

### Acceptance criteria

- `requireFamilyDbId` for an existing family returns `Success(dbId)`.
- `requireFamilyDbId` for a non-existent family returns `Failure` with `errorCode="FAMILY_NOT_FOUND"` and message `BackendMessages.message("family.familyNotFound")`.
- `FamilyActionBalanceService` failure responses now carry `errorCode="FAMILY_NOT_FOUND"` (previously `null`).
- No service class contains the `getDbId(...).isEmpty()` guard pattern after migration.
- All existing family action and history query tests pass.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='FamilyOperationGuardTest,*FamilyAction*Test,*FamilyHistoryQuery*Test,*FamilyChildManagement*Test'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyOperationGuard.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionBalanceService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionBulkService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionRequestService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyChildManagementService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/system/SuperAdminService.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/FamilyOperationGuardTest.java
git commit -m "refactor(backend): extract FamilyOperationGuard and fix FAMILY_NOT_FOUND error code consistency"
```

---

## P1-2: Extract `ChildOwnershipService`

**Status:** ✅ Completed  
**Priority:** P1  
**Depends on:** P1-1

### Outcome

A single `ChildOwnershipService` bean provides
`Optional<ChildEntity> findFamilyChild(int familyDbId, int childId)`, replacing 6
duplicated copies of the `findByIdOptional(childId).filter(c ->
Objects.equals(c.getFamilyDbId(), familyDbId))` helper.

### Architectural decision

`service/family/` owns this `@ApplicationScoped` bean. It depends on
`ChildRepository`. `FamilyActionSupportService` already has the canonical copy;
rather than make 5 other services depend on `FamilyActionSupportService` (which
also carries action-specific helpers), extract the child-ownership lookup into its
own focused bean. `FamilyActionSupportService` delegates to it to preserve its
existing callers.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/ChildOwnershipService.java`.
- Modify `service/family/action/FamilyActionSupportService.java` (delegate `findFamilyChild` to the new bean).
- Modify `service/family/FamilyChildManagementService.java`.
- Modify `service/family/FamilyHistoryQueryServiceImpl.java`.
- Modify `service/family/FamilyNotificationServiceImpl.java`.
- Modify `service/telegram/TelegramChildConnectionServiceImpl.java`.
- Modify `service/telegram/TelegramIdentityServiceImpl.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/ChildOwnershipServiceTest.java`.

### Work

1. Create `ChildOwnershipService` as `@ApplicationScoped` with constructor-injected `ChildRepository`.
2. Implement `Optional<ChildEntity> findFamilyChild(int familyDbId, int childId)` — `childRepository.findByIdOptional(childId).filter(c -> Objects.equals(c.getFamilyDbId(), familyDbId))`.
3. In `FamilyActionSupportService`, delegate the existing `findFamilyChild` to the new bean (keeps callers stable).
4. In the other 4 services, inject `ChildOwnershipService` and replace the private helper with the injected call.
5. Write unit tests: child exists in family → `Optional.of(child)`; child exists in different family → `Optional.empty()`; child does not exist → `Optional.empty()`.

### Acceptance criteria

- `findFamilyChild` returns the child only when `child.familyDbId` equals the supplied `familyDbId`.
- `FamilyActionSupportService.findFamilyChild` delegates to `ChildOwnershipService`.
- No service class other than `FamilyActionSupportService` (which delegates) contains a private `findFamilyChild` helper after migration.
- All existing tests pass.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='ChildOwnershipServiceTest,*FamilyAction*Test,*FamilyChildManagement*Test,*FamilyHistoryQuery*Test,*FamilyNotification*Test,*TelegramChildConnection*Test,*TelegramIdentity*Test'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/ChildOwnershipService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionSupportService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyChildManagementService.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyNotificationServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramChildConnectionServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramIdentityServiceImpl.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/ChildOwnershipServiceTest.java
git commit -m "refactor(backend): extract ChildOwnershipService for family-scoped child lookup"
```

---

## P1-3: Extract `HistoryDtoMapper` and `RelatedEntityHydrator`

**Status:** ✅ Completed  
**Priority:** P1  
**Depends on:** -

### Outcome

Two focused helpers remove ~100 verbatim-duplicated lines:
`HistoryDtoMapper` maps `HistoryEntryEntity`→`HistoryEntryDto` (duplicated in 3
classes), and `RelatedEntityHydrator` batch-resolves missing task/shop entities for
history rows (duplicated verbatim in 2 classes).

### Architectural decision

`service/family/` owns both as `@ApplicationScoped` beans because they depend on
`FamilyRelatedDetailsResolver`, `TaskRepository`, and `ShopItemRepository`.
`HistoryDtoMapper` is a hand-written mapper (not MapStruct) because the skill
standard requires MapStruct only when the same mapping repeats across 3+ services
and the mapping is non-trivial — here it is 3 sites but short, so an explicit
mapper bean is clearer and avoids a new MapStruct dependency surface.
`RelatedEntityHydrator` takes the history rows plus the existing task/shop maps and
fills in missing entries by collecting unsatisfied IDs and batch-fetching them.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/HistoryDtoMapper.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/RelatedEntityHydrator.java`.
- Modify `service/family/FamilyHistoryQueryServiceImpl.java` (delete `toHistoryDto`, `hydrateMissingHistoryEntries`, `hydrateMissingRequests`, `buildTaskMap`, `buildShopItemMap`; inject the new beans).
- Modify `service/family/dashboard/FamilyDashboardHydrator.java` (delete the duplicated `hydrateMissingHistoryEntries`, `hydrateMissingRequests`; inject the new beans).
- Modify `service/system/SuperAdminService.java` (replace `toHistoryPayload` with `HistoryDtoMapper` call).
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/HistoryDtoMapperTest.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/RelatedEntityHydratorTest.java`.

### Work

1. Create `HistoryDtoMapper` with `HistoryEntryDto toDto(HistoryEntryEntity entry, Map<Long, TaskDto> taskMap, Map<Long, ShopItemDto> shopMap)` — extract the canonical logic from `FamilyHistoryQueryServiceImpl.toHistoryDto` (delegates detail resolution to `FamilyRelatedDetailsResolver`, formats `createdAt.toString()`).
2. Create `RelatedEntityHydrator` with:
   - `void hydrateMissingHistoryEntries(List<HistoryEntryEntity> entries, Map<Long, TaskDto> taskMap, Map<Long, ShopItemDto> shopMap)` — collect missing task/shop IDs, batch-fetch via repositories, populate maps with `putIfAbsent`.
   - `void hydrateMissingRequests(List<PurchaseRequestEntity> requests, Map<Long, TaskDto> taskMap, Map<Long, ShopItemDto> shopMap)` — same pattern for request related IDs.
3. Update `FamilyHistoryQueryServiceImpl` and `FamilyDashboardHydrator` to inject and call the new beans; delete the duplicated private methods.
4. Update `SuperAdminService.toHistoryPayload` to call `HistoryDtoMapper`.
5. Write unit tests for `HistoryDtoMapper`: earn entry with related task → dto has task details; spend entry with related shop item → dto has shop details; entry with null relatedId → dto has empty details. Write tests for `RelatedEntityHydrator`: entries referencing tasks already in map → no fetch; entries referencing missing tasks → batch fetch and populate.

### Acceptance criteria

- `HistoryDtoMapper.toDto` produces the same `HistoryEntryDto` shape (fields, `createdAt` format, detail resolution) as the previous private methods — verified by existing integration tests.
- `RelatedEntityHydrator.hydrateMissingHistoryEntries` does not re-fetch tasks/shop items already present in the supplied maps.
- `FamilyHistoryQueryServiceImpl` and `FamilyDashboardHydrator` no longer declare `hydrateMissingHistoryEntries` or `hydrateMissingRequests`.
- `SuperAdminService` no longer declares `toHistoryPayload`.
- All existing history, dashboard, and super-admin tests pass.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='HistoryDtoMapperTest,RelatedEntityHydratorTest,*FamilyHistoryQuery*Test,*FamilyDashboard*Test,*SuperAdminService*Test'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/HistoryDtoMapper.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/RelatedEntityHydrator.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardHydrator.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/system/SuperAdminService.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/HistoryDtoMapperTest.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/RelatedEntityHydratorTest.java
git commit -m "refactor(backend): extract HistoryDtoMapper and RelatedEntityHydrator from duplicated service code"
```

---

## P1-4: Extract `PageRequest` pagination helper

**Status:** ✅ Completed  
**Priority:** P1  
**Depends on:** -

### Outcome

A `PageRequest` record and `Pagination` utility normalize page-size clamping,
replacing 4 duplicated clamping blocks and fixing the inconsistent max-page-size
constant (100 in history services vs 500 in system dashboard).

### Architectural decision

`service/common/` owns `PageRequest` because both resource and service layers
consume pagination parameters. The max page size is centralized as a configurable
constant (default 100, overridable per call site via
`PageRequest.of(page, limit, maxPageSize)`) — this resolves the 100-vs-500
inconsistency by making the limit explicit at each call site rather than implicit.
A `Pagination.clamp(limit, max)` static helper backs `PageRequest`.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/common/PageRequest.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/common/Pagination.java`.
- Modify `service/family/FamilyHistoryQueryServiceImpl.java` (replace `MAX_PAGE_SIZE` + clamping with `PageRequest`).
- Modify `resource/system/SystemDashboardResource.java` (replace `Math.min(limit, 500)` with `PageRequest.of(page, limit, 500)`).
- Modify `resource/system/SuperAdminResource.java` (adopt `PageRequest`).
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/common/PageRequestTest.java`.

### Work

1. Create `PageRequest` record `(int page, int limit)` with `static PageRequest of(int page, int limit, int maxLimit)` that clamps `limit` to `[1, maxLimit]` and `page` to `[1, ∞)`, plus `int offset()` returning `(page - 1) * limit`.
2. Create `Pagination` with `static int clampLimit(int limit, int maxLimit)` and `static int offset(int page, int limit)`.
3. In `FamilyHistoryQueryServiceImpl`, replace `MAX_PAGE_SIZE=100` and the `Math.min(Math.max(limit, 1), MAX_PAGE_SIZE)` blocks with `PageRequest.of(page, limit, 100)`.
4. In `SystemDashboardResource` and `SuperAdminResource`, replace inline clamping with `PageRequest.of(page, limit, 500)` (or the existing per-resource max).
5. Write unit tests: `of(0, 50, 100)` clamps page to 1; `of(2, 0, 100)` clamps limit to 1; `of(1, 999, 100)` clamps limit to 100; `offset()` = `(page-1)*limit`.

### Acceptance criteria

- `PageRequest.of(2, 50, 100).offset()` returns `50`.
- `PageRequest.of(1, 999, 100).limit()` returns `100`.
- `PageRequest.of(0, 50, 100).page()` returns `1`.
- `FamilyHistoryQueryServiceImpl` no longer declares `MAX_PAGE_SIZE` or inline clamping.
- `SystemDashboardResource` no longer inlines `Math.min(limit, 500)`.
- All existing pagination-dependent tests pass with identical response sizes.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='PageRequestTest,*FamilyHistoryQuery*Test,*SystemDashboardResourceTest,*SuperAdminResourceTest'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/common/PageRequest.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/common/Pagination.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/FamilyHistoryQueryServiceImpl.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/system/SystemDashboardResource.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/system/SuperAdminResource.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/common/PageRequestTest.java
git commit -m "refactor(backend): extract PageRequest pagination helper and fix inconsistent max page size"
```

---

## P1-5: Extract `PanachePagination` repository helper

**Status:** ✅ Completed  
**Priority:** P1  
**Depends on:** P1-4

### Outcome

A `PanachePagination` static helper wraps the `.range(offset, offset + limit - 1).list()`
pattern, replacing 3 duplicated blocks across `HistoryRepository` and
`PurchaseRequestRepository`.

### Architectural decision

`repository/` owns this because it wraps Panache `Query` operations. It is a `final`
utility class with static methods, taking a `PanacheQuery<T>` plus `PageRequest` and
returning `List<T>`. It does not introduce a new abstraction layer — it is a
one-liner convenience that removes the repeated 4-line `.range().list()` block.

### Files

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PanachePagination.java`.
- Modify `repository/HistoryRepository.java` (2 paginated query sites).
- Modify `repository/PurchaseRequestRepository.java` (1 paginated query site).
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/repository/PanachePaginationTest.java`.

### Work

1. Create `PanachePagination` with `static <T> List<T> page(PanacheQuery<T> query, PageRequest pageRequest)` — `query.range(pageRequest.offset(), pageRequest.offset() + pageRequest.limit() - 1).list()`.
2. In `HistoryRepository`, replace the two `find(...).range(offset, offset + limit - 1).list()` blocks with `PanachePagination.page(find(...), pageRequest)`.
3. In `PurchaseRequestRepository`, replace the one paginated block likewise.
4. Write a unit test using a mock `PanacheQuery` verifying `range(offset, offset + limit - 1)` is called with correct values derived from `PageRequest`.

### Acceptance criteria

- `PanachePagination.page(query, PageRequest.of(2, 50, 100))` calls `query.range(50, 99)` and returns `query.list()`.
- `HistoryRepository` no longer inlines `.range(offset, offset + limit - 1).list()`.
- `PurchaseRequestRepository` no longer inlines the paginated range block.
- All existing repository and integration tests pass.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='PanachePaginationTest,*HistoryRepositoryTest,*PurchaseRequestRepositoryTest,*FamilyHistoryQuery*Test'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PanachePagination.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java \
  apps/backend/src/test/java/com/sashplatonov/earnit/kids/repository/PanachePaginationTest.java
git commit -m "refactor(backend): extract PanachePagination helper for paginated repository queries"
```

---

## P2-1: Remove noise `EXPLAIN:` comments — DTO layer

**Status:** ✅ Completed  
**Priority:** P2  
**Depends on:** -

### Outcome

DTO-layer `EXPLAIN:` comments that restate obvious behavior (convenience
constructors, field semantics clear from the record name,ADM cross-references) are
removed. Comments explaining genuinely non-obvious mapping or status defaults are
kept.

### Architectural decision

The DTO layer is the lowest-risk starting point because records have no behavior —
comment removal cannot break tests. The rule: remove comments that describe *what*
the code does in obvious terms (`// EXPLAIN: Convenience constructor preserving the
pre-status call signature.`); keep comments that explain *why* a non-obvious
default or mapping exists. Each file is reviewed individually; no bulk regex
deletion.

### Files

Review and modify (remove noise comments, keep complex-flow comments) in:
- `dto/response/ChildDto.java` — remove "Convenience constructor" comment.
- `dto/response/ShopItemDto.java` — remove "Convenience constructor" comment.
- `dto/response/TaskDto.java` — remove "Convenience constructor" comment.
- `dto/response/AdminActivationFunnelResponse.java` — remove "ADM-12" cross-reference (obvious from class name).
- `dto/response/AdminCoinEconomyResponse.java` — keep median-days comment (explains a non-obvious metric).
- `dto/response/AdminDashboardResponse.java` — remove "ADM-16" cross-reference.
- `dto/response/AdminParentBehaviorResponse.java` — remove "ADM-10" cross-reference and "Optional: AI-generated insights" (obvious from field name).
- `dto/response/AdminChildBehaviorResponse.java` — remove "ADM-11" cross-reference.
- `dto/response/AdminRetentionResponse.java` — remove "ADM-13" cross-reference.
- `dto/response/AdminTrendsResponse.java` — remove "ADM-14" cross-reference.
- `dto/request/CreateRequestNoteRequest.java` — remove "Optional payload" and "Single-line, up to 120 characters" (Bean Validation annotations already express this).

### Work

1. For each listed DTO file, read the file and identify `EXPLAIN:` comments.
2. Remove comments that restate obvious information (constructor purpose obvious from signature, field semantics obvious from name, ADM-NN cross-references that duplicate the class name).
3. Keep comments that explain a non-obvious calculation, default, or cross-domain reference (e.g. `AdminCoinEconomyResponse` median-days metric).
4. Run Checkstyle to confirm no forbidden-comment violations are introduced (all remaining comments still start with `EXPLAIN:`/`FIXME:`).

### Acceptance criteria

- No DTO file contains an `EXPLAIN:` comment that merely restates the constructor purpose or field name.
- Comments explaining non-obvious metrics (e.g. median-days in `AdminCoinEconomyResponse`) remain.
- `./mvnw validate` passes (Checkstyle — no comment-policy violations).
- `./mvnw test` passes (DTOs have no behavior; tests unaffected).

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ChildDto.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ShopItemDto.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/TaskDto.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminActivationFunnelResponse.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminDashboardResponse.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminParentBehaviorResponse.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminChildBehaviorResponse.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminRetentionResponse.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminTrendsResponse.java \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/CreateRequestNoteRequest.java
git commit -m "refactor(backend): remove noise EXPLAIN comments from DTO layer"
```

---

## P2-2: Remove noise `EXPLAIN:` comments — service/telegram

**Status:** ✅ Completed  
**Priority:** P2  
**Depends on:** P2-1

### Outcome

`service/telegram/` has 30 files with `EXPLAIN:` comments — the largest
concentration. Comments that restate obvious button-label purpose or copy-template
semantics are removed; comments explaining non-obvious Telegram API quirks, deep
link flow, or bot-menu decision trees are kept.

### Architectural decision

The telegram package has the highest comment density because it encodes Telegram
bot UX flow, which is genuinely non-obvious. The review is conservative: only
remove comments that describe *what* a constant or method obviously is (e.g. `//
EXPLAIN: Button labels: exactly one semantic emoji each.` above a list of labeled
constants is obvious from the constant names and the `TelegramBotEmoji` prefix).
Keep comments that explain *why* a flow branches a certain way, Telegram API
constraints, or deep-link encoding. Review each of the 30 files individually.

Priority files by comment count:
`TelegramCopy.java` (37), `TelegramMenuBuilder.java` (20),
`TelegramReplyKeyboard.java` (15), `TelegramMiniAppAuthService.java` (15),
`TelegramReplyKeyboardNavigator.java` (14), `TelegramBotApiClient.java` (14),
`BotKeyboardFactory.java` (13), `TelegramOutboxProcessor.java` (12),
`TelegramNotificationComposer.java` (11), `TelegramRequestResolutionText.java`
(9), `TelegramBotServiceImpl.java` (8), `TelegramFeatureSupport.java` (7).

### Files

Review and modify (remove noise, keep complex-flow) in all 30 files under
`service/telegram/` that contain `EXPLAIN:` comments. The full list is produced by:
`grep -rln "EXPLAIN:" src/main/java/com/sashplatonov/earnit/kids/service/telegram --include="*.java"`.

### Work

1. For each of the 30 files, read the file and classify each `EXPLAIN:` comment:
   - **Remove**: comments that restate what the code obviously does (button label lists, message template descriptions, obvious enum/constant semantics).
   - **Keep**: comments explaining Telegram API quirks, deep-link encoding, bot-menu decision flow, plural-form rules, webhook retry semantics, outbox ordering.
2. Remove noise comments; leave a blank line only if removal would merge two unrelated code blocks (otherwise collapse the blank line too).
3. After each batch of ~5 files, run `./mvnw validate` to confirm Checkstyle passes.

### Acceptance criteria

- No file in `service/telegram/` contains an `EXPLAIN:` comment that restates obvious constant/method purpose.
- Comments explaining Telegram API constraints, deep-link encoding, and bot-menu flow remain.
- `./mvnw validate` passes.
- `./mvnw test` passes (telegram service tests verify behavior is unchanged).

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*Telegram*Test'
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/
git commit -m "refactor(backend): remove noise EXPLAIN comments from telegram service layer"
```

---

## P2-3: Remove noise `EXPLAIN:` comments — repository/remaining

**Status:** ✅ Completed
**Priority:** P2  
**Depends on:** P2-2

### Outcome

`AdminAnalyticsRepository` (25 comments), `HistoryRepository` (7 comments), and
the remaining packages (`repository/command/`, `domain/model/`, `service/family/`,
`service/analytics/`, `service/account/`, `api/`, `config/`, `service/family/action/`,
`service/family/dashboard/`, `service/telegram/admin/`, `dto/request/`) have noise
`EXPLAIN:` comments removed. Comments explaining query semantics, index rationale,
or non-obvious filtering logic are kept.

### Architectural decision

`AdminAnalyticsRepository` is the largest target (25 comments). Many comments
cross-reference `FamilyDashboardScopeLoader`/`FamilyNotificationServiceImpl` to
explain why "active children" is defined as `status = ACTIVE` — this is a
non-obvious domain definition and those comments are kept. Comments that merely
label a metric as `ADM-05` or restate the query's obvious purpose are removed.
`HistoryRepository` comments explaining the `slowOperationDiagnostics` wrapping are
noise (the wrapping is obvious from the code) and removed.

### Files

Review and modify in (produced by `grep -rln "EXPLAIN:"
src/main/java/com/sashplatonov/earnit/kids --include="*.java" | grep -v
service/telegram | grep -v "dto/response"`):
- `repository/AdminAnalyticsRepository.java` (25 comments).
- `repository/HistoryRepository.java` (7 comments).
- `repository/TelegramDeliveryRepository.java` (4 comments — keep: explains why only real message-ids are returned).
- `repository/command/TaskContentCommand.java`, `TaskUpsertCommand.java`, `ShopItemUpsertCommand.java` — remove "Convenience constructor" comments.
- `domain/model/`, `service/family/`, `service/family/action/`, `service/family/dashboard/`, `service/analytics/`, `service/account/`, `api/`, `config/`, `service/telegram/admin/` — review and remove noise.

### Work

1. For `AdminAnalyticsRepository`: remove `ADM-NN` cross-reference comments that duplicate the method name; keep comments explaining the `status = ACTIVE` domain definition and median-days calculation rationale.
2. For `HistoryRepository`: remove comments restating `slowOperationDiagnostics.recordQuery` purpose (obvious from the call); keep comments explaining query plan or index rationale if any.
3. For `repository/command/`: remove "Convenience constructor preserving the pre-icon call signature" comments (3 files).
4. For remaining files: review each `EXPLAIN:` comment individually; remove noise, keep complex-flow.
5. Run `./mvnw validate` after each batch.

### Acceptance criteria

- No file under `repository/` contains an `EXPLAIN:` comment that restates obvious query purpose or diagnostic-wrapping purpose.
- Comments explaining the `status = ACTIVE` domain definition and median calculation in `AdminAnalyticsRepository` remain.
- Comments in `TelegramDeliveryRepository` explaining the real-message-id filter remain.
- `./mvnw validate` passes.
- `./mvnw test` passes.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/ \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/ \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/analytics/ \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/account/ \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/api/ \
  apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/
git commit -m "refactor(backend): remove noise EXPLAIN comments from repository and remaining layers"
```

---

## P2-4: Adopt `ResourceAuthSupport` across all resources

**Status:** ✅ Completed
**Priority:** P2  
**Depends on:** P0-2

### Outcome

All resource classes that currently inline `getAuthOrFail`/`unauthorized`/`badRequest`
or the 2-line auth guard migrate to `ResourceAuthSupport`, eliminating the remaining
~10 per-class copies and standardizing the unauthorized message to
`BackendMessages.message("errors.unauthorized")` everywhere.

### Architectural decision

This is the adoption phase of P0-2. Resources that cannot extend `FamilyResourceSupport`
(non-family resources) extend `ResourceAuthSupport` directly or inject it as a CDI
bean. The `requireX` throwing methods replace the 2-line `if (auth == null ||
!auth.roleCheck()) return unauthorized();` guards. The `AccountResource`,
`FamilyNotificationResource`, and telegram resources that hardcode
`"Authentication is required."` are standardized to the i18n message.

### Files

- Modify `resource/account/AccountResource.java` (remove private `getAuthOrFail`/`unauthorized`/`badRequest`/`response`/`voidResponse`; extend `ResourceAuthSupport` or inject; use `OperationResultResponses` for result mapping).
- Modify `resource/family/FamilyNotificationResource.java` (remove private auth trio + inline switch; delegate to `ResourceAuthSupport` + `OperationResultResponses`).
- Modify `resource/common/PushResource.java` (remove private auth trio).
- Modify `resource/system/SuperAdminResource.java` (remove `requireSuperAdmin(ctx)` returning Response; use `ResourceAuthSupport.requireSuperAdmin`).
- Modify `resource/system/SystemDashboardResource.java` (remove `requireSuperAdmin(ctx)` duplicate).
- Modify `resource/telegram/TelegramAccountConnectionResource.java`, `TelegramChildConnectionResource.java`, `TelegramParentInviteResource.java` (remove inline `response()` switches; use `OperationResultResponses`).
- Modify `resource/family/FamilyParentAccessResource.java` (replace 3 inline switches with `OperationResultResponses` + `FailureStatusResolver`).
- Modify `resource/family/FamilyChildSettingsResource.java` (replace inline switch with `OperationResultResponses`).
- Modify the 8 telegram admin resources that inline `ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)`.

### Work

1. For each resource, replace the private auth-extraction methods with `ResourceAuthSupport` inheritance or injection.
2. Replace the 2-line auth guard at the top of each endpoint with `var auth = requireAdmin(ctx);` (or `requireChild`/`requireSuperAdmin` as appropriate) — the throwing version aborts the request automatically.
3. Replace inline `OperationResult`→`Response` switches with `OperationResultResponses.toOk`/`toVoidOk`/`toCreated`.
4. For `FamilyParentAccessResource` sites needing 404/500 status, use the `FailureStatusResolver` overload.
5. Standardize all `unauthorized()` messages to `BackendMessages.message("errors.unauthorized")`.
6. Run the full resource test suite after each resource migration.

### Acceptance criteria

- No resource class declares a private `getAuthOrFail`, `unauthorized`, `badRequest`, or `requireSuperAdmin` method after migration.
- No resource class contains an inline `switch (result)` for `OperationResult` mapping after migration.
- All `401` responses use `BackendMessages.message("errors.unauthorized")` (no hardcoded `"Authentication is required."`).
- All existing resource and integration tests pass.
- HTTP response status codes, error codes, and error messages are byte-identical to pre-refactor (verified by integration tests).

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*ResourceTest,*Resource*Test'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/
git commit -m "refactor(backend): adopt ResourceAuthSupport and OperationResultResponses across all resources"
```

---

## P0-4: Restore H2-compatible Flyway startup after V39

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** -

### Outcome

Quarkus starts with the complete Flyway chain in the H2 test profile, so the two
currently blocked Quarkus tests and local `/q/openapi` verification can run. The
production migration history remains checksum-safe and family identifiers remain
non-reversible/non-email-derived values.

### Architectural decision

V39 has been applied in production, so its checksum and production behavior are
immutable. Add a test-resource migration with the same version/name that shadows
the production resource only in H2 tests and uses one H2-compatible `MD5` update
to model the intended post-V39 state. Do not modify V39 and do not add a new
production remapping migration: either would risk Flyway validation failure or a
second irreversible identifier change in production.

### Files

- Create `apps/backend/src/test/resources/db/migration/V39__randomize_family_ids.sql` as the H2-only counterpart to the immutable production migration.
- Create or modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/repository/FamilyIdRandomizationMigrationTest.java`.
- Modify the existing Quarkus tests only to remove temporary workarounds, if any.

### Work

1. Preserve the applied production V39 byte-for-byte; do not run Flyway repair and do not create a production follow-up that rewrites `family_id`.
2. Make the H2 test migration chain compatible by adding its version-matched test resource, preserving the intended contract that an existing `families.family_id` becomes a `fam_`-prefixed, deterministic non-email value exactly once.
3. Add migration coverage for a seeded family ID and for application startup; assert the resulting identifier is prefixed, changed once, unique, and does not expose the original email-derived input.
4. Re-run the two currently failing Quarkus tests before the full backend gate.

### Acceptance criteria

- H2 executes V39 without `ENCODE`/`DIGEST` function errors.
- The H2 test counterpart executes exactly one `UPDATE families SET family_id` statement.
- The production V39 file and its Flyway checksum are unchanged.
- `AdjustBalanceRequestValidationTest` and `NewRelicMetricsExportSmokeTest` boot Quarkus and pass.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='AdjustBalanceRequestValidationTest,NewRelicMetricsExportSmokeTest,FamilyIdRandomizationMigrationTest'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test
```

### Commit

```bash
git add apps/backend/src/test/resources/db/migration/ apps/backend/src/test/java/com/sashplatonov/earnit/kids/repository/
git commit -m "fix(backend): restore V39 migration test startup"
```

---

## P0-5: Remove SpotBugs ownership-leak suppressions

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** P0-4

### Outcome

SpotBugs reports no medium-or-higher defects without filtering `EI_EXPOSE_REP`,
`EI_EXPOSE_REP2`, or `BX_UNBOXING_IMMEDIATELY_REBOXED`. Mutable request-scoped
objects and collections no longer escape CDI beans or response DTOs by reference.

### Architectural decision

Each class owns its mutable state. Constructor-injected mutable collections and
request contexts are copied or transformed at the owning boundary; response DTOs
expose immutable snapshots. Remove the filter entries only together with the
root-cause code fixes and tests—do not lower SpotBugs severity or introduce
annotations/suppressions.

### Files

- Modify `apps/backend/config/spotbugs-exclude.xml`.
- Modify the classes currently matched by that filter under `apps/backend/src/main/java/com/sashplatonov/earnit/kids/{config,i18n,resource,service,dto}/`.
- Create or modify focused unit tests beside each corrected component under `apps/backend/src/test/java/com/sashplatonov/earnit/kids/`.

### Work

1. Run SpotBugs with the current filter removed in a temporary working copy to enumerate the real defects by class and pattern.
2. Replace direct retention/return of mutable arrays, collections, request contexts, or boxed values with the appropriate immutable copy, value extraction, or primitive representation.
3. Remove every `EI_EXPOSE_REP`, `EI_EXPOSE_REP2`, and `BX_UNBOXING_IMMEDIATELY_REBOXED` match from `spotbugs-exclude.xml`; remove the filter reference altogether if no justified, non-fixable match remains.
4. Add tests demonstrating that mutation of an input collection/context cannot alter the component's retained state and that response collection mutations cannot alter subsequent reads.

### Acceptance criteria

- `config/spotbugs-exclude.xml` contains no suppression for the listed patterns.
- Standard `mvnw verify` runs SpotBugs with zero findings and no suppression/annotation added for this work.
- Existing HTTP response shapes and request-scoped locale/metrics behavior are unchanged.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='*ResourceTest,*ServiceTest,*DtoTest'
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw spotbugs:check
rg -n 'EI_EXPOSE_REP|BX_UNBOXING_IMMEDIATELY_REBOXED' config/spotbugs-exclude.xml
```

### Commit

```bash
git add apps/backend/config/spotbugs-exclude.xml apps/backend/src/main/java/ apps/backend/src/test/java/
git commit -m "fix(backend): remove SpotBugs ownership suppressions"
```

---

## P0-6: Restore and enforce 80% JaCoCo line coverage

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** P0-4

### Outcome

The backend's checked line coverage is at least 80%, enforced by Maven rather
than merely reported. Added tests exercise meaningful failure, authorization,
and persistence-boundary behavior rather than generated code or dead branches.

### Architectural decision

The P3-1 contract is the source of truth: the current `pom.xml` threshold of
0.65 and repository-package exclusion cannot define a weaker quality gate. Keep
only exclusions proven impossible to instrument and document that Quarkus reason
next to the configuration; otherwise remove them and cover the real behavior.
Coverage is raised through focused unit/integration tests, not reduced by wider
exclusions or changed production semantics.

### Files

- Modify `apps/backend/pom.xml`.
- Create or modify focused tests under `apps/backend/src/test/java/com/sashplatonov/earnit/kids/` for the lowest-covered production classes from `target/site/jacoco`.
- Modify production classes only when a test exposes an untestable dependency boundary that requires a behavior-preserving seam.

### Work

1. After P0-4 restores test startup, generate the JaCoCo XML/HTML report and rank uncovered executable lines by package and risk.
2. Add regression coverage for the highest-impact uncovered resource, service, and repository-adjacent branches, including authorization/failure paths introduced by this refactor.
3. Set the Maven JaCoCo bundle LINE `COVEREDRATIO` minimum to `0.80`; remove or narrowly justify any remaining package exclusion based on instrumentation evidence.
4. Ensure the check fails locally below the threshold and passes only with the measured 80%+ result.

### Acceptance criteria

- JaCoCo reports at least 0.80 checked line coverage after a clean test run.
- `mvnw verify` fails when the measured checked coverage is below 0.80.
- No new coverage exclusion, skipped test, or suppression is introduced to reach the target.
- New tests cover both successful and failure/authorization paths for affected code.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw clean verify
rg -n '<minimum>0\.80</minimum>|<exclude>' pom.xml
```

### Commit

```bash
git add apps/backend/pom.xml apps/backend/src/main/java/ apps/backend/src/test/java/
git commit -m "test(backend): enforce refactor coverage target"
```

---

## P2-5: Complete `OperationResult` response-mapping migration

**Status:** ✅ Completed

**Priority:** P2
**Depends on:** P0-1

### Outcome

No resource retains an inline `switch (result)` for `OperationResult` mapping.
The five remaining switches in `AuthResource` and `ChildMagicLinkResource` use
the existing common mapper while preserving authentication cookies, redirect
responses, API status codes, bodies, and error codes.

### Architectural decision

`OperationResultResponses` is the only common mapping mechanism. It must accept
resource-owned success callbacks and explicit failure status/error-code mapping
where auth behavior is not the generic 400 response. Cookie construction,
redirect origin resolution, and `AuthResponse` DTO selection remain in the auth
resources; do not create an auth mapper or move HTTP mechanics into services.

### Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/util/OperationResultResponses.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/auth/AuthResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/auth/ChildMagicLinkResource.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/util/OperationResultResponsesTest.java`.
- Modify or create resource tests for both auth resources.

### Work

1. Extend the existing mapper with the minimal callback/status overload needed by the auth success and failure paths.
2. Replace the four `AuthResource` and one `ChildMagicLinkResource` switches without changing the successful cookies, selection-required response, 201 registration response, 401/400/409 error codes, or 303 redirect locations.
3. Add response-level regression tests for each distinct status and cookie/redirect branch.
4. Search all resources after the change and remove only `OperationResult` switches; do not alter unrelated pattern matching.

### Acceptance criteria

- `rg -n -U 'switch\\s*\\(result\\)' apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource` returns no matches.
- Auth responses preserve their pre-change status, entity, `Set-Cookie` headers, error code, and redirect location for success and failure paths.
- The common mapper remains the single source of generic failure response construction.

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw test -Dtest='AuthResourceTest,ChildMagicLinkResourceTest,OperationResultResponsesTest'
rg -n -U 'switch\\s*\\(result\\)' src/main/java/com/sashplatonov/earnit/kids/resource
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/{resource/auth,util}/ apps/backend/src/test/java/com/sashplatonov/earnit/kids/{resource/auth,util}/
git commit -m "refactor(backend): complete result response mapping"
```

---

## P2-6: Finish removal of noise `EXPLAIN:` comments

**Status:** ✅ Completed
**Priority:** P2
**Depends on:** P2-1, P2-2, P2-3

### Outcome

The production source contains fewer than 20 `EXPLAIN:` comments, with every
remaining comment documenting a non-obvious algorithm, ordering constraint,
security boundary, or subtle bug workaround rather than restating identifiers or
the adjacent statement.

### Architectural decision

The existing Checkstyle rule remains the syntax guard for comments, but review
of semantic value belongs to the owning package. Remove comments in place;
neither new comments nor Checkstyle suppressions are an acceptable substitute.
Preserve comments only when deleting them would lose the reason a non-obvious
flow must remain as written.

### Files

- Modify the Java files returned by `rg -l 'EXPLAIN:' apps/backend/src/main/java/com/sashplatonov/earnit/kids --glob '*.java'` after individual review.
- Modify focused tests only if comment removal exposes dead code or stale behavior that must be removed safely.

### Work

1. Produce a per-file inventory of the 269 current comments and classify each as required rationale or decorative restatement.
2. Remove decorative comments across all packages, including the files not reached by the three completed package-oriented passes.
3. Keep only comments that explain a decision not apparent from names/types/control flow; do not reword noise to retain it.
4. Recount comments and run Checkstyle after the final edit.

### Acceptance criteria

- The exact production-source count is below 20.
- Every remaining comment begins with `EXPLAIN:` or `FIXME:` and explains a genuinely non-obvious constraint or algorithm.
- No Java behavior, API response, or test expectation changes solely because of this cleanup.

### Verification

```bash
cd apps/backend
rg -n 'EXPLAIN:' src/main/java/com/sashplatonov/earnit/kids --glob '*.java' | wc -l
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
```

### Commit

```bash
git add apps/backend/src/main/java/
git commit -m "refactor(backend): finish noise comment cleanup"
```

---

## P3-1: Final quality gate and coverage check

**Status:** ⛔ Blocked
**Priority:** P3  
**Depends on:** P0-1, P0-2, P0-3, P0-4, P0-5, P0-6, P1-1, P1-2, P1-3, P1-4, P1-5, P2-1, P2-2, P2-3, P2-4, P2-5, P2-6

### Outcome

The full backend quality gate passes after all refactoring: Checkstyle clean,
JaCoCo line coverage ≥ 0.80, SpotBugs clean, no `@SuppressWarnings` introduced, no
new suppressions in `config/`, and the OpenAPI spec at `/q/openapi` is unchanged
(confirming no API contract drift).

### Architectural decision

This is a verification-only task. No code changes unless a gate fails — if a gate
fails, the fix is a follow-up to the responsible task, not a suppression. Coverage
must not drop below 0.80; if new extracted utilities lack coverage, add tests
(they already have tests from P0/P1 tasks, but this confirms the aggregate).

### Files

- No file changes expected. If a gate fails, modify the responsible file(s) identified by the gate output.

### Work

1. Run `./mvnw verify` and confirm: Checkstyle 0 errors, JaCoCo ≥ 0.80, SpotBugs 0 errors, tests 0 failures.
2. Run `./mvnw quarkus:build` to confirm JVM build smoke passes (no wiring errors from new CDI beans).
3. Start `./mvnw quarkus:dev` and `curl localhost:8080/q/openapi` — diff against pre-refactor spec (captured before starting) to confirm no API contract change.
4. Confirm `grep -rn "@SuppressWarnings" src/main/java` returns 0 results.
5. Confirm `grep -rn "EXPLAIN:" src/main/java | wc -l` is substantially reduced from the original 346 (target: under ~20, representing only genuinely complex-flow comments).
6. Confirm no new files in `config/` excluding patterns or suppressions.

### Acceptance criteria

- `./mvnw validate` passes with 0 Checkstyle errors.
- `./mvnw verify` passes with JaCoCo ≥ 0.80, SpotBugs 0 errors, all tests green.
- `./mvnw quarkus:build` succeeds.
- `/q/openapi` spec is identical to pre-refactor (no new/removed/changed operations, schemas, or status codes).
- `grep -rn "@SuppressWarnings" src/main/java/com/sashplatonov` returns 0 matches.
- Remaining `EXPLAIN:` comment count is under ~20 (only complex-flow comments survive).

### Verification

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw quarkus:build
grep -rn "@SuppressWarnings" src/main/java/com/sashplatonov --include="*.java" | wc -l
grep -rn "EXPLAIN:" src/main/java/com/sashplatonov --include="*.java" | wc -l
```

### Commit

```bash
# Only if fixes were needed; otherwise no commit.
git add <fixed files>
git commit -m "test(backend): restore quality gate coverage after refactor"
```
