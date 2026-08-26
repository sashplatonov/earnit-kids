# Dependency Update — Implementation Backlog

## Goal

Update all project dependencies (web, Telegram integration, Java, Quarkus, Docker base images, static analysis tools) to their latest stable versions while keeping the full build, test, lint, and Docker pipeline green. No behavioural changes to the product — only dependency upgrades with necessary compatibility fixes.

## Architectural decisions

- **Source of truth for versions:** `npm outdated` and `mvn versions:display-dependency-updates` / `display-property-updates` run against the current repository on 2026-08-26. Target versions are the latest stable (non-milestone, non-beta) releases identified by those commands.
- **Layer boundaries:** Backend upgrades are scoped to `apps/backend/pom.xml` and Dockerfiles. Web upgrades are scoped to `apps/web/package.json` and `apps/web/package-lock.json`. No application code changes unless a compatibility fix is required by the upgraded library.
- **Compatibility strategy:** Minor and patch updates are applied in grouped batches per ecosystem. Major version bumps (http-proxy-3 1→2, TypeScript 6→7) are isolated into separate tasks because they carry breaking API changes and require individual validation.
- **Telegram SDK:** The Telegram Mini App SDK (`telegram-web-app.js`) is loaded from `https://telegram.org/js/telegram-web-app.js` in `apps/web/src/app.html` with no version pin — Telegram serves the latest compatible version. The backend Telegram integration uses standard Quarkus REST/JAX-RS (no third-party Telegram bot library). Both are upgraded transitively via the Quarkus and web stack updates — no separate Telegram dependency exists.
- **Rejected duplicate approaches:** Do not introduce `npm-check-updates`, `renovate`, or `dependabot` config in this backlog. Do not split the Quarkus BOM from its managed dependencies — they move together as one batch.
- **Java 25 constraint:** `maven.compiler.release` must remain `25`. The `--enable-preview` flag must remain in surefire/failsafe `argLine`. No Java version changes.
- **Netty 4.1 → 4.2:** The Netty BOM override in `dependencyManagement` is a new minor series (4.2.x). Quarkus 3.39.1 may already manage Netty 4.2.x via its own BOM — the explicit override may need removal if Quarkus BOM already provides 4.2.17. Verify resolved version before pinning.
- **BouncyCastle migration:** `bcprov-jdk15on` is deprecated. The `web-push` library 5.1.2 pulls it transitively. If `web-push` latest (check Maven Central) already requires `bcprov-jdk18on`, update both together. Otherwise, keep the explicit `bcprov-jdk15on` override and update to its latest patch within the 1.x line.
- **MapStruct 1.7.0.Beta2 and Surefire 3.6.0-M1:** These are pre-release versions. Skip them — stay on 1.6.3 and 3.5.4 respectively until a stable release is available.
- **AssertJ 4.0.0-M1:** Milestone release — skip. Stay on 3.27.x line (update to 3.27.7).
- **Checkstyle property update shows 14.0.0:** That is a major version jump. The safe update within the 10.x line is 10.26.1 (shown in dependency-updates). Target 10.26.1, not 14.0.0.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P0-1 | P0 | - | Quarkus BOM is the foundation for all backend dependencies |
| 2 | P1-1 | P1 | P0-1 | Netty BOM must align with Quarkus BOM expectations |
| 3 | P1-2 | P1 | P0-1 | Jackson is a shared serialization contract |
| 4 | P1-3 | P1 | P0-1 | Third-party libraries (jsoup, google-api-client, etc.) |
| 5 | P1-4 | P1 | P0-1 | BouncyCastle + web-push security update |
| 6 | P1-5 | P1 | P0-1 | Test dependencies (mockito, assertj, rest-assured) |
| 7 | P1-6 | P1 | P0-1 | Static analysis tools (checkstyle, PMD, spotbugs) |
| 8 | P1-7 | P1 | P0-1 | Build plugins (jacoco) |
| 9 | P1-8 | P1 | - | Web npm patch/minor updates (safe batch) |
| 10 | P1-9 | P1 | P1-8 | Web npm minor feature updates (eslint, playwright, etc.) |
| 11 | P2-1 | P2 | P1-8 | http-proxy-3 major version (1→2), breaking changes |
| 12 | P2-2 | P2 | P1-9 | TypeScript major version (6→7), breaking changes |
| 13 | P2-3 | P2 | P0-1, P1-8 | Docker base image updates |
| 14 | P2-4 | P2 | - | Telegram SDK load strategy verification |
| 15 | P1-10 | P1 | All backend tasks | Backend full verification gate |
| 16 | P1-11 | P1 | All web tasks | Web full verification gate |
| 17 | P0-2 | P0 | P1-10, P1-11, P2-3 | Full-stack Docker Compose verification |

---

## TASK P0-1: Update Quarkus platform BOM to 3.39.1

**Status:** TODO
**Priority:** P0
**Depends on:** -

**Exact scope:**

Update the Quarkus platform version property in `apps/backend/pom.xml`. This controls the Quarkus BOM imported in `dependencyManagement` and the `quarkus-maven-plugin` version. All Quarkus-managed dependencies (quarkus-arc, quarkus-rest, quarkus-hibernate-orm, etc.) move from 3.37.2 to 3.39.1.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<quarkus.platform.version>3.37.2</quarkus.platform.version>` in `apps/backend/pom.xml`.

**Goal:**

Bring the Quarkus platform and all its managed extensions to the latest 3.39.1 release with no behavioural regressions.

### Outcome

The backend compiles and all tests pass against Quarkus 3.39.1. The Quarkus BOM manages all `io.quarkus:*` dependencies at version 3.39.1.

### Architectural decision

The Quarkus BOM is the single source of truth for all `io.quarkus:*` artifact versions. Updating the `quarkus.platform.version` property propagates to the BOM import and the `quarkus-maven-plugin`. No individual Quarkus extension versions should be overridden — they all resolve from the BOM. The explicit Netty and Jackson overrides in `dependencyManagement` remain in place but their versions are addressed in P1-1 and P1-2.

### Required changes

1. Change `<quarkus.platform.version>3.37.2</quarkus.platform.version>` to `3.39.1` in the `<properties>` block.
2. Run `./mvnw dependency:tree` to confirm all `io.quarkus:*` artifacts resolve to 3.39.1.
3. If Quarkus 3.39.1 introduces deprecation warnings or configuration property changes, address them in application code — do not suppress or ignore. Check `application.properties` for any properties flagged as removed or renamed in the Quarkus 3.38/3.39 migration notes.
4. If any test fails due to Quarkus internal changes (e.g., Panache, Hibernate ORM, REST), fix the test to align with the new behaviour — do not weaken assertions.

### Out of scope

- Netty BOM override version (handled in P1-1).
- Jackson databind override version (handled in P1-2).
- Static analysis tool versions (handled in P1-6).
- Docker base image updates (handled in P2-3).

### Acceptance criteria

- `./mvnw dependency:tree -Dincludes=io.quarkus` shows version `3.39.1` for all Quarkus artifacts.
- `./mvnw verify` passes with no new warnings compared to the pre-update baseline.
- No `@SuppressWarnings`, Checkstyle exclusions, or PMD exclusions added.
- `application.properties` contains no deprecated Quarkus properties.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml
git commit -m "chore(backend): bump Quarkus platform to 3.39.1"
```

