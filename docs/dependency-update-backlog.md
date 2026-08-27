# Dependency Update — Implementation Backlog

## Goal

Update all project dependencies (web, Telegram integration, Java, Quarkus, Docker base images, static analysis tools, Maven plugins) to their latest available versions — including major version jumps — while keeping the full build, test, lint, and Docker pipeline green. No behavioural changes to the product — only dependency upgrades with necessary compatibility fixes.

## Architectural decisions

- **Source of truth for versions:** `npm outdated`, `mvn versions:display-dependency-updates`, `display-property-updates`, and `display-plugin-updates` with `-DallowMajorUpdates=true`, run against the current repository on 2026-08-26. Target versions are the latest stable releases identified by those commands. Pre-release versions (alpha, beta, milestone) are skipped — only GA/stable releases are targeted.
- **Major version jumps included:**
  - Checkstyle: 10.21.4 → **14.0.0** (four major version jumps — 10→11→12→13→14)
  - http-proxy-3: 1.23.3 → **2.0.0**
  - TypeScript: 6.0.3 → **7.0.2**
  - maven-compiler-plugin: 3.13.0 → **3.15.0** (latest stable; 4.0.0-beta exists but is beta)
- **Pre-release versions skipped (not stable):**
  - Netty 5.0.0.Alpha2 — latest stable is 4.2.17.Final
  - AssertJ 4.0.0-M1 — latest stable is 3.27.7
  - MapStruct 1.7.0.Beta2 — latest stable is 1.6.3
  - Surefire/Failsafe 3.6.0-M1 — latest stable is 3.5.4
  - maven-compiler-plugin 4.0.0-beta-4 — latest stable is 3.15.0