---

## TASK P1-1: Update Netty BOM to 4.2.17.Final

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update the Netty BOM override in `apps/backend/pom.xml` from 4.1.135.Final to 4.2.17.Final. The `netty.version` property controls the `netty-bom` import and all explicit `io.netty:*` entries in `dependencyManagement`.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<netty.version>4.1.135.Final</netty.version>` in `apps/backend/pom.xml`.

**Goal:**

All Netty artifacts resolve to 4.2.17.Final with no runtime regressions in HTTP, WebSocket, or DNS resolution paths.

### Outcome

The backend uses Netty 4.2.17.Final consistently across all modules. `dependency:tree` shows no Netty version conflicts.

### Architectural decision

Netty 4.2.x is a new minor series within the 4.x line. The Quarkus 3.39.1 BOM (from P0-1) may already manage Netty at 4.2.x — check the resolved version after P0-1. If Quarkus already provides 4.2.17.Final, the explicit `netty-bom` override and all individual `io.netty:*` entries in `dependencyManagement` can be removed entirely (simplification). If Quarkus provides an older 4.2.x, keep the override and update to 4.2.17.Final. The decision is made by inspecting `dependency:tree -Dincludes=io.netty` output after P0-1 is applied.

### Required changes

1. Run `./mvnw dependency:tree -Dincludes=io.netty` after P0-1 to see what Quarkus 3.39.1 resolves Netty to.
2. If Quarkus already resolves 4.2.17.Final: remove the `netty-bom` import and all individual `io.netty:*` entries from `dependencyManagement` — let Quarkus BOM manage them.
3. If Quarkus resolves an older 4.2.x: update `<netty.version>` to `4.2.17.Final` and keep the override.
4. If Quarkus resolves 4.1.x: update `<netty.version>` to `4.2.17.Final` and keep the override — this forces the upgrade.
5. Verify no Netty API breakages in application code (the codebase does not directly import Netty — it's transitive via Quarkus/Vert.x).

### Out of scope

- Jackson databind version (P1-2).
- Application code changes beyond what Netty 4.2 API changes require.

### Acceptance criteria

- `./mvnw dependency:tree -Dincludes=io.netty` shows all Netty artifacts at 4.2.17.Final (or no explicit override if Quarkus BOM provides it).
- No Netty version conflicts or `dependencyConvergence` warnings.
- `./mvnw verify` passes.
- WebSocket endpoints (`quarkus-websockets-next`) and REST endpoints still function correctly in tests.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml
git commit -m "chore(backend): bump Netty to 4.2.17.Final"
```

---

## TASK P1-2: Update Jackson databind to 2.22.2

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update the Jackson databind override in `apps/backend/pom.xml` from 2.21.4 to 2.22.2. The `jackson.databind.version` property controls the explicit `jackson-databind` entry in `dependencyManagement`.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<jackson.databind.version>2.21.4</jackson.databind.version>` in `apps/backend/pom.xml`.

**Goal:**

Jackson databind resolves to 2.22.2 with no serialization/deserialization regressions in REST endpoints or DTO records.

### Outcome

All JSON request/response handling uses Jackson 2.22.2. Existing DTO record serialization and deserialization tests pass unchanged.

### Architectural decision

Jackson 2.22.x is a minor version bump. The explicit override exists because Quarkus may manage a different Jackson version. After P0-1, check if Quarkus 3.39.1 already provides Jackson 2.22.x — if so, the override can be removed. Otherwise, update the override to 2.22.2. Jackson minor versions are backward compatible for standard `@JsonCreator` records and `ObjectMapper` usage.

### Required changes

1. Run `./mvnw dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind` after P0-1 to see the Quarkus-managed version.
2. Update `<jackson.databind.version>` from `2.21.4` to `2.22.2` (or remove the override if Quarkus 3.39.1 already manages 2.22.2).
3. Run the full test suite — REST endpoints use `quarkus-rest-jackson` which relies on Jackson for JSON binding.
4. If any DTO record deserialization test fails, investigate the Jackson 2.22 migration notes for breaking changes and fix the DTO or configure `ObjectMapper` accordingly.

### Out of scope

- Netty version (P1-1).
- Other third-party libraries (P1-3).

### Acceptance criteria

- `./mvnw dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind` shows 2.22.2.
- All REST endpoint tests that serialize/deserialize JSON pass.
- No `@JsonIgnore` or `@JsonProperty` annotations added unless required by a Jackson 2.22 breaking change.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml
git commit -m "chore(backend): bump Jackson databind to 2.22.2"
```

---

## TASK P1-3: Update backend third-party libraries

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update standalone third-party dependencies in `apps/backend/pom.xml` that are not managed by the Quarkus BOM: jsoup, google-api-client, rest-assured, and assertj-core.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchors: `jsoup` (version `1.18.3`), `google-api-client` (version `2.8.1`), `rest-assured` (no version — managed by Quarkus BOM), `assertj-core` (version `3.27.3`).

**Goal:**

All standalone third-party libraries are at their latest stable versions with no API regressions.

### Outcome

jsoup 1.23.2, google-api-client 2.9.0, rest-assured 6.0.1, assertj-core 3.27.7. All existing tests pass.

### Architectural decision

These libraries have independent version lines and are either explicitly versioned (jsoup, google-api-client, assertj) or managed by the Quarkus BOM (rest-assured). jsoup 1.18→1.23 is a minor jump but jsoup maintains strong backward compatibility. google-api-client 2.8→2.9 is a minor bump. assertj 3.27.3→3.27.7 is a patch. rest-assured 6.0.0→6.0.1 is a patch (may be managed by Quarkus BOM — check after P0-1).

### Required changes

1. Update jsoup from `1.18.3` to `1.23.2` in the `<dependency>` block (inline version).
2. Update google-api-client from `2.8.1` to `2.9.0` in the `<dependency>` block (inline version).
3. Update assertj-core from `3.27.3` to `3.27.7` in the `<dependency>` block (inline version).
4. For rest-assured: after P0-1, check if Quarkus 3.39.1 BOM already manages 6.0.1. If yes, no change needed. If it still shows 6.0.0, add an explicit version override `6.0.1` in `dependencyManagement`.
5. Run full test suite — jsoup is used for HTML sanitization (`WebPushProtocolAdapter` and related), google-api-client for Google OAuth (`GoogleIdentityVerifier`), rest-assured for API tests, assertj for test assertions.
6. If jsoup 1.23 introduces sanitization behaviour changes, verify that HTML sanitization tests still pass and that no new tags or attributes are allowed through.

### Out of scope

- BouncyCastle / web-push (P1-4).
- Mockito (P1-5).
- Static analysis tools (P1-6).
- MapStruct (stay on 1.6.3 — 1.7.0.Beta2 is a beta).

### Acceptance criteria

- jsoup resolves to 1.23.2 in `dependency:tree`.
- google-api-client resolves to 2.9.0.
- assertj-core resolves to 3.27.7.
- rest-assured resolves to 6.0.1 (either from BOM or explicit override).
- Google OAuth verifier test (`GoogleIdentityVerifier`) passes.
- HTML sanitization tests pass.
- `./mvnw verify` passes.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml
git commit -m "chore(backend): bump jsoup, google-api-client, assertj, rest-assured"
```

---

## TASK P1-4: Update BouncyCastle and web-push library

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update the BouncyCastle provider and `web-push` library in `apps/backend/pom.xml`. The current `bcprov-jdk15on:1.70` is deprecated — migrate to `bcprov-jdk18on` if the latest `web-push` version requires it. The `web-push` library (nl.martijndwars) is used by `WebPushJavaAdapter` for VAPID-signed push notifications.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `bcprov-jdk15on` and `web-push` in `apps/backend/pom.xml`.
- Search anchor: `import org.bouncycastle.jce.provider.BouncyCastleProvider` in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushJavaAdapter.java`.

**Goal:**

Web push encryption uses the latest BouncyCastle provider and web-push library with no changes to push notification delivery behaviour.

### Outcome

`WebPushJavaAdapter` uses the updated BouncyCastle provider and web-push library. VAPID-signed push notifications are constructed and sent correctly. All web push related tests pass.

### Architectural decision

BouncyCastle `bcprov-jdk15on` is the legacy artifact (last release 1.78). The active artifact is `bcprov-jdk18on` (latest 1.81+). The `web-push` library 5.1.2 depends on `bcprov-jdk15on`. Check Maven Central for the latest `web-push` version:

- If latest `web-push` (e.g., 5.1.3+) already depends on `bcprov-jdk18on`: update `web-push` to latest, replace `bcprov-jdk15on` with `bcprov-jdk18on` (matching the version `web-push` expects), and update the import in `WebPushJavaAdapter` if the package path changed (it should not — `org.bouncycastle.jce.provider.BouncyCastleProvider` is stable across jdk15on/jdk18on).
- If `web-push` still uses `bcprov-jdk15on` at its latest version: update `bcprov-jdk15on` to its latest patch (1.78.1) and keep `web-push` at 5.1.2 or update to latest patch.
- The `BouncyCastleProvider` class name and `Security.addProvider()` API are identical between jdk15on and jdk18on — no code change expected unless the package moves.

### Required changes