- **Layer boundaries:** Backend upgrades are scoped to `apps/backend/pom.xml`, `apps/backend/config/`, and Dockerfiles. Web upgrades are scoped to `apps/web/package.json` and `apps/web/package-lock.json`. No application code changes unless a compatibility fix is required by the upgraded library.
- **Compatibility strategy:** Minor and patch updates are applied in grouped batches per ecosystem. Major version bumps (Checkstyle 14, http-proxy-3 2, TypeScript 7, maven-compiler-plugin 3.15) are isolated into separate tasks because they carry breaking API/config changes and require individual validation.
- **Telegram SDK:** The Telegram Mini App SDK (`telegram-web-app.js`) is loaded from `https://telegram.org/js/telegram-web-app.js` in `apps/web/src/app.html` with no version pin — Telegram serves the latest compatible version. The backend Telegram integration uses standard Quarkus REST/JAX-RS (no third-party Telegram bot library). Both are upgraded transitively via the Quarkus and web stack updates — no separate Telegram dependency exists.
- **Rejected duplicate approaches:** Do not introduce `npm-check-updates`, `renovate`, or `dependabot` config. Do not split the Quarkus BOM from its managed dependencies.
- **Java 25 constraint:** `maven.compiler.release` must remain `25`. The `--enable-preview` flag must remain in surefire/failsafe `argLine`. No Java version changes.
- **Netty 4.1 → 4.2:** Netty 5.0.0.Alpha2 exists but is alpha. Latest stable is 4.2.17.Final. The explicit override may be removable if Quarkus 3.39.1 BOM already manages 4.2.x.
- **BouncyCastle migration:** `bcprov-jdk15on:1.70` is deprecated. Migrate to `bcprov-jdk18on` (latest stable 1.81+). The `BouncyCastleProvider` class name is identical across jdk15on/jdk18on.
- **Checkstyle 14.0.0:** This is the highest-risk update. Checkstyle 14 requires Java 21+ (compatible with project's Java 25). The `config/checkstyle.xml` will likely need updates — rules may have been renamed, removed, or added across 10→11→12→13→14. The Checkstyle 14.0.0 POM itself uses `maven-checkstyle-plugin 3.6.0` and `checkstyle.plugin.version 3.6.0`, confirming plugin compatibility.
- **Svelte 4→5 legacy syntax migration:** The project runs Svelte 5 (`5.56.4`) but every `.svelte` component uses Svelte 4 syntax (`export let`, `$:` reactives, `on:click`, `<slot>`, `createEventDispatcher`). Svelte 5 supports these in legacy mode, but they are deprecated and will be removed in Svelte 6. This is the single largest migration effort in the backlog: 62+ files for `export let`, 34+ files for `$:` reactives, 51+ files for `on:event`, 8 files for `<slot>`, 4 files for `createEventDispatcher`.
- **Unused jsoup dependency:** `jsoup` (1.18.3→1.23.2) is declared in `apps/backend/pom.xml` but not imported anywhere in Java source. Remove it as part of the dependency cleanup.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P0-1 | P0 | - | Quarkus BOM is the foundation for all backend dependencies |
| 2 | P1-1 | P1 | P0-1 | Netty BOM must align with Quarkus BOM expectations |
| 3 | P1-2 | P1 | P0-1 | Jackson is a shared serialization contract |
| 4 | P1-3 | P1 | P0-1 | Third-party libraries (jsoup removal, google-api-client, jose4j, argon2, lombok) |
| 5 | P1-4 | P1 | P0-1 | BouncyCastle + web-push security update |
| 6 | P1-5 | P1 | P0-1 | Test dependencies (mockito, assertj, rest-assured) |
| 7 | P1-6 | P1 | P0-1 | Static analysis tools (PMD, SpotBugs — minor updates) |
| 8 | P0-2 | P0 | P0-1 | Checkstyle 10→14 major jump — highest risk, isolated |
| 9 | P1-7 | P1 | P0-1 | Build plugins (jacoco, maven-compiler-plugin) |
| 10 | P1-8 | P1 | - | Web npm patch and minor updates (safe batch) |
| 11 | P2-1 | P2 | P1-8 | http-proxy-3 major version (1→2), breaking changes |
| 12 | P2-2 | P2 | P1-8 | TypeScript major version (6→7), breaking changes |
| 13 | P2-3 | P2 | P0-1, P1-8 | Docker base image updates |
| 14 | P2-4 | P2 | - | Telegram SDK load strategy verification |
| 15 | P1-11 | P1 | P1-8 | Svelte 5 migration: props (`export let`→`$props()`) |
| 16 | P1-12 | P1 | P1-11 | Svelte 5 migration: reactivity (`$:`→`$derived`/`$effect`) |
| 17 | P1-13 | P1 | P1-11 | Svelte 5 migration: events (`on:click`→`onclick`) |
| 18 | P1-14 | P1 | P1-11 | Svelte 5 migration: snippets (`<slot>`→`{@render}`) |
| 19 | P1-15 | P1 | P1-11 | Svelte 5 migration: `createEventDispatcher`→callback props |
| 20 | P1-9 | P1 | All backend tasks | Backend full verification gate |
| 21 | P1-10 | P1 | All web tasks, P1-11–P1-15 | Web full verification gate |
| 22 | P0-3 | P0 | P1-9, P1-10, P2-3 | Full-stack Docker Compose verification |

---

## TASK P0-1: Update Quarkus platform BOM to 3.39.1

**Status:** DONE
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
- Static analysis tool versions (handled in P1-6 and P0-2).
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

**Status:** DONE
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update the Netty BOM override in `apps/backend/pom.xml` from 4.1.135.Final to 4.2.17.Final (latest stable — Netty 5.0.0.Alpha2 is alpha and skipped). The `netty.version` property controls the `netty-bom` import and all explicit `io.netty:*` entries in `dependencyManagement`.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<netty.version>4.1.135.Final</netty.version>` in `apps/backend/pom.xml`.

**Goal:**

All Netty artifacts resolve to 4.2.17.Final with no runtime regressions in HTTP, WebSocket, or DNS resolution paths.

### Outcome

The backend uses Netty 4.2.17.Final consistently across all modules. `dependency:tree` shows no Netty version conflicts.

### Architectural decision

Netty 4.2.x is a new minor series within the 4.x line. Netty 5.0.0.Alpha2 exists but is an alpha release — not suitable for production. The Quarkus 3.39.1 BOM (from P0-1) may already manage Netty at 4.2.x — check the resolved version after P0-1. If Quarkus already provides 4.2.17.Final, the explicit `netty-bom` override and all individual `io.netty:*` entries in `dependencyManagement` can be removed entirely (simplification). If Quarkus provides an older 4.2.x, keep the override and update to 4.2.17.Final.

### Required changes

1. Run `./mvnw dependency:tree -Dincludes=io.netty` after P0-1 to see what Quarkus 3.39.1 resolves Netty to.
2. If Quarkus already resolves 4.2.17.Final: remove the `netty-bom` import and all individual `io.netty:*` entries from `dependencyManagement` — let Quarkus BOM manage them.
3. If Quarkus resolves an older 4.2.x or 4.1.x: update `<netty.version>` to `4.2.17.Final` and keep the override.
4. Verify no Netty API breakages in application code (the codebase does not directly import Netty — it's transitive via Quarkus/Vert.x).

### Out of scope

- Jackson databind version (P1-2).
- Netty 5.x (alpha — not targeted).

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

**Status:** DONE
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

Jackson 2.22.x is a minor version bump. The explicit override exists because Quarkus may manage a different Jackson version. After P0-1, check if Quarkus 3.39.1 already provides Jackson 2.22.x — if so, the override can be removed. Otherwise, update the override to 2.22.2.

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

**Status:** DONE
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update standalone third-party dependencies in `apps/backend/pom.xml` that are not managed by the Quarkus BOM: jsoup, google-api-client, rest-assured, assertj-core, jose4j, argon2-jvm, and Lombok.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchors: `jsoup` (version `1.18.3`), `google-api-client` (version `2.8.1`), `assertj-core` (version `3.27.3`), `jose4j` (version `0.9.6`), `argon2-jvm` (version `2.12`), `lombok` (version `1.18.46`).

**Goal:**

All standalone third-party libraries are at their latest stable versions with no API regressions.

### Outcome

jsoup 1.23.2, google-api-client 2.9.0, rest-assured 6.0.1, assertj-core 3.27.7, jose4j latest stable, argon2-jvm latest stable, Lombok latest stable. All existing tests pass.

### Architectural decision

These libraries have independent version lines. jsoup 1.18→1.23 is a minor jump but jsoup maintains strong backward compatibility. google-api-client 2.8→2.9 is a minor bump. assertj 3.27.3→3.27.7 is a patch (4.0.0-M1 is milestone — skipped). rest-assured 6.0.0→6.0.1 is a patch. jose4j, argon2-jvm, and Lombok should be checked for latest stable versions on Maven Central.

### Required changes

1. Update jsoup from `1.18.3` to `1.23.2` in the `<dependency>` block (inline version).
2. Update google-api-client from `2.8.1` to `2.9.0` in the `<dependency>` block (inline version).
3. Update assertj-core from `3.27.3` to `3.27.7` in the `<dependency>` block (inline version).
4. For rest-assured: after P0-1, check if Quarkus 3.39.1 BOM already manages 6.0.1. If yes, no change needed. If it still shows 6.0.0, add an explicit version override `6.0.1` in `dependencyManagement`.
5. Check Maven Central for latest stable `org.bitbucket.b_c:jose4j` (currently 0.9.6) — update if a newer stable version exists.
6. Check Maven Central for latest stable `de.mkammerer:argon2-jvm` (currently 2.12) — update if a newer stable version exists.
7. Check Maven Central for latest stable `org.projectlombok:lombok` (currently 1.18.46) — update if a newer stable version exists.
8. Run full test suite — jsoup is used for HTML sanitization, google-api-client for Google OAuth (`GoogleIdentityVerifier`), rest-assured for API tests, assertj for test assertions, jose4j for JWT, argon2 for password hashing, Lombok for code generation.
9. If jsoup 1.23 introduces sanitization behaviour changes, verify that HTML sanitization tests still pass.
10. If Lombok update requires a different annotation processor path, update the `maven-compiler-plugin` `annotationProcessorPaths` section.

### Out of scope

- BouncyCastle / web-push (P1-4).
- Mockito (P1-5).
- Static analysis tools (P1-6, P0-2).
- MapStruct (stay on 1.6.3 — 1.7.0.Beta2 is beta).

### Acceptance criteria

- jsoup resolves to 1.23.2 in `dependency:tree`.
- google-api-client resolves to 2.9.0.
- assertj-core resolves to 3.27.7.
- rest-assured resolves to 6.0.1 (either from BOM or explicit override).
- jose4j, argon2-jvm, Lombok at latest stable (or unchanged if no newer stable version exists).
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
git commit -m "chore(backend): bump jsoup, google-api-client, assertj, rest-assured, jose4j, argon2, lombok"
```

---

## TASK P1-4: Update BouncyCastle and web-push library

**Status:** DONE
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update the BouncyCastle provider and `web-push` library in `apps/backend/pom.xml`. The current `bcprov-jdk15on:1.70` is deprecated — migrate to `bcprov-jdk18on` (latest stable 1.81+). The `web-push` library (nl.martijndwars) is used by `WebPushJavaAdapter` for VAPID-signed push notifications.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `bcprov-jdk15on` and `web-push` in `apps/backend/pom.xml`.
- Search anchor: `import org.bouncycastle.jce.provider.BouncyCastleProvider` in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushJavaAdapter.java`.

**Goal:**

Web push encryption uses the latest BouncyCastle provider and web-push library with no changes to push notification delivery behaviour.

### Outcome

`WebPushJavaAdapter` uses the updated BouncyCastle provider and web-push library. VAPID-signed push notifications are constructed and sent correctly. All web push related tests pass.

### Architectural decision

BouncyCastle `bcprov-jdk15on` is the legacy artifact. The active artifact is `bcprov-jdk18on` (latest 1.81+). The `web-push` library 5.1.2 depends on `bcprov-jdk15on` transitively. Check Maven Central for the latest `web-push` version:

- If latest `web-push` already depends on `bcprov-jdk18on`: update `web-push` to latest, replace `bcprov-jdk15on` with `bcprov-jdk18on`.
- If `web-push` still uses `bcprov-jdk15on` at its latest version: replace `bcprov-jdk15on` with `bcprov-jdk18on` explicitly anyway (the `BouncyCastleProvider` class name and `Security.addProvider()` API are identical across jdk15on/jdk18on), and update `web-push` to latest patch.

### Required changes

1. Check Maven Central for latest `nl.martijndwars:web-push` version and its BouncyCastle dependency.
2. Check Maven Central for latest `org.bouncycastle:bcprov-jdk18on` version.
3. Update the `web-push` `<dependency>` to the latest stable version.
4. Replace `bcprov-jdk15on` with `bcprov-jdk18on` and set version to latest stable (1.81+).
5. Run `./mvnw dependency:tree -Dincludes=org.bouncycastle` to verify no duplicate BouncyCastle artifacts on the classpath.
6. Run web push tests — if none exist for `WebPushJavaAdapter` directly, run the full suite which includes `PushResource` and related integration tests.
7. Verify `WebPushJavaAdapter.java` still compiles — the `BouncyCastleProvider`, `Notification`, and `PushService` APIs should be unchanged.

### Out of scope

- jose4j (updated in P1-3).
- argon2-jvm (updated in P1-3).

### Acceptance criteria

- No `bcprov-jdk15on` on the classpath (verify via `dependency:tree`).
- `bcprov-jdk18on` at latest stable version.
- No duplicate BouncyCastle versions in `dependency:tree`.
- `WebPushJavaAdapter` compiles and the BouncyCastle provider is registered at startup.
- `./mvnw verify` passes.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw dependency:tree -Dincludes=org.bouncycastle && ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushJavaAdapter.java
git commit -m "chore(backend): migrate BouncyCastle to jdk18on and update web-push"
```

---

## TASK P1-5: Update backend test dependencies

**Status:** DONE
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

All Mockito-based tests pass with Mockito 5.23.0. The surefire/failsafe `-javaagent` path resolves correctly to the 5.23.0 jar.

### Architectural decision

Mockito 5.23.0 is a minor version bump within the 5.x line. The `-javaagent` path in surefire/failsafe `argLine` uses `${mockito.version}` so it automatically resolves to the new version.

### Required changes

1. Update `<mockito.version>5.21.0</mockito.version>` to `5.23.0`.
2. Run the full test suite — verify all Mockito-based unit tests pass.
3. If Mockito 5.23 introduces stricter mock behaviour, fix the test to align — do not use `@SuppressWarnings` or lenient configurations to suppress legitimate test issues.

### Out of scope

- AssertJ (updated in P1-3).
- Surefire plugin version (stay on 3.5.4 — 3.6.0-M1 is milestone).
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

## TASK P1-6: Update PMD and SpotBugs

**Status:** DONE
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update PMD and SpotBugs versions in `apps/backend/pom.xml`. These are build-time static analysis tools that run during the `verify` phase. Checkstyle is handled separately in P0-2 due to its major version jump.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<pmd.version>7.17.0</pmd.version>`, `<spotbugs.version>4.9.8.3</spotbugs.version>`, `<spotbugs.annotations.version>4.10.2</spotbugs.annotations.version>` in `apps/backend/pom.xml`.
- Modify `apps/backend/config/pmd/backend-srp-ruleset.xml` if PMD 7.26 renames rules.
- Modify `apps/backend/config/spotbugs-exclude.xml` if SpotBugs 4.10 changes detector categories.

**Goal:**

PMD 7.26.0, SpotBugs 4.10.4.0 (plugin) / 4.10.4 (annotations) run successfully during `verify` with no new violations and no rule exclusions added.

### Outcome

The `verify` phase passes with updated PMD and SpotBugs. No new violations are introduced. If the tools find pre-existing issues that were previously undetected, fix the code rather than suppressing the rules.

### Architectural decision

PMD 7.17→7.26 is a minor jump within the 7.x line. SpotBugs 4.9→4.10 is a minor jump. Both tools maintain backward compatibility for standard rulesets within minor versions. The project's `AGENTS.md` explicitly forbids adding PMD or SpotBugs exclusions to make checks pass — any new violations must be fixed in code. Checkstyle is excluded from this task because it has a major version jump (10→14) and is handled in P0-2.

### Required changes

1. Update `<pmd.version>` from `7.17.0` to `7.26.0`.
2. Update `<spotbugs.version>` from `4.9.8.3` to `4.10.4.0`.
3. Update `<spotbugs.annotations.version>` from `4.10.2` to `4.10.4`.
4. Run `./mvnw verify` — if PMD reports new violations, fix the code. If SpotBugs reports new findings, fix the code or update `spotbugs-exclude.xml` only if the finding is a confirmed false positive.
5. If a PMD rule is removed or renamed in 7.26, update `config/pmd/backend-srp-ruleset.xml`.

### Out of scope

- Checkstyle (handled in P0-2 — major version jump 10→14).
- JaCoCo version (P1-7).
- Surefire plugin version (stay on 3.5.4).

### Acceptance criteria

- `./mvnw verify` passes.
- No new `<exclude>` entries added to `config/pmd/backend-srp-ruleset.xml` or `config/spotbugs-exclude.xml` unless a confirmed false positive.
- No `@SuppressWarnings` annotations added to Java source files.
- PMD and SpotBugs reports show zero violations.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml apps/backend/config/
git commit -m "chore(backend): bump PMD and SpotBugs to latest"
```

---

## TASK P0-2: Update Checkstyle to 14.0.0 (major version jump)

**Status:** DONE
**Priority:** P0
**Depends on:** P0-1

**Exact scope:**

Update Checkstyle from 10.21.4 to 14.0.0 in `apps/backend/pom.xml`. This is a **four major version jump** (10→11→12→13→14) and the highest-risk dependency update in this backlog. The Checkstyle config file `apps/backend/config/checkstyle.xml` will likely need significant updates — rules may have been renamed, removed, added, or changed in severity across these major versions. Checkstyle 14.0.0 requires Java 21+ (compatible with the project's Java 25).

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<checkstyle.version>10.21.4</checkstyle.version>` in `apps/backend/pom.xml`.
- Modify `apps/backend/config/checkstyle.xml` — extensive updates expected for Checkstyle 14.0.0 rule compatibility.
- Potentially modify Java source files if Checkstyle 14.0.0 flags new violations that were not detected by 10.21.4.

**Goal:**

Checkstyle 14.0.0 runs successfully during the `validate` phase with no violations and no rule exclusions added to make checks pass.

### Outcome

The `verify` phase passes with Checkstyle 14.0.0. The `config/checkstyle.xml` is updated to use Checkstyle 14.0.0-compatible rule names and configuration syntax. All Java source code passes the updated ruleset.

### Architectural decision

Checkstyle 14.0.0 is a major version released on 2026-08-18 (8 days ago). The jump from 10 to 14 spans four major versions, each of which may have:
- Renamed or removed configuration properties
- Changed rule default behaviours
- Added new required rules
- Changed the XML config schema

The `maven-checkstyle-plugin` version (3.6.0) is already at latest stable and is confirmed compatible with Checkstyle 14.0.0 (the Checkstyle 14.0.0 POM itself uses `checkstyle.plugin.version 3.6.0`). The `maven-checkstyle-plugin` dependency on `com.puppycrawl.tools:checkstyle` is explicitly versioned via `${checkstyle.version}`, so updating the property is sufficient.

This task is P0 because a broken Checkstyle config will block the entire `verify` phase, preventing all other backend work. It is isolated from P1-6 (PMD/SpotBugs) because the Checkstyle config changes are expected to be extensive and deserve focused attention.

### Required changes

1. Update `<checkstyle.version>` from `10.21.4` to `14.0.0`.
2. Run `./mvnw validate` to trigger Checkstyle — expect config parsing errors or rule violations.
3. For each config error:
   - If a rule name is removed/renamed: update `config/checkstyle.xml` to the new name, or remove the rule if it no longer exists.
   - If a rule property is renamed: update the property in `config/checkstyle.xml`.
   - If the XML schema changed: update the config header/DTD reference.
4. For each new violation in Java source code:
   - Fix the code to comply with the rule (do not suppress).
   - If the rule is not relevant to the project, disable it explicitly in `config/checkstyle.xml` with a comment explaining why.
5. Iterate: `./mvnw validate` → fix → repeat until Checkstyle passes.
6. Run full `./mvnw verify` to ensure no other phase is affected.
7. Consult the Checkstyle migration notes for versions 11, 12, 13, and 14 for breaking changes:
   - https://checkstyle.org/releasenotes.html
   - Pay attention to removed modules, renamed properties, and changed defaults.

### Out of scope

- PMD and SpotBugs (P1-6).
- maven-checkstyle-plugin version (already at 3.6.0 — latest stable).
- Application logic changes beyond fixing Checkstyle violations.

### Acceptance criteria

- `./mvnw validate` passes with Checkstyle 14.0.0.
- `./mvnw verify` passes.
- `config/checkstyle.xml` uses only Checkstyle 14.0.0-compatible rules and properties.
- No new `<exclude>` entries or suppression configurations added unless a confirmed false positive.
- No `@SuppressWarnings` annotations added to Java source files.
- Checkstyle report has zero violations.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw validate && ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml apps/backend/config/checkstyle.xml apps/backend/src/main/java/
git commit -m "chore(backend): upgrade Checkstyle to 14.0.0"
```

---

## TASK P1-7: Update JaCoCo and maven-compiler-plugin

**Status:** DONE
**Priority:** P1
**Depends on:** P0-1

**Exact scope:**

Update the JaCoCo Maven plugin from 0.8.14 to 0.8.15 and the maven-compiler-plugin from 3.13.0 to 3.15.0 (latest stable — 4.0.0-beta exists but is beta) in `apps/backend/pom.xml`.

**Files:**

- Modify `apps/backend/pom.xml`.
- Search anchor: `<jacoco.version>0.8.14</jacoco.version>` in `apps/backend/pom.xml`.
- Search anchor: `<artifactId>maven-compiler-plugin</artifactId>` followed by `<version>3.13.0</version>` in `apps/backend/pom.xml`.

**Goal:**

JaCoCo 0.8.15 and maven-compiler-plugin 3.15.0 run successfully with no regressions in coverage reporting or compilation.

### Outcome

The `verify` phase passes with updated build plugins. JaCoCo coverage report is generated and the 80% minimum passes. Compilation with `--enable-preview` and Java 25 works correctly with maven-compiler-plugin 3.15.0.

### Architectural decision

JaCoCo 0.8.15 is a patch release. The `argLine` uses `@{argLine}` to preserve the JaCoCo javaagent injection (per repository memory note). maven-compiler-plugin 3.15.0 is a minor version bump from 3.13.0 — it may have changes to how `--enable-preview` and annotation processor paths are handled. The `annotationProcessorPaths` for Lombok and MapStruct must still resolve correctly. maven-compiler-plugin 4.0.0-beta-4 exists but is a beta — skipped in favour of 3.15.0 stable.

### Required changes

1. Update `<jacoco.version>` from `0.8.14` to `0.8.15`.
2. Update `maven-compiler-plugin` version from `3.13.0` to `3.15.0`.
3. Run `./mvnw verify` — verify the JaCoCo agent attaches correctly and the coverage report is generated.
4. Verify that `--enable-preview` flag still works with maven-compiler-plugin 3.15.0.
5. Verify that Lombok and MapStruct annotation processors still run correctly.
6. If coverage numbers shift slightly due to JaCoCo 0.8.15's improved bytecode analysis, ensure the 80% minimum still passes. If it drops below 80%, add tests — do not lower the threshold.

### Out of scope

- Coverage threshold changes (must remain at 80% line coverage).
- JaCoCo exclusions (must remain as-is for Panache repositories).
- Surefire/Failsafe plugin version (stay on 3.5.4 — 3.6.0-M1 is milestone).

### Acceptance criteria

- `./mvnw verify` passes.
- JaCoCo report is generated at `target/site/jacoco/`.
- Coverage check passes (80% minimum line coverage).
- `@{argLine}` placeholder preserved in surefire/failsafe `argLine`.
- `--enable-preview` compilation works with maven-compiler-plugin 3.15.0.
- Lombok and MapStruct annotation processing works.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/pom.xml
git commit -m "chore(backend): bump JaCoCo to 0.8.15 and maven-compiler-plugin to 3.15.0"
```

---

## TASK P1-8: Update web npm patch and minor packages

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Update all npm packages in `apps/web/package.json` that have patch or minor version updates available (excluding major version jumps which are handled in P2-1 and P2-2). This is the safe batch — no breaking API changes expected.

Packages to update (current → target):
- `@lucide/svelte`: 1.31.0 → 1.34.0 (minor)
- `@playwright/test`: 1.61.1 → 1.62.1 (minor)
- `@sveltejs/kit`: 2.69.2 → 2.70.3 (minor)
- `@types/node`: 26.1.1 → 26.3.0 (minor)
- `@vitest/coverage-v8`: 4.1.10 → 4.1.11 (patch)
- `eslint`: 10.2.0 → 10.9.1 (minor)
- `@eslint/js`: 10.0.1 → 10.0.1 (already current; the `v10.9.1` GitHub release is for `eslint`, not `@eslint/js`, which has no npm `10.9.1` release)
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

These are all patch or minor version bumps within their respective major lines. Svelte 5.56.x patches, SvelteKit 2.70.x minor, Vite 8.2.x minor, and ESLint 10.9.x minor are all backward compatible. `@eslint/js` remains at 10.0.1 because the `v10.9.1` GitHub release belongs to `eslint`, while npm has no matching `@eslint/js` release; the packages are versioned independently. `@sveltejs/adapter-node` (5.5.7) and `@sveltejs/adapter-static` (3.0.10) are already at their latest within their ranges. TypeScript stays at 6.0.3 (major to 7.0 is P2-2). `http-proxy-3` stays at 1.23.3 (major to 2.0 is P2-1).

### Required changes

1. Update all listed package versions in `apps/web/package.json` to their target versions; leave `@eslint/js` at 10.0.1 because no newer compatible registry version exists.
2. Run `npm install` in `apps/web/` to regenerate `package-lock.json`.
3. Run `npm run lint` — if ESLint 10.9 or eslint-plugin-svelte 3.23 introduces new lint errors, fix the code (do not use `eslint-disable`).
4. Run `npm run test` — if Vitest 4.1.11 or svelte-check 4.7.6 introduces new type errors or test failures, fix them.
5. Run `npm run build` — if Vite 8.2 or SvelteKit 2.70 introduces build changes, fix any build errors.
6. If `@sveltejs/kit` 2.70 introduces new deprecation warnings in `svelte.config.js` or `vite.config.ts`, address them.

### Out of scope

- `http-proxy-3` major version (P2-1).
- `typescript` major version (P2-2).
- `@sveltejs/adapter-node` and `@sveltejs/adapter-static` (already at latest within range).
- E2E tests (validated in P1-10).

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

## TASK P2-1: Update http-proxy-3 to 2.0.0 (major version)

**Status:** DONE
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

http-proxy-3 2.0.0 is a major version bump. The library is used in `scripts/preview.mjs` (the production preview server). The proxy server is critical for the Docker deployment (`apps/web/Dockerfile` CMD is `node scripts/preview.mjs`). If the 2.0 API is incompatible and the migration is too complex, document the decision to stay on 1.23.3 and mark this task as BLOCKED with a clear reason.

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

## TASK P2-2: Update TypeScript to 7.0.2 (major version)

**Status:** DONE
**Priority:** P2
**Depends on:** P1-8

**Exact scope:**

Add TypeScript 7.0.2 as `@typescript/native` in `apps/web/package.json`. Keep the
official `@typescript/typescript6` alias at the `typescript` package name while
Svelte and ESLint require the TypeScript 6 programmatic API.

**Files:**

- Modify `apps/web/package.json`.
- Modify `apps/web/package-lock.json` (via `npm install`).
- Modify `apps/web/tsconfig.json` if TypeScript 7.0 requires config changes.
- Potentially modify any `.ts` or `.svelte` files with type errors introduced by TypeScript 7.0.

**Goal:**

TypeScript 7.0.2 is used by `tsc` for native project type checking. `svelte-check`
and the Svelte/Vite build continue to use the supported TypeScript 6 compatibility
API with no type errors.

### Outcome

`npx tsc --noEmit`, `npm run lint` (which runs `svelte-check --tsconfig
./tsconfig.json`), and `npm run build` pass. No `@ts-ignore` or
`@ts-expect-error` directives are added.

### Architectural decision

TypeScript 7.0 does not provide the programmatic API required by Svelte,
`svelte-check`, and `typescript-eslint`. Follow TypeScript's supported
side-by-side migration: install TypeScript 7 as `@typescript/native` for `tsc`,
and alias `typescript` to `@typescript/typescript6` until the dependent tools
support the TypeScript 7 API.

### Required changes

1. Check the TypeScript 7.0 release notes and migration guide.
2. Add `@typescript/native` as an npm alias for `typescript@^7.0.2` and alias
   `typescript` to `@typescript/typescript6@^6.0.2` in `apps/web/package.json`.
3. Run `npm install` to update `package-lock.json`.
4. Run `npx tsc --noEmit` to validate TypeScript 7 native type checking.
5. Run `npm run lint` — if `svelte-check` reports new type errors, fix each one in the source code. Do not use `@ts-ignore` or `@ts-expect-error`.
6. Run `npm run test` — if Vitest's TypeScript transformation changes, fix test files.
7. Run `npm run build` — if Vite's TypeScript handling changes, fix build errors.
8. If `tsconfig.json` uses options removed in TypeScript 7, update the config.

### Out of scope

- http-proxy-3 (P2-1).
- Other npm packages.

### Acceptance criteria

- `npx tsc --version` reports 7.0.2 and `npx tsc --noEmit` passes.
- `npm run lint` passes (includes `svelte-check`).
- `npm run test` passes.
- `npm run build` passes.
- No `@ts-ignore` or `@ts-expect-error` directives added.

### Targeted validation

```bash
cd apps/web && npm install && npx tsc --noEmit && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/package.json apps/web/package-lock.json apps/web/tsconfig.json
git commit -m "chore(web): add TypeScript 7 compatibility setup"
```

---

## TASK P2-3: Update Docker base images

**Status:** TODO
**Priority:** P2
**Depends on:** P0-1, P1-8

**Exact scope:**

Update Docker base image tags and the New Relic agent version in all Dockerfiles to their latest available versions.

**Files:**

- Modify `apps/backend/Dockerfile.jvm` — `eclipse-temurin:25-jdk-jammy`, `eclipse-temurin:25-jre-jammy`, `NEW_RELIC_AGENT_VERSION=9.3.0`.
- Modify `apps/backend/Dockerfile` — `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`, `quay.io/quarkus/ubi9-quarkus-micro-image:2.0`.
- Modify `apps/web/Dockerfile` — `node:24-alpine`.
- Modify `docker-compose.yml` — `postgres:18-alpine`.
- Modify `docker-compose.native.yml` — check for any base image references.

**Goal:**

All Docker base images use the latest available tags. The New Relic Java agent is at its latest stable version.

### Outcome

`docker compose config` validates successfully. `docker compose build` succeeds for all services. The built images start and pass health checks.

### Architectural decision

Base image tags like `eclipse-temurin:25-jdk-jammy` and `node:24-alpine` are rolling tags that receive security patches. Check Docker Hub for newer tags. The Quarkus Mandrel builder image tag `jdk-25` should match the latest Quarkus 3.39.1 compatible image. The New Relic agent should be updated to the latest stable release.

### Required changes

1. Check Docker Hub for latest `eclipse-temurin:25` tags — prefer staying on `jammy` unless a newer base offers security improvements.
2. Check quay.io for latest `ubi9-quarkus-mandrel-builder-image` tags compatible with Quarkus 3.39.1 and Java 25.
3. Check quay.io for latest `ubi9-quarkus-micro-image` tags — update if newer exists.
4. Check Docker Hub for latest `node:24-alpine` — update to latest 24.x alpine tag.
5. Check Docker Hub for latest `postgres:18-alpine` — update if newer 18.x exists.
6. Check the New Relic Java agent releases page for the latest stable version — update `NEW_RELIC_AGENT_VERSION` in `apps/backend/Dockerfile.jvm`.
7. Run `docker compose config` to validate compose files.
8. Run `docker compose --profile db up -d --build` to verify the full stack builds and starts.
9. Verify health checks pass for all services.

### Out of scope

- Application code changes.
- Docker Compose network or volume configuration.

### Acceptance criteria

- `docker compose config` passes for both compose files.
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

The Telegram SDK is served from Telegram's CDN without a version pin — this is the standard integration pattern. There is no npm package to update. The `TelegramWebApp` type in `telegram.ts` is a minimal hand-written type. If the current SDK adds new methods the app should use, those are out of scope for a dependency update — they are feature additions.

### Required changes

1. Verify `https://telegram.org/js/telegram-web-app.js` returns HTTP 200 with valid JavaScript.
2. Verify `apps/web/svelte.config.js` CSP `script-src` directive includes `https://telegram.org`.
3. Review `apps/web/src/lib/services/telegram.ts` — verify `TelegramWebApp` type fields still exist in the current SDK by checking the Telegram Mini App docs.
4. If the SDK has renamed or removed any fields: update the type and all usages. If unchanged: no code change needed.
5. Run `npm run build` and verify no CSP-related build warnings.

### Out of scope

- Adding new Telegram SDK features — those are product features, not dependency updates.
- Self-hosting the Telegram SDK — keep the CDN load pattern.
- Backend Telegram integration — it uses standard Quarkus REST, no separate dependency.

### Acceptance criteria

- `https://telegram.org/js/telegram-web-app.js` returns HTTP 200 with valid JavaScript.
- CSP `script-src` includes `https://telegram.org` in `svelte.config.js`.
- `TelegramWebApp` type fields match the current Telegram SDK API.
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

## TASK P1-11: Migrate Svelte 4 props to `$props()` runes

**Status:** TODO
**Priority:** P1
**Depends on:** P1-8

**Exact scope:**

Migrate all 62+ `.svelte` components from Svelte 4 `export let` prop syntax to Svelte 5 `$props()` runes. The project already runs Svelte 5 (`5.56.4`) but every component uses deprecated Svelte 4 syntax in legacy mode. This is the foundational Svelte 5 migration task — all other Svelte 5 migration tasks (P1-12 through P1-15) depend on this one because `$props()` is the entry point for callback props, snippet children, and other runes-based APIs.

**Files:**

- Modify all `.svelte` files in `apps/web/src/lib/components/` and `apps/web/src/routes/` that use `export let`.
- Search anchor: `export let` across all `apps/web/src/**/*.svelte` files (62 files, 226 occurrences).

**Goal:**

Every `.svelte` component uses `let { prop1, prop2 } = $props();` instead of `export let prop1;` / `export let prop2;`. No `export let` remains in any `.svelte` file.

### Outcome

`grep -r 'export let' apps/web/src/**/*.svelte` returns zero results. All components use `$props()` destructuring. Svelte 5 legacy mode warnings for `export let` are eliminated.

### Architectural decision

`export let` is the Svelte 4 prop declaration syntax. Svelte 5 introduces `$props()` runes as the replacement. Svelte 5 supports `export let` in legacy mode but emits deprecation warnings and will remove it in Svelte 6. The migration is mechanical but pervasive — every component is affected. The `$props()` syntax also enables the migration of `createEventDispatcher` (P1-15) and `<slot>` (P1-14) since callback props and snippet children are passed via `$props()`.

Default values change from `export let foo = 'default'` to `let { foo = 'default' } = $props()`. Type annotations change from `export let foo: string` to `let { foo }: { foo: string } = $props()`.

### Required changes

1. Search all `.svelte` files for `export let` — there are 62 files with 226 occurrences.
2. For each file, replace `export let propName;` / `export let propName = defaultValue;` / `export let propName: Type;` with `let { propName, propName2 } = $props();` destructuring at the top of the `<script>` block.
3. Preserve default values: `export let foo = 'bar'` becomes `let { foo = 'bar' } = $props()`.
4. Preserve type annotations: `export let foo: string` becomes `let { foo }: { foo: string } = $props()`.
5. For components with a single prop, use `let { prop } = $props()`. For components with multiple props, group them in one destructuring.
6. Run `npm run lint` after each batch of files — `svelte-check` will flag any type errors from the migration.
7. Run `npm run build` to verify the build passes.
8. Run `npm run test` to verify unit tests pass.

### Out of scope

- `$:` reactive statement migration (P1-12).
- `on:event` directive migration (P1-13).
- `<slot>` / snippet migration (P1-14).
- `createEventDispatcher` migration (P1-15).
- `writable()` store migration to `$state` — stores are not deprecated in Svelte 5, modernize opportunistically.

### Acceptance criteria

- `grep -r 'export let' apps/web/src/` returns zero results.
- `npm run lint` passes with no new errors.
- `npm run test` passes with no new failures.
- `npm run build` passes with no errors.
- No `@ts-ignore` or `@ts-expect-error` added.
- No Svelte 5 legacy mode warnings for `export let` in the build output.

### Targeted validation

```bash
cd apps/web && grep -r 'export let' src/ || echo 'No export let found' && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/
git commit -m "refactor(web): migrate Svelte 4 export let props to $props() runes"
```

---

## TASK P1-12: Migrate Svelte 4 `$:` reactives to `$derived`/`$effect`

**Status:** TODO
**Priority:** P1
**Depends on:** P1-11

**Exact scope:**

Migrate all 34+ `.svelte` components from Svelte 4 `$:` reactive statements to Svelte 5 `$derived()` and `$effect()` runes. `$:` statements are deprecated in Svelte 5 and will be removed in Svelte 6.

**Files:**

- Modify all `.svelte` files in `apps/web/src/` that use `$:` reactive statements.
- Search anchor: `$:` across all `apps/web/src/**/*.svelte` files (34 files, 129+ occurrences).

**Goal:**

Every `$:` reactive statement is replaced with `$derived()` (for computed values) or `$effect()` (for side effects). No `$:` syntax remains in any `.svelte` file.

### Outcome

`grep -r '\$:' apps/web/src/**/*.svelte` returns zero results (excluding `$:` in strings/comments). All reactive computations use `$derived()` and side effects use `$effect()`.

### Architectural decision

`$:` is the Svelte 4 reactive statement syntax. Svelte 5 replaces it with two distinct runes:
- `$derived()` — for computed values that return a result (replaces `$: result = a + b`).
- `$effect()` — for side effects that run when dependencies change (replaces `$: { ... }` blocks and `$: if (...) { ... }`).

The migration requires classifying each `$:` statement as either a computed value or a side effect. Statements like `$: total = items.length` become `let total = $derived(items.length)`. Statements like `$: { if (x) doSomething(); }` become `$effect(() => { if (x) doSomething(); })`.

### Required changes

1. Search all `.svelte` files for `$:` reactive statements — 34 files, 129+ occurrences.
2. For each `$:` statement, classify it:
   - If it assigns a value (`$: result = expr`): replace with `let result = $derived(expr)`.
   - If it's a block (`$: { ... }`): replace with `$effect(() => { ... })`.
   - If it's a conditional (`$: if (cond) { ... }`): replace with `$effect(() => { if (cond) { ... } })`.
3. Ensure `$derived()` expressions are pure (no side effects inside).
4. Ensure `$effect()` callbacks properly track dependencies (Svelte 5 auto-tracks).
5. Run `npm run lint` after each batch — `svelte-check` will flag incorrect runes usage.
6. Run `npm run build` and `npm run test`.

### Out of scope

- `export let` migration (P1-11, must be done first).
- `on:event` migration (P1-13).
- `<slot>` migration (P1-14).
- `createEventDispatcher` migration (P1-15).

### Acceptance criteria

- `grep -rn '^\$:' apps/web/src/` returns zero results (excluding comments and strings).
- `npm run lint` passes with no new errors.
- `npm run test` passes with no new failures.
- `npm run build` passes with no errors.
- No Svelte 5 legacy mode warnings for `$:` in build output.

### Targeted validation

```bash
cd apps/web && grep -rn '^\$:' src/ --include='*.svelte' || echo 'No $: found' && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/
git commit -m "refactor(web): migrate Svelte 4 $: reactives to $derived/$effect runes"
```

---

## TASK P1-13: Migrate Svelte 4 `on:event` directives to event attributes

**Status:** TODO
**Priority:** P1
**Depends on:** P1-11

**Exact scope:**

Migrate all 51+ `.svelte` components from Svelte 4 `on:click`, `on:change`, `on:keydown` (and other `on:*`) event directives to Svelte 5 event attributes (`onclick`, `onchange`, `onkeydown`). Also migrate event modifiers (`on:click|stopPropagation`) to inline `event.stopPropagation()` calls. `on:event` directives are deprecated in Svelte 5 and will be removed in Svelte 6.

**Files:**

- Modify all `.svelte` files in `apps/web/src/` that use `on:` event directives.
- Search anchor: `on:` across all `apps/web/src/**/*.svelte` template sections (51 files, 300+ occurrences).

**Goal:**

Every `on:click={...}` is replaced with `onclick={...}`. Every `on:click|stopPropagation={...}` is replaced with `onclick={(e) => { e.stopPropagation(); ... }}`. No `on:*` directives remain.

### Outcome

`grep -rn 'on:[a-z]' apps/web/src/ --include='*.svelte'` returns zero results (excluding `on:` in strings). All event handlers use the new attribute syntax.

### Architectural decision

Svelte 4 uses `on:event={handler}` directive syntax. Svelte 5 replaces it with `onevent={handler}` attribute syntax (e.g., `onclick`, `onchange`). Event modifiers like `|stopPropagation`, `|preventDefault` are removed in Svelte 5 — the handler must call `event.stopPropagation()` / `event.preventDefault()` manually.

The migration is mechanical but pervasive (300+ occurrences across 51 files). Each `on:click={handler}` becomes `onclick={handler}`. Each `on:click|stopPropagation={handler}` becomes `onclick={(e) => { e.stopPropagation(); handler(e); }}`.

### Required changes

1. Search all `.svelte` files for `on:` event directives — 51 files, 300+ occurrences.
2. For each `on:event={handler}`:
   - Without modifier: replace with `onevent={handler}` (e.g., `on:click={handleClick}` → `onclick={handleClick}`).
   - With `|stopPropagation`: replace with `onevent={(e) => { e.stopPropagation(); handler(e); }}`.
   - With `|preventDefault`: replace with `onevent={(e) => { e.preventDefault(); handler(e); }}`.
   - With `|stopPropagation|preventDefault`: combine both calls.
3. Files with the most occurrences: `TelegramParentFamily.svelte` (20+), `ParentInvitationFlow.svelte` (20+), `telegram/dashboard/+page.svelte` (20+), `TelegramGroupManager.svelte` (15+).
4. Run `npm run lint`, `npm run build`, `npm run test`.

### Out of scope

- `export let` migration (P1-11).
- `$:` migration (P1-12).
- `<slot>` migration (P1-14).
- `createEventDispatcher` migration (P1-15).

### Acceptance criteria

- `grep -rn ' on:[a-z]' apps/web/src/ --include='*.svelte'` returns zero results (excluding strings/comments).
- `grep -rn '|stopPropagation\||preventDefault' apps/web/src/ --include='*.svelte'` returns zero results.
- `npm run lint` passes with no new errors.
- `npm run test` passes with no new failures.
- `npm run build` passes with no errors.

### Targeted validation

```bash
cd apps/web && grep -rn ' on:[a-z]' src/ --include='*.svelte' || echo 'No on: directives found' && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/
git commit -m "refactor(web): migrate Svelte 4 on:event directives to event attributes"
```

---

## TASK P1-14: Migrate Svelte 4 `<slot>` to `{@render}` snippets

**Status:** TODO
**Priority:** P1
**Depends on:** P1-11

**Exact scope:**

Migrate all 8 `.svelte` components that use `<slot>` tags and `$$slots` references to Svelte 5 snippet children with `{@render}`. Also migrate `slot="name"` attributes (9 files, 23 occurrences) and `<svelte:fragment slot="...">` (8 occurrences) to named snippet props.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte` — `<slot />`, `$$slots`, `slot="selection"`, `slot="icon"`.
- Modify `apps/web/src/lib/components/telegram/TelegramBottomSheet.svelte` — `<slot />`.
- Modify `apps/web/src/lib/components/telegram/TelegramEntityRow.svelte` — `<slot name="selection"/>`, `<slot name="icon"/>`, `$$slots`.
- Modify `apps/web/src/lib/components/telegram/TelegramListSurface.svelte` — `<slot />`.
- Modify `apps/web/src/lib/components/workspace/DashboardPeriodControl.svelte` — `<slot name="label">`.
- Modify `apps/web/src/lib/features/workspace/access/ParentMembershipList.svelte` — `<slot name="admin-note" />`.
- Modify `apps/web/src/routes/app/+layout.svelte` — `<slot />`.
- Modify `apps/web/src/routes/app/telegram/+layout.svelte` — `<slot />`.
- Modify components with `slot="name"` attribute: `TelegramChildRewards.svelte`, `TelegramChildTasks.svelte`, `TelegramHistoryList.svelte`, `TelegramParentRewards.svelte`, `TelegramParentTasks.svelte`, `TelegramReadyCatalog.svelte`, `ParentInvitationFlow.svelte`, `telegram/dashboard/+page.svelte`.
- Search anchor: `<slot` and `$$slots` and `slot="` across `apps/web/src/**/*.svelte`.

**Goal:**

Every `<slot>` tag is replaced with `{@render children()}`. Every named slot (`<slot name="foo">`) is replaced with a named snippet prop (`{@render foo()}`). Every `slot="name"` attribute on child components is replaced with a snippet prop. No `<slot>` or `slot="` syntax remains.

### Outcome

`grep -r '<slot' apps/web/src/ --include='*.svelte'` returns zero results. `grep -r 'slot="' apps/web/src/ --include='*.svelte'` returns zero results. All content projection uses Svelte 5 snippets.

### Architectural decision

Svelte 4 uses `<slot>` for content projection and `<slot name="foo">` for named slots. Consumers use `slot="foo"` to target named slots. Svelte 5 replaces this with snippet props:

- Default slot: component declares `let { children } = $props();` and renders `{@render children()}`. Consumer passes content as `<Component>{content}</Component>`.
- Named slot: component declares `let { foo } = $props();` and renders `{@render foo()}`. Consumer passes content as `<Component foo={() => content}>` or `<Component {#snippet foo()}content{/snippet}>`.
- `$$slots` checks: replace with `typeof children === 'function'` or similar checks.

### Required changes

1. For each component with `<slot />`:
   - Add `children` to the `$props()` destructuring.
   - Replace `<slot />` with `{@render children()}`.
2. For each component with `<slot name="foo">`:
   - Add `foo` to the `$props()` destructuring.
   - Replace `<slot name="foo">` with `{@render foo()}`.
3. For each `$$slots.foo` check: replace with `typeof foo === 'function'`.
4. For each consumer using `slot="foo"`: replace with either inline snippet `{#snippet foo()}content{/snippet}` or callback prop `foo={() => content}`.
5. For each `<svelte:fragment slot="foo">`: replace with `{#snippet foo()}content{/snippet}`.
6. For `let:period` slot prop in `telegram/dashboard/+page.svelte` (line 400): replace with snippet parameter — the parent component passes a snippet that receives `period` as an argument.
7. Run `npm run lint`, `npm run build`, `npm run test`.

### Out of scope

- `export let` migration (P1-11).
- `$:` migration (P1-12).
- `on:event` migration (P1-13).
- `createEventDispatcher` migration (P1-15).

### Acceptance criteria

- `grep -r '<slot' apps/web/src/ --include='*.svelte'` returns zero results.
- `grep -r 'slot="' apps/web/src/ --include='*.svelte'` returns zero results.
- `grep -r '\$\$slots' apps/web/src/ --include='*.svelte'` returns zero results.
- `npm run lint` passes with no new errors.
- `npm run test` passes with no new failures.
- `npm run build` passes with no errors.

### Targeted validation

```bash
cd apps/web && grep -r '<slot\|slot="\|\$\$slots' src/ --include='*.svelte' || echo 'No slot patterns found' && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/
git commit -m "refactor(web): migrate Svelte 4 slot to {@render} snippets"
```

---

## TASK P1-15: Migrate `createEventDispatcher` to callback props

**Status:** TODO
**Priority:** P1
**Depends on:** P1-11

**Exact scope:**

Migrate the 4 `.svelte` components that use `createEventDispatcher` to Svelte 5 callback props. `createEventDispatcher` is deprecated in Svelte 5 and will be removed in Svelte 6.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramCoinAdjust.svelte` — `createEventDispatcher<{...}>()` + `dispatch('adjust', ...)`.
- Modify `apps/web/src/lib/components/telegram/TelegramGroupManager.svelte` — `createEventDispatcher<{...}>()` + `dispatch(...)`.
- Modify `apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte` — `createEventDispatcher<{...}>()` + `dispatch(...)`.
- Modify `apps/web/src/lib/components/telegram/TelegramRequestSheet.svelte` — `createEventDispatcher<{...}>()` + `dispatch(...)`.
- Modify all parent components that use `on:adjust`, `on:close`, etc. to consume events from these 4 components — replace with callback props.

**Goal:**

No `createEventDispatcher` or `dispatch()` calls remain in any `.svelte` file. All child-to-parent communication uses callback props passed via `$props()`.

### Outcome

`grep -r 'createEventDispatcher' apps/web/src/ --include='*.svelte'` returns zero results. `grep -r 'dispatch(' apps/web/src/ --include='*.svelte'` returns zero results.

### Architectural decision

Svelte 4 uses `createEventDispatcher` to dispatch custom events from child to parent. Parents listen with `on:eventname`. Svelte 5 replaces this with callback props:

- Child declares `let { onAdjust } = $props();` and calls `onAdjust?.(data)` instead of `dispatch('adjust', data)`.
- Parent passes `<Child onAdjust={(data) => { ... }} />` instead of `<Child on:adjust={(e) => e.detail }>`.
- The event `detail` becomes the direct callback argument — no `e.detail` wrapping.

### Required changes

1. For each of the 4 components:
   - Remove `import { createEventDispatcher } from 'svelte'`.
   - Remove `const dispatch = createEventDispatcher<{...}>()`.
   - Add event callback names to `$props()` destructuring (e.g., `let { onAdjust, onClose } = $props()`).
   - Replace `dispatch('adjust', payload)` with `onAdjust?.(payload)`.
   - Type the callback props: `let { onAdjust }: { onAdjust?: (payload: PayloadType) => void } = $props()`.
2. For each parent component that consumes events from these 4 components:
   - Replace `on:adjust={(e) => handler(e.detail)}` with `onAdjust={(payload) => handler(payload)}`.
   - This depends on P1-13 (event directive migration) being done for the parent components.
3. Run `npm run lint`, `npm run build`, `npm run test`.

### Out of scope

- `export let` migration (P1-11).
- `$:` migration (P1-12).
- `on:event` migration (P1-13 — but parent components need it for consuming the old events).
- `<slot>` migration (P1-14).
- `writable()` store migration — not deprecated, modernize opportunistically.

### Acceptance criteria

- `grep -r 'createEventDispatcher' apps/web/src/ --include='*.svelte'` returns zero results.
- `grep -r 'dispatch(' apps/web/src/ --include='*.svelte'` returns zero results (excluding non-event dispatch like `window.dispatchEvent`).
- `npm run lint` passes with no new errors.
- `npm run test` passes with no new failures.
- `npm run build` passes with no errors.

### Targeted validation

```bash
cd apps/web && grep -r 'createEventDispatcher\|dispatch(' src/ --include='*.svelte' || echo 'No event dispatcher patterns found' && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/
git commit -m "refactor(web): migrate createEventDispatcher to callback props"
```

---

## TASK P1-9: Backend full verification gate

**Status:** TODO
**Priority:** P1
**Depends on:** P0-1, P0-2, P1-1, P1-2, P1-3, P1-4, P1-5, P1-6, P1-7

**Exact scope:**

Run the complete backend verification suite after all backend dependency updates are applied. This is the final gate that confirms the entire backend stack works together with all updated dependencies.

**Files:**

- No file changes expected — this is a verification task.
- If the full `verify` reveals issues that individual task validation missed, fix them here.

**Goal:**

The backend passes `./mvnw verify` with all dependencies updated and no regressions.

### Outcome

`./mvnw verify` passes cleanly: compilation, unit tests, JaCoCo coverage check, Checkstyle 14.0.0, PMD 7.26.0, and SpotBugs 4.10.4.0 all succeed.

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

- Web verification (P1-10).
- Docker verification (P0-3).

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

## TASK P1-10: Web full verification gate

**Status:** TODO
**Priority:** P1
**Depends on:** P1-8, P2-1, P2-2, P2-4, P1-11, P1-12, P1-13, P1-14, P1-15

**Exact scope:**

Run the complete web verification suite after all web dependency updates and Svelte 5 legacy code migrations are applied. This includes lint, unit tests, production build, and E2E tests. This gate validates that the Svelte 4→5 syntax migration (P1-11 through P1-15) combined with the dependency updates (P1-8, P2-1, P2-2) produces a working application.

**Files:**

- No file changes expected — this is a verification task.
- If the full validation reveals issues that individual task validation missed, fix them here.

**Goal:**

The web app passes `npm run lint`, `npm run test`, `npm run build`, and `npm run test:e2e` with all dependencies updated.

### Outcome

All web quality gates pass: ESLint, svelte-check, Vitest unit tests, Vite production build, and Playwright E2E tests.

### Architectural decision

Per `AGENTS.md`, `npm run lint`, `npm run test`, and `npm run build` are always required. E2E tests are required when UI changes occur — the Svelte 4→5 syntax migration (P1-11 through P1-15) changes every component's prop, event, and slot syntax, so E2E validation is critical to catch behavioural regressions.

### Required changes

1. Run `npm run lint` — fix any new lint errors.
2. Run `npm run test` — fix any new test failures.
3. Run `npm run build` — fix any new build errors.
4. Run `npm run test:e2e` — fix any E2E test failures. If a test fails due to a rendering or timing change introduced by a dependency update, fix the test or the component.
5. Do not skip tests, use `.skip`, or disable E2E test files.

### Out of scope

- Backend verification (P1-9).
- Docker verification (P0-3).

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

## TASK P0-3: Full-stack Docker Compose verification

**Status:** TODO
**Priority:** P0
**Depends on:** P1-9, P1-10, P2-3

**Exact scope:**

Run the full-stack Docker Compose build and startup to verify that all updated dependencies work together in the containerized environment. This is the final integration gate.

**Files:**

- No file changes expected — this is a verification task.

**Goal:**

The full stack (web + backend + PostgreSQL) builds and runs in Docker Compose with all updated dependencies. All services pass health checks.

### Outcome

`docker compose --profile db up -d --build` succeeds. All three services (web, backend, db) reach healthy status. The web app can reach the backend over the `edge` network.

### Architectural decision

Docker Compose is the final integration environment. It validates that the updated Maven dependencies package correctly into the Quarkus uber-jar, the updated npm dependencies build correctly into the SvelteKit production bundle, and the updated base images are compatible. Both JVM and native compose files should be validated.

### Required changes

1. Run `docker compose config` for `docker-compose.yml` — fix any env drift.
2. Run `docker compose -f docker-compose.native.yml config` — fix any env drift.
3. Run `docker compose --profile db up -d --build` — verify all images build.
4. Wait for health checks to pass (`docker compose ps` shows all healthy).
5. Verify the web service can reach the backend: `curl -s http://localhost:${WEB_PORT}/healthz`.
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