1. Check Maven Central for latest `nl.martijndwars:web-push` version and its BouncyCastle dependency.
2. Check Maven Central for latest `org.bouncycastle:bcprov-jdk18on` (or `bcprov-jdk15on` if web-push hasn't migrated).
3. Update the `web-push` `<dependency>` to the latest stable version.
4. Update or replace the BouncyCastle `<dependency>`:
   - If migrating to jdk18on: change `<artifactId>bcprov-jdk15on</artifactId>` to `<artifactId>bcprov-jdk18on</artifactId>` and update the version to latest stable.
   - If staying on jdk15on: update version to latest (1.78.1).
5. Run `./mvnw dependency:tree -Dincludes=org.bouncycastle` to verify no duplicate BouncyCastle artifacts on the classpath.
6. Run web push tests — if none exist for `WebPushJavaAdapter` directly, run the full suite which includes `PushResource` and related integration tests.
7. Verify `WebPushJavaAdapter.java` still compiles — the `BouncyCastleProvider`, `Notification`, and `PushService` APIs should be unchanged.

### Out of scope

- jose4j (version 0.9.6 — check if update is available and include here if minor/patch only).
- argon2-jvm (version 2.12 — check if update is available and include here if minor/patch only).
- Lombok (version 1.18.46 — check if update is available and include here if minor/patch only).

### Acceptance criteria

- No `bcprov-jdk15on` on the classpath if migrated to `bcprov-jdk18on` (verify via `dependency:tree`).
- No duplicate BouncyCastle versions in `dependency:tree`.
- `WebPushJavaAdapter` compiles and the BouncyCastle provider is registered at startup.
- `./mvnw verify` passes.
- No `Security.addProvider` or `BouncyCastleProvider` API changes required in application code.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw dependency:tree -Dincludes=org.bouncycastle && ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushJavaAdapter.java
git commit -m "chore(backend): update BouncyCastle and web-push library"
```

---

## TASK P1-5: Update backend test dependencies

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update Mockito from 5.21.0 to 5.23.0 in `apps/backend/pom.xml`. The `mockito.version` property controls `mockito-core` and `mockito-junit-jupiter`. The surefire/failsafe `argLine` references the mockito-core jar path, so the version must be consistent.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<mockito.version>5.21.0</mockito.version>` in `apps/backend/pom.xml`.
- Search anchor: `mockito-core-${mockito.version}.jar` in `apps/backend/pom.xml` (surefire/failsafe argLine).

**Goal:**

Mockito 5.23.0 is used consistently across all test scopes with no mock behaviour regressions.

### Outcome

All Mockito-based tests (`AccountServiceImplTest`, `AuthServiceImplTest`, `ConstraintViolationExceptionMapperTest`, etc.) pass with Mockito 5.23.0. The surefire/failsafe `-javaagent` path resolves correctly to the 5.23.0 jar.

### Architectural decision

Mockito 5.23.0 is a minor version bump within the 5.x line. The `-javaagent` path in surefire/failsafe `argLine` uses `${mockito.version}` so it automatically resolves to the new version. Mockito 5.x minor versions maintain backward compatibility for `@Mock`, `@InjectMocks`, `MockitoExtension`, and `mock()` static methods used in the test suite.

### Required changes

1. Update `<mockito.version>5.21.0</mockito.version>` to `5.23.0`.
2. Run the full test suite — verify all Mockito-based unit tests pass.
3. If Mockito 5.23 introduces stricter mock behaviour (e.g., stricter stubbing), fix the test to align — do not use `@SuppressWarnings` or lenient configurations to suppress legitimate test issues.

### Out of scope

- AssertJ (updated in P1-3).
- Surefire plugin version (stay on 3.5.4 — 3.6.0-M1 is a milestone).
- JaCoCo (P1-7).

### Acceptance criteria

- `./mvnw dependency:tree -Dincludes=org.mockito` shows 5.23.0 for both `mockito-core` and `mockito-junit-jupiter`.
- The surefire `-javaagent` path resolves to `mockito-core-5.23.0.jar` without "file not found" errors.
- All 6 test files using Mockito pass: `ConstraintViolationExceptionMapperTest`, `AccountResourceTest`, `ChildMagicLinkResourceTest`, `AccountServiceImplTest`, `AuthServiceImplTest`, `PublicOriginResolverTest`.
- `./mvnw verify` passes.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml
git commit -m "chore(backend): bump Mockito to 5.23.0"
```

---

## TASK P1-6: Update backend static analysis tools

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update Checkstyle, PMD, and SpotBugs versions in `apps/backend/pom.xml`. These are build-time static analysis tools that run during the `verify` phase. The Checkstyle config (`config/checkstyle.xml`), PMD ruleset (`config/pmd/backend-srp-ruleset.xml`), and SpotBugs exclude filter (`config/spotbugs-exclude.xml`) may need adjustments if new rules are introduced or existing rules are renamed.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<checkstyle.version>10.21.4</checkstyle.version>`, `<pmd.version>7.17.0</pmd.version>`, `<spotbugs.version>4.9.8.3</spotbugs.version>`, `<spotbugs.annotations.version>4.10.2</spotbugs.annotations.version>` in `apps/backend/pom.xml`.
- Modify `apps/backend/config/checkstyle.xml` if Checkstyle 10.26 introduces new required rules or renames existing ones.
- Modify `apps/backend/config/pmd/backend-srp-ruleset.xml` if PMD 7.26 renames rules.
- Modify `apps/backend/config/spotbugs-exclude.xml` if SpotBugs 4.10 changes detector categories.

**Goal:**

Checkstyle 10.26.1, PMD 7.26.0, SpotBugs 4.10.4.0 (plugin) / 4.10.4 (annotations) run successfully during `verify` with no new violations and no rule exclusions added.

### Outcome

The `verify` phase passes with updated static analysis tools. No new Checkstyle, PMD, or SpotBugs violations are introduced. If the tools find pre-existing issues that were previously undetected, fix the code rather than suppressing the rules.

### Architectural decision

Checkstyle 10.21→10.26 is a minor jump within the 10.x line. PMD 7.17→7.26 is a minor jump within the 7.x line. SpotBugs 4.9→4.10 is a minor jump. All three tools maintain backward compatibility for standard rulesets within minor versions. The risk is that new rules may be enabled by default or existing rules may become stricter. The project's `AGENTS.md` explicitly forbids adding Checkstyle, SpotBugs, PMD, or compiler exclusions to make checks pass — any new violations must be fixed in code.

### Required changes

1. Update `<checkstyle.version>` from `10.21.4` to `10.26.1`.
2. Update `<pmd.version>` from `7.17.0` to `7.26.0`.
3. Update `<spotbugs.version>` from `4.9.8.3` to `4.10.4.0`.
4. Update `<spotbugs.annotations.version>` from `4.10.2` to `4.10.4`.
5. Run `./mvnw verify` — if Checkstyle reports new violations, fix the code (do not suppress). If PMD reports new violations, fix the code. If SpotBugs reports new findings, fix the code or update `spotbugs-exclude.xml` only if the finding is a confirmed false positive that was not detected before.
6. If a Checkstyle rule is removed or renamed in 10.26, update `config/checkstyle.xml` to use the new rule name.
7. If a PMD rule is removed or renamed in 7.26, update `config/pmd/backend-srp-ruleset.xml`.

### Out of scope

- JaCoCo version (P1-7).
- Surefire plugin version (stay on 3.5.4).
- Application logic changes beyond fixing static analysis violations.

### Acceptance criteria

- `./mvnw verify` passes.
- No new `<exclude>` entries added to `config/checkstyle.xml`, `config/pmd/backend-srp-ruleset.xml`, or `config/spotbugs-exclude.xml` unless a confirmed false positive.
- No `@SuppressWarnings` annotations added to Java source files.
- Checkstyle, PMD, and SpotBugs reports show zero violations.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml apps/backend/config/
git commit -m "chore(backend): bump Checkstyle, PMD, SpotBugs to latest"
```

---

## TASK P1-7: Update JaCoCo plugin to 0.8.15

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update the JaCoCo Maven plugin version from 0.8.14 to 0.8.15 in `apps/backend/pom.xml`. JaCoCo runs during `verify` to measure test coverage with a minimum 80% line coverage rule.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<jacoco.version>0.8.14</jacoco.version>` in `apps/backend/pom.xml`.

**Goal:**

JaCoCo 0.8.15 generates coverage reports and enforces the 80% line coverage minimum with no regressions.

### Outcome

The `verify` phase passes with JaCoCo 0.8.15. Coverage report is generated and the coverage check passes.

### Architectural decision

JaCoCo 0.8.15 is a patch release. The `argLine` uses `@{argLine}` to preserve the JaCoCo javaagent injection (per repository memory note). The coverage exclusions for Panache repositories and infrastructure persistence packages remain unchanged. JaCoCo patch releases are backward compatible.

### Required changes

1. Update `<jacoco.version>` from `0.8.14` to `0.8.15`.
2. Run `./mvnw verify` — verify the JaCoCo agent attaches correctly and the coverage report is generated.
3. If coverage numbers shift slightly due to JaCoCo 0.8.15's improved bytecode analysis, ensure the 80% minimum still passes. If it drops below 80%, add tests — do not lower the threshold.

### Out of scope

- Coverage threshold changes (must remain at 80% line coverage).
- JaCoCo exclusions (must remain as-is for Panache repositories).

### Acceptance criteria

- `./mvnw verify` passes.
- JaCoCo report is generated at `target/site/jacoco/`.
- Coverage check passes (80% minimum line coverage).
- `@{argLine}` placeholder preserved in surefire/failsafe `argLine`.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml
git commit -m "chore(backend): bump JaCoCo to 0.8.15"
```

---

## TASK P1-8: Update web npm patch and minor safe packages

**Status:** TODO
**Priority:** P1
**Depends on:** -

**Exact scope:**

Update all npm packages in `apps/web/package.json` that have patch or minor version updates available without major version jumps. This is the safe batch — no breaking API changes expected.

Packages to update (current → target):
- `@lucide/svelte`: 1.31.0 → 1.34.0 (minor)
- `@playwright/test`: 1.61.1 → 1.62.1 (minor)
- `@sveltejs/kit`: 2.69.2 → 2.70.3 (minor)
- `@types/node`: 26.1.1 → 26.3.0 (minor)
- `@vitest/coverage-v8`: 4.1.10 → 4.1.11 (patch)
- `eslint`: 10.2.0 → 10.9.1 (minor)
- `@eslint/js`: 10.0.1 → 10.9.1 (minor — implied by eslint update)
- `eslint-plugin-svelte`: 3.20.0 → 3.23.0 (minor)
- `globals`: 17.7.0 → 17.11.0 (minor)
- `marked`: 18.0.6 → 18.0.11 (patch)
- `svelte`: 5.56.4 → 5.56.10 (patch)
- `svelte-check`: 4.7.2 → 4.7.6 (patch)
- `typescript-eslint`: 8.63.0 → 8.68.0 (minor)
- `vite`: 8.1.4 → 8.2.2 (minor)
- `vitest`: 4.1.10 → 4.1.11 (patch)

**Files:**

- Modify `apps/web/package.json`.
- Modify `apps/web/package-lock.json` (via `npm install`).

**Goal:**

All safe-update npm packages are at their latest patch/minor versions. The web app builds, lints, and tests pass with no regressions.

### Outcome

`npm outdated` shows zero patch/minor updates remaining for the packages in scope. Only major version updates (http-proxy-3, TypeScript) remain for P2-1 and P2-2.

### Architectural decision

These are all patch or minor version bumps within their respective major lines. Svelte 5.56.x patches, SvelteKit 2.70.x minor, Vite 8.2.x minor, and ESLint 10.9.x minor are all backward compatible. The `@sveltejs/adapter-node` (5.5.7) and `@sveltejs/adapter-static` (3.0.10) are already at their latest within their ranges — no change needed. TypeScript stays at 6.0.3 (major bump to 7.0 is handled separately in P2-2). `http-proxy-3` stays at 1.23.3 (major bump to 2.0 is handled separately in P2-1).

### Required changes

1. Update all listed package versions in `apps/web/package.json` to their target versions.
2. Run `npm install` in `apps/web/` to regenerate `package-lock.json`.
3. Run `npm run lint` — if ESLint 10.9 or eslint-plugin-svelte 3.23 introduces new lint errors, fix the code (do not use `eslint-disable`).
4. Run `npm run test` — if Vitest 4.1.11 or svelte-check 4.7.6 introduces new type errors or test failures, fix them.
5. Run `npm run build` — if Vite 8.2 or SvelteKit 2.70 introduces build changes, fix any build errors.
6. If `@sveltejs/kit` 2.70 introduces new deprecation warnings in `svelte.config.js` or `vite.config.ts`, address them.

### Out of scope

- `http-proxy-3` major version (P2-1).
- `typescript` major version (P2-2).
- `@sveltejs/adapter-node` and `@sveltejs/adapter-static` (already at latest within range).
- E2E tests (validated in P1-11).

### Acceptance criteria

- `npm outdated` shows no updates available for the packages listed above (except http-proxy-3 2.0.0 and typescript 7.0.2 which are deferred).
- `npm run lint` passes with no new errors.
- `npm run test` passes with no new failures.
- `npm run build` passes with no errors.
- No `eslint-disable` directives added.
- No `@ts-ignore` or `@ts-expect-error` added.

### Targeted validation

```bash
cd apps/web && npm install && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/package.json apps/web/package-lock.json
git commit -m "chore(web): bump npm patch and minor dependencies"
```

---

## TASK P1-9: Update web npm minor feature packages

**Status:** TODO
**Priority:** P1
**Depends on:** P1-8

**Exact scope:**

This task is merged into P1-8 if P1-8 succeeds without issues. It exists as a separate checkpoint only if P1-8 reveals that one of the minor updates (e.g., SvelteKit 2.70, Vite 8.2, ESLint 10.9) requires code changes that should be isolated. If P1-8 passes cleanly, this task is marked DONE with no changes and the commit is a no-op.

**Files:**

- Potentially modify `apps/web/eslint.config.js` if ESLint 10.9 changes flat config behaviour.
- Potentially modify `apps/web/vite.config.ts` if Vite 8.2 changes config structure.
- Potentially modify `apps/web/svelte.config.js` if SvelteKit 2.70 changes adapter config.

**Goal:**

Confirm that all minor version updates from P1-8 are fully integrated with no deferred fixes.

### Outcome

All web tooling (ESLint, Vite, SvelteKit, Playwright) works at its updated version with no configuration drift.

### Architectural decision

This is a verification checkpoint. If P1-8 passes all gates cleanly, no action is needed. If P1-8 required skipping a package due to a minor incompatibility, this task addresses the specific configuration or code change needed to bring that package up to date.

### Required changes

1. Review the output of P1-8's validation commands.
2. If all passed: mark DONE, no commit needed.
3. If any package was held back: apply the specific fix (config change or code change) and rerun validation.

### Out of scope

- Major version updates (P2-1, P2-2).

### Acceptance criteria

- `npm run lint && npm run test && npm run build` all pass.
- `npm outdated` shows only http-proxy-3 and typescript as having available updates (deferred to P2-1 and P2-2).

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/
git commit -m "chore(web): finalize minor dependency integration"
```

---

## TASK P2-1: Update http-proxy-3 to 2.0.0

**Status:** TODO
**Priority:** P2
**Depends on:** P1-8

**Exact scope:**

Update `http-proxy-3` from 1.23.3 to 2.0.0 in `apps/web/package.json`. This is a major version bump with potential breaking API changes. The library is used in the web app's proxy/preview server scripts.

**Files:**

- Modify `apps/web/package.json`.
- Modify `apps/web/package-lock.json` (via `npm install`).
- Search anchor: `http-proxy-3` in `apps/web/scripts/preview.mjs` and `apps/web/scripts/build.mjs`.
- Potentially modify `apps/web/scripts/preview.mjs` if the http-proxy-3 2.0 API changed.

**Goal:**

The web preview/proxy server uses http-proxy-3 2.0.0 with no proxy behaviour regressions.

### Outcome

`npm run preview` and `npm start` work correctly with http-proxy-3 2.0.0. The proxy correctly forwards requests to the backend and serves the SvelteKit handler.

### Architectural decision

http-proxy-3 2.0.0 is a major version bump. Before implementing, check the [http-proxy-3 changelog](https://github.com/sagemathinc/http-proxy-3) for breaking changes. The library is used in `scripts/preview.mjs` (the production preview server) and potentially in `scripts/build.mjs`. The proxy server is critical for the Docker deployment (`apps/web/Dockerfile` CMD is `node scripts/preview.mjs`). If the 2.0 API is incompatible and the migration is too complex, document the decision to stay on 1.23.3 and mark this task as BLOCKED with a clear reason.

### Required changes

1. Check the http-proxy-3 2.0.0 changelog/release notes for breaking changes.
2. Update `http-proxy-3` from `^1.23.3` to `^2.0.0` in `apps/web/package.json`.
3. Run `npm install` to update `package-lock.json`.
4. Inspect `apps/web/scripts/preview.mjs` for http-proxy-3 usage — update the API calls if the 2.0 interface changed (e.g., `createProxyServer`, `web`, `ws` method signatures).
5. Run `npm run build && npm run preview` and verify the preview server starts and serves pages.
6. If the 2.0 API is fundamentally incompatible and migration would require significant rewrite: revert to 1.23.3, mark this task BLOCKED, and document the reason.

### Out of scope

- TypeScript major version (P2-2).
- Other npm packages.

### Acceptance criteria

- `npm run build` passes.
- `npm run preview` starts the server and serves the app at `http://localhost:4174`.
- Proxy forwarding to the backend works (verify with a manual request or E2E test).
- If BLOCKED: `http-proxy-3` remains at 1.23.3 and the task status is `BLOCKED` with a documented reason.

### Targeted validation

```bash
cd apps/web && npm install && npm run build && npm run preview &
sleep 5 && curl -s http://localhost:4174/healthz && kill %1
```

### Commit

```bash
git add apps/web/package.json apps/web/package-lock.json apps/web/scripts/preview.mjs
git commit -m "chore(web): bump http-proxy-3 to 2.0.0"
```

---

## TASK P2-2: Update TypeScript to 7.0.2

**Status:** TODO
**Priority:** P2
**Depends on:** P1-9

**Exact scope:**

Update TypeScript from 6.0.3 to 7.0.2 in `apps/web/package.json`. This is a major version bump that may introduce stricter type checking, new compiler options, or removed deprecated features.

**Files:**

- Modify `apps/web/package.json`.
- Modify `apps/web/package-lock.json` (via `npm install`).
- Modify `apps/web/tsconfig.json` if TypeScript 7.0 requires config changes.
- Potentially modify any `.ts` or `.svelte` files with type errors introduced by TypeScript 7.0.

**Goal:**

TypeScript 7.0.2 is used for type checking and build with no type errors. `svelte-check` and `vite build` pass with the new compiler.

### Outcome

`npm run lint` (which runs `svelte-check --tsconfig ./tsconfig.json`) and `npm run build` pass with TypeScript 7.0.2. No `@ts-ignore` or `@ts-expect-error` directives added.

### Architectural decision

TypeScript 7.0 is a major version bump. Check the [TypeScript 7.0 release notes](https://www.typescriptlang.org/docs/handbook/release-notes/) for breaking changes. Common breaking changes in major TS versions include: stricter type narrowing, removed deprecated compiler options, changed lib defaults. The project uses `tsconfig.json` with SvelteKit's generated config. If TypeScript 7.0 introduces too many new type errors that require extensive code changes, document the decision to stay on 6.0.3 and mark this task as BLOCKED.

### Required changes

1. Check the TypeScript 7.0 release notes and migration guide.
2. Update `typescript` from `^6.0.3` to `^7.0.2` in `apps/web/package.json`.
3. Run `npm install` to update `package-lock.json`.
4. Run `npm run lint` — if `svelte-check` reports new type errors, fix each one in the source code. Do not use `@ts-ignore` or `@ts-expect-error`.
5. Run `npm run test` — if Vitest's TypeScript transformation changes, fix test files.
6. Run `npm run build` — if Vite's TypeScript handling changes, fix build errors.
7. If `tsconfig.json` uses deprecated compiler options removed in TS 7.0, update the config.
8. If the number of new type errors is excessive (e.g., >20 files affected): revert to 6.0.3, mark BLOCKED, document the reason and estimated effort.

### Out of scope

- http-proxy-3 (P2-1).
- Other npm packages.

### Acceptance criteria

- `npm run lint` passes (includes `svelte-check`).
- `npm run test` passes.
- `npm run build` passes.
- No `@ts-ignore` or `@ts-expect-error` directives added.
- If BLOCKED: TypeScript remains at 6.0.3 and the task status is `BLOCKED` with a documented reason.

### Targeted validation

```bash
cd apps/web && npm install && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/package.json apps/web/package-lock.json apps/web/tsconfig.json
git commit -m "chore(web): bump TypeScript to 7.0.2"
```

---

## TASK P2-3: Update Docker base images

**Status:** TODO
**Priority:** P2
**Depends on:** P0-1, P1-8

**Exact scope:**

Update Docker base image tags and the New Relic agent version in all Dockerfiles. Check for newer base image tags for: `eclipse-temurin:25-jdk-jammy`, `eclipse-temurin:25-jre-jammy`, `node:24-alpine`, `postgres:18-alpine`, `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`, `quay.io/quarkus/ubi9-quarkus-micro-image:2.0`, and `NEW_RELIC_AGENT_VERSION`.

**Files:**

- Modify `apps/backend/Dockerfile.jvm` — `eclipse-temurin:25-jdk-jammy`, `eclipse-temurin:25-jre-jammy`, `NEW_RELIC_AGENT_VERSION=9.3.0`.
- Modify `apps/backend/Dockerfile` — `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`, `quay.io/quarkus/ubi9-quarkus-micro-image:2.0`.
- Modify `apps/web/Dockerfile` — `node:24-alpine`.
- Modify `docker-compose.yml` — `postgres:18-alpine`.
- Modify `docker-compose.native.yml` — check for any base image references.

**Goal:**

All Docker base images use the latest available tags for their respective major versions. The New Relic Java agent is at its latest stable version.

### Outcome

`docker compose config` validates successfully. `docker compose build` succeeds for all services. The built images start and pass health checks.

### Architectural decision

Base image tags like `eclipse-temurin:25-jdk-jammy` and `node:24-alpine` are rolling tags that receive security patches. Check Docker Hub for the current digest and whether a newer point release exists (e.g., `node:24-alpine` vs `node:24.x-alpine`). The Quarkus Mandrel builder image tag `jdk-25` should match the latest Quarkus 3.39.1 compatible image — check quay.io for available tags. The New Relic agent version 9.3.0 should be updated to the latest stable release from the [New Relic Java agent releases](https://github.com/newrelic/newrelic-java-agent/releases). PostgreSQL 18-alpine is already very recent — verify it's the latest 18.x tag.

### Required changes

1. Check Docker Hub for latest `eclipse-temurin:25` tags — if a newer variant exists (e.g., `25-jdk-jammy` vs `25-jdk-noble`), evaluate whether to switch. Prefer staying on `jammy` unless a newer base offers security improvements.
2. Check quay.io for latest `ubi9-quarkus-mandrel-builder-image` tags compatible with Quarkus 3.39.1 and Java 25.
3. Check quay.io for latest `ubi9-quarkus-micro-image` tags — if 2.0 is latest, no change. If a newer tag exists, update.
4. Check Docker Hub for latest `node:24-alpine` — update to latest 24.x alpine tag if a specific version is available.
5. Check Docker Hub for latest `postgres:18-alpine` — update if a newer 18.x point release exists.
6. Check the New Relic Java agent [releases page](https://github.com/newrelic/newrelic-java-agent/releases) for the latest stable version — update `NEW_RELIC_AGENT_VERSION` in `apps/backend/Dockerfile.jvm`.
7. Run `docker compose config` to validate compose files.
8. Run `docker compose --profile db up -d --build` to verify the full stack builds and starts.
9. Verify health checks pass for all services.

### Out of scope

- Application code changes.
- Docker Compose network or volume configuration.
- `.env` or `.env.example` changes (unless new env vars are introduced by updated images — unlikely for base image bumps).

### Acceptance criteria

- `docker compose config` passes for both `docker-compose.yml` and `docker-compose.native.yml`.
- `docker compose --profile db up -d --build` succeeds.
- All service health checks pass within their `start_period`.
- `docker compose down` cleans up successfully.
- New Relic agent version is the latest stable (not a beta/snapshot).

### Targeted validation

```bash
docker compose config && docker compose --profile db up -d --build && sleep 30 && docker compose ps && docker compose down
```

### Commit

```bash
git add apps/backend/Dockerfile.jvm apps/backend/Dockerfile apps/web/Dockerfile docker-compose.yml docker-compose.native.yml
git commit -m "chore(docker): update base images and New Relic agent"
```

---

## TASK P2-4: Verify Telegram SDK load strategy

**Status:** TODO
**Priority:** P2
**Depends on:** -

**Exact scope:**

Verify that the Telegram Mini App SDK (`telegram-web-app.js`) loaded from `https://telegram.org/js/telegram-web-app.js` in `apps/web/src/app.html` is the latest version served by Telegram. The SDK is unversioned (always latest from Telegram's CDN). Verify the CSP in `apps/web/svelte.config.js` allows the SDK domain. Verify that `apps/web/src/lib/services/telegram.ts` type definitions are compatible with the current SDK API.

**Files:**

- Verify `apps/web/src/app.html` — search anchor: `telegram-web-app.js`.
- Verify `apps/web/svelte.config.js` — search anchor: `script-src` CSP directive (must include `https://telegram.org`).
- Verify `apps/web/src/lib/services/telegram.ts` — search anchor: `TelegramWebApp` type.

**Goal:**

Confirm the Telegram Mini App SDK is loaded correctly, the CSP allows it, and the TypeScript type definitions in `telegram.ts` match the current SDK API surface.

### Outcome

The Telegram Mini App SDK loads without CSP violations. The `TelegramWebApp` type in `telegram.ts` covers all SDK methods used by the application (`initData`, `ready`, `expand`, `initDataUnsafe.start_param`).

### Architectural decision

The Telegram SDK is served from Telegram's CDN without a version pin — this is the standard integration pattern recommended by Telegram. There is no npm package to update. The `TelegramWebApp` type in `telegram.ts` is a minimal hand-written type that covers only the fields the app uses. If the current SDK adds new methods the app should use (e.g., `hapticFeedback`, `setHeaderColor`, `setBackgroundColor`), those are out of scope for a dependency update — they are feature additions, not dependency updates.

### Required changes

1. Open `https://telegram.org/js/telegram-web-app.js` in a browser and verify it serves a valid JavaScript file (HTTP 200, content-type `application/javascript`).
2. Verify `apps/web/svelte.config.js` CSP `script-src` directive includes `https://telegram.org` — it does (confirmed in inspection).
3. Review `apps/web/src/lib/services/telegram.ts` — the `TelegramWebApp` type defines `initData`, `initDataUnsafe`, `ready`, `expand`. Verify these fields still exist in the current SDK by checking the [Telegram Mini App docs](https://core.telegram.org/bots/webapps).
4. If the SDK has renamed or removed any of these fields: update the type and all usages. If they are unchanged: no code change needed.
5. Run `npm run build` and verify no CSP-related build warnings.

### Out of scope

- Adding new Telegram SDK features (haptics, theme controls, etc.) — those are product features, not dependency updates.
- Self-hosting the Telegram SDK — keep the CDN load pattern.
- Backend Telegram integration — it uses standard Quarkus REST, no separate dependency.

### Acceptance criteria

- `https://telegram.org/js/telegram-web-app.js` returns HTTP 200 with valid JavaScript.
- CSP `script-src` includes `https://telegram.org` in `svelte.config.js`.
- `TelegramWebApp` type fields (`initData`, `initDataUnsafe`, `ready`, `expand`) match the current Telegram SDK API.
- `npm run build` passes with no new warnings.
- No code changes required if the SDK API is unchanged (mark DONE with no commit if verification passes without changes).

### Targeted validation

```bash
cd apps/web && npm run build && curl -sI https://telegram.org/js/telegram-web-app.js | head -3
```

### Commit

```bash
git add apps/web/src/lib/services/telegram.ts apps/web/src/app.html apps/web/svelte.config.js
git commit -m "chore(web): verify Telegram Mini App SDK compatibility"
```

---

## TASK P1-10: Backend full verification gate

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1, P1-1, P1-2, P1-3, P1-4, P1-5, P1-6, P1-7

**Exact scope:**

Run the complete backend verification suite after all backend dependency updates are applied. This is the final gate that confirms the entire backend stack works together with all updated dependencies.

**Files:**

- No file changes expected — this is a verification task.
- If the full `verify` reveals issues that individual task validation missed, fix them here.

**Goal:**

The backend passes `./mvnw verify` with all dependencies updated and no regressions.

### Outcome

`./mvnw verify` passes cleanly: compilation, unit tests, JaCoCo coverage check, Checkstyle, PMD, and SpotBugs all succeed.

### Architectural decision

The backend `verify` goal is the single source of truth for backend quality. It runs Checkstyle (validate phase), unit tests (test phase), JaCoCo coverage check (verify phase), PMD (verify phase), and SpotBugs (verify phase). Per `AGENTS.md`, this gate is always required. Per repository memory, Quarkus tests may fail in sandbox with FileSystemException — rerun unsandboxed if needed.

### Required changes

1. Run `./mvnw verify` unsandboxed (per repository memory note about Quarkus FileSystemException in sandbox).
2. If any phase fails:
   - Checkstyle: fix the code violation.
   - PMD: fix the code violation.
   - SpotBugs: fix the finding or confirm false positive.
   - Tests: fix the test or the code under test.
   - JaCoCo: add tests to reach 80% coverage if it dropped.
3. Do not skip, suppress, or exclude any quality gate.
4. Re-run `./mvnw verify` until it passes.

### Out of scope

- Web verification (P1-11).
- Docker verification (P0-2).

### Acceptance criteria

- `./mvnw verify` passes with exit code 0.
- Checkstyle report has zero violations.
- PMD report has zero violations.
- SpotBugs report has zero bugs.
- JaCoCo coverage check passes (≥80% line coverage).
- All unit tests pass.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/
git commit -m "test(backend): verify full build after dependency updates"
```

---

## TASK P1-11: Web full verification gate

**Status:** TODO
**Priority:** P1
**Depends on:** P1-8, P1-9, P2-1, P2-2, P2-4

**Exact scope:**

Run the complete web verification suite after all web dependency updates are applied. This includes lint, unit tests, production build, and E2E tests if UI behaviour could be affected by dependency changes.

**Files:**

- No file changes expected — this is a verification task.
- If the full validation reveals issues that individual task validation missed, fix them here.

**Goal:**

The web app passes `npm run lint`, `npm run test`, `npm run build`, and `npm run test:e2e` with all dependencies updated.

### Outcome

All web quality gates pass: ESLint, svelte-check, Vitest unit tests, Vite production build, and Playwright E2E tests.

### Architectural decision

Per `AGENTS.md`, `npm run lint`, `npm run test`, and `npm run build` are always required. E2E tests are required when UI changes occur — dependency updates (especially Svelte, SvelteKit, Vite patches) can subtly affect rendering, so E2E validation is warranted. The E2E tests run against a preview server with a mock backend (`tests/e2e/e2eBackend.mjs`).

### Required changes

1. Run `npm run lint` — fix any new lint errors.
2. Run `npm run test` — fix any new test failures.
3. Run `npm run build` — fix any new build errors.
4. Run `npm run test:e2e` — fix any E2E test failures. If a test fails due to a rendering or timing change introduced by a dependency update, fix the test or the component.
5. Do not skip tests, use `.skip`, or disable E2E test files.

### Out of scope

- Backend verification (P1-10).
- Docker verification (P0-2).

### Acceptance criteria

- `npm run lint` passes with zero errors.
- `npm run test` passes with zero failures.
- `npm run build` passes with zero errors.
- `npm run test:e2e` passes with zero failures (or at minimum `npm run test:e2e:critical` passes).
- No `eslint-disable`, `.skip`, or test exclusions added.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e:critical
```

### Commit

```bash
git add apps/web/
git commit -m "test(web): verify full build after dependency updates"
```

---

## TASK P0-2: Full-stack Docker Compose verification

**Status:** TODO
**Priority:** P0
**Depends on:** P1-10, P1-11, P2-3

**Exact scope:**

Run the full-stack Docker Compose build and startup to verify that all updated dependencies work together in the containerized environment. This is the final integration gate.

**Files:**

- No file changes expected — this is a verification task.

**Goal:**

The full stack (web + backend + PostgreSQL) builds and runs in Docker Compose with all updated dependencies. All services pass health checks.

### Outcome

`docker compose --profile db up -d --build` succeeds. All three services (web, backend, db) reach healthy status. The web app can reach the backend over the `edge` network.

### Architectural decision

Docker Compose is the final integration environment. It validates that the updated Maven dependencies package correctly into the Quarkus uber-jar, the updated npm dependencies build correctly into the SvelteKit production bundle, and the updated base images are compatible. Per `AGENTS.md`, `docker compose config` must pass when compose changes are made — run it first to catch env drift. Both JVM and native compose files should be validated.

### Required changes

1. Run `docker compose config` for `docker-compose.yml` — fix any env drift.
2. Run `docker compose -f docker-compose.native.yml config` — fix any env drift.
3. Run `docker compose --profile db up -d --build` — verify all images build.
4. Wait for health checks to pass (`docker compose ps` shows all healthy).
5. Verify the web service can reach the backend: `curl -s http://localhost:${WEB_PORT}/healthz` and `curl -s http://localhost:${WEB_PORT}/api/openapi.yaml` (proxied to backend).
6. Run `docker compose down` to clean up.
7. If the native build is part of the verification scope: run `docker compose -f docker-compose.native.yml --profile db up -d --build` and verify. If native build fails due to a dependency incompatibility with GraalVM/native-image, document the issue and mark the native verification as a separate follow-up.

### Out of scope

- Application behaviour testing beyond health checks and basic API availability.
- Performance benchmarking.
- Production deployment.

### Acceptance criteria

- `docker compose config` passes for both compose files.
- `docker compose --profile db up -d --build` succeeds (JVM mode).
- All services reach `healthy` status within their `start_period`.
- Web healthcheck passes: `wget -q --spider http://127.0.0.1:3000/healthz`.
- Backend healthcheck passes: `wget -q --spider http://127.0.0.1:${BACKEND_INTERNAL_PORT}/q/health/ready`.
- Database healthcheck passes: `pg_isready -U ${DB_USER}`.
- `docker compose down` succeeds with no orphaned containers or volumes.

### Targeted validation

```bash
docker compose config && docker compose --profile db up -d --build && sleep 30 && docker compose ps && curl -s http://localhost:3000/healthz && docker compose down
```

### Commit

```bash
git add .
git commit -m "test(docker): verify full-stack compose after dependency updates"
```