```markdown
---
name: java-service-standards
description: Java service development standards for Quarkus-based backend services.
---

# ☕ MY JAVA DEVELOPMENT SKILL

> This skill defines coding standards, architecture patterns, and implementation rules
> for Java services. AI agents must follow these rules when implementing new Java backend features.

## Table of Contents
- Tech Stack Reference
- Java 25 Standards
- Project Structure
- Code Quality & Linting
- Formatting Standards
- Builder Pattern Standards
- Mapping Standards
- Database & Persistence Standards
- Flyway Migration Standards
- REST API Standards
- OpenAPI Documentation Standards
- Service Layer Standards
- Exception Handling Standards
- Validation Standards
- Lombok Usage Standards
- Testing Standards
- Configuration Standards
- Logging Standards
- Security Standards
- JWT & Cookie Implementation Rules
- WebSocket Standards
- Async & Concurrency Standards
- Virtual Threads Adoption Rules
- Caching Standards
- Performance & Resource Optimization
- Health & Observability Standards
- CI/CD Readiness Standards
- Master Rules For AI Agent
- Prefer Over
- Architecture-Specific Rules

---

## 🔧 Tech Stack Reference

### Core Platform
| Component | Version | Target |
|-----------|---------|--------|
| Java | 25 (`maven.compiler.release=25`) | 25 |
| Preview Features | Enabled (`--enable-preview` in compiler/surefire/failsafe) | Keep enabled while using preview syntax |
| Spring Boot | Not used in this project | N/A |
| Runtime Framework | Quarkus 3.34.2 | Latest stable Quarkus 3.x |
| Build Tool | Maven Wrapper 3.9.14 | Maven 3.9.x+ |

### Dependencies
```text
[group: Quarkus Core (managed by Quarkus BOM 3.34.2)]
- io.quarkus:quarkus-arc
- io.quarkus:quarkus-rest
- io.quarkus:quarkus-rest-jackson
- io.quarkus:quarkus-smallrye-health
- io.quarkus:quarkus-smallrye-openapi

[group: Persistence / Database]
- io.quarkus:quarkus-hibernate-orm
- io.quarkus:quarkus-hibernate-orm-panache
- io.quarkus:quarkus-jdbc-postgresql
- io.quarkus:quarkus-agroal
- io.quarkus:quarkus-flyway

[group: Validation / API]
- io.quarkus:quarkus-hibernate-validator
- io.quarkus:quarkus-websockets-next

[group: Mapping]
- org.mapstruct:mapstruct:1.6.3
- org.projectlombok:lombok:1.18.38 (scope: provided)

[group: Utilities]
- org.jsoup:jsoup:1.18.3

[group: Testing]
- io.quarkus:quarkus-junit
- io.rest-assured:rest-assured
- org.assertj:assertj-core:3.27.3
- org.mockito:mockito-core
- org.mockito:mockito-junit-jupiter
- io.quarkus:quarkus-jdbc-h2

[group: Quality Plugins]
- org.jacoco:jacoco-maven-plugin:0.8.14
- org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0
- com.github.spotbugs:spotbugs-maven-plugin:4.9.8.3
```

### Build Rules & Plugin Configuration
- `quarkus-maven-plugin` drives build and code generation.
- `maven-compiler-plugin`: release=25, --enable-preview, annotation processors: Lombok + MapStruct.
- `maven-surefire-plugin` and `maven-failsafe-plugin` use `--enable-preview`.
- JaCoCo line coverage gate in `verify`: minimum `0.80`.
- Checkstyle runs in `validate` and fails build on errors.
- SpotBugs runs in `verify` with `failOnError=false`.

### Dependency Management Rules
- ALWAYS check latest stable dependency versions via Context7 MCP before adding new libraries.
- ALWAYS prefer BOM-managed versions where available.
- ALWAYS verify Quarkus BOM already manages the dependency before pinning a version.
- NEVER pin old versions without a written rationale in PR notes.
- NEVER add a dependency without checking maintenance status and known CVEs.

---

## ☕ Java 25 Standards

### Target Language Level
- Target Java 25 and compile with preview features enabled.
- Use modern language constructs when they improve readability and reduce boilerplate.

### Features To Use Actively

**Records** — for all immutable DTOs, request and response models.

**Sealed types + records** — for result modeling with explicit success/failure variants.

**Switch expressions with type patterns** — for branching on sealed result types instead of if-else chains.

**`var`** — for local variables where the type is obvious from the right-hand side.

**Stream API + collector pipelines** — for collection transformations and projections.

**Sequenced collections** (`getFirst`, `getLast`) — use when accessing ordered collection boundaries.

### Features Allowed For New Code Where Appropriate
- Virtual threads (see Virtual Threads Adoption Rules section)
- Structured concurrency (see Virtual Threads Adoption Rules section)
- Text blocks for multiline string literals
- Stream gatherers for custom intermediate stream operations
- Unnamed variables (`_`) for intentionally unused lambda or pattern variables

### Code Style Maturity Level
- Mature modern Java style: records, sealed types, switch expressions, streams, explicit domain modeling.
- Adopt advanced features only where they directly improve clarity — never for novelty.

---

## 📁 Project Structure

### Package Layout
```text
src/main/java/{base.package}/
  config/
  domain/model/
  dto/request/
  dto/response/
  exception/
  repository/
  resource/
  service/
  util/
  util/validation/

src/test/java/{base.package}/
  config/
  domain/model/
  exception/
  repository/
  resource/
  service/
  support/
```

### File Organization Rules
- One public type per file.
- Naming is strongly layer-based — suffix must reflect the layer role.
- Nested types are allowed only for tightly coupled concepts (result variants, config groups).

### Naming Conventions
```text
REST Resources:       *Resource
WebSocket Endpoints:  *Socket
Services:             *Service (interface), *ServiceImpl (implementation)
Repositories:         *Repository
Entities:             *Entity
Request DTOs:         *Request
Response DTOs:        *Response, *Dto
Custom Validators:    Valid* (annotation), *Validator (implementation)
Tests:                *Test
```

---

## 🔍 Code Quality & Linting

### Tools Configured
```text
Checkstyle  — runs in Maven validate phase, fails build on violations
SpotBugs    — runs in Maven verify phase
JaCoCo      — line coverage gate >= 0.80 in verify phase
PMD         — version property present, no active ruleset configured yet
SonarQube   — not configured in this project
```

### Checkstyle Rules In Effect
- Max line length: 120
- Newline at end of file required
- Tabs forbidden — spaces only
- Javadoc comments forbidden
- Block comments `/* ... */` forbidden
- Inline comments allowed only for complex algorithms, non-obvious flow, or tricky bug fixes
- Naming checks: types, methods, fields, constants
- Import hygiene: no wildcards, no unused imports, no redundant imports
- Braces required on all blocks
- Method length max: 60 lines
- Parameter count max: 10
- `IllegalCatch` — catching `Throwable` is forbidden

### SpotBugs Configuration
- Excludes generated code
- Suppresses selected false positives for framework-managed fields
- Suppresses EI_EXPOSE_REP / EI_EXPOSE_REP2 for record types

### Integration
```text
./mvnw validate  →  Checkstyle
./mvnw test      →  Unit + integration tests
./mvnw verify    →  JaCoCo coverage check + SpotBugs
```

---

## 🎨 Formatting Standards

```text
indent_style             = spaces
indent_size              = 4
max_line_length          = 120
charset                  = UTF-8
end_of_line              = lf
insert_final_newline     = true
```

Import ordering:
- Regular imports grouped by package domain.
- Static imports placed at the bottom.
- No wildcard imports.

---

## 🏗️ Builder Pattern Standards

### Rules
- Use Lombok `@Builder` for mutable JPA entities that have several optional or defaulted fields.
- Use `@Builder.Default` for fields that carry stable domain defaults (counters, flags, timestamps).
- Use records for immutable request and response contracts — builders are not needed there.
- NEVER expose multiple redundant construction pathways for the same type.

### Entity Lombok Setup
- Entity classes use: `@Getter`, `@Setter`, `@Builder`,
  `@AllArgsConstructor(access = AccessLevel.PACKAGE)`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
- This protects the no-arg constructor from misuse while keeping JPA and builder functional.

---

## 🗺️ Mapping Standards

### Rules
- Keep entity-to-DTO mapping explicit and local when the transformation is short and readable.
- Introduce MapStruct mapper interfaces only when the same mapping logic is repeated
  across three or more service classes.
- NEVER hide business transformation rules inside a mapping utility.
- NEVER map directly from a JPA entity to an HTTP response inside a repository method.

---

## 🗄️ Database & Persistence Standards

### Persistence Stack
- Hibernate ORM + Panache repositories.
- PostgreSQL in runtime, H2 in PostgreSQL-mode for the test profile.
- Flyway for schema lifecycle management.

### Entity Style
- Entities use `@Entity`, `@Table` with explicit table name, `@Column` with explicit column names.
- NEVER rely on Hibernate implicit naming for columns or tables.

### Repository Style
- Repositories extend `PanacheRepositoryBase<Entity, IdType>`.
- Queries use Panache positional parameters (`?1`, `?2`, ...).
- NEVER build query strings through string concatenation or interpolation.

### Transaction Management
- Write operations are annotated with `@Transactional` at repository method level.
- Service methods handle orchestration and business logic without transaction annotations
  unless a multi-repository write must be atomic.

### Fetch Strategy
- Prefer scalar foreign key references over deep JPA relation graphs.
- Fetch associated data explicitly through targeted repository queries.
- NEVER use `FetchType.EAGER` on collection associations.

### Connection Pool
- Configure Agroal pool with explicit max-size, idle-removal-interval, and acquisition-timeout.
- NEVER leave pool configuration at defaults in a production-bound service.

---

## 🗃️ Flyway Migration Standards

### File Naming Convention
```text
V{version}__{description}.sql
```
- Version is a sequential integer — never skip or reorder versions.
- Description uses snake_case.
- NEVER reuse, rename, or edit a migration file after it has been applied to any environment.

### Migration File Location
```text
Main:  src/main/resources/db/migration/
Test:  src/test/resources/db/migration/
```
- Test migrations must be kept manually in sync with main migrations.

### Migration Content Rules
- ALWAYS use explicit column types matching the target database dialect.
- ALWAYS declare `NOT NULL` constraints inline with the column definition.
- ALWAYS add indexes for foreign key columns and hot-lookup columns in the same migration file.
- ALWAYS add a descriptive comment header explaining the purpose of the migration.
- NEVER use `CASCADE DELETE` without a written rationale in the migration comment header.
- NEVER drop a column without a data-loss review comment in the migration header.
- NEVER write environment-specific DDL in shared migration files.

### Rollback Strategy
- Flyway Community Edition does not support automatic rollback.
- Write a compensating `V{n+1}__rollback_{description}.sql` if a migration must be undone.
- Document the rollback intent in both the original and the compensating migration headers.

### Test Profile Behavior
- Test profile uses `quarkus.flyway.clean-at-start=true` — schema is fully dropped and replayed on every run.
- Test migrations must be fully self-contained and idempotent from a clean state.

---

## 🌐 REST API Standards

### Controller Style
- Use Jakarta REST annotations: `@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`.
- Define a class-level base path.
- Declare `@Produces` and `@Consumes` explicitly — never rely on defaults.
- Build `Response` objects explicitly for every branch — never return raw entities.

### Error Response
- All error payloads use the standardized `ErrorResponse` shape (RFC-7807-like):
  `type`, `title`, `status`, `detail`, `errorCode`.
- NEVER return ad-hoc error shapes from any endpoint.

### API Versioning
- Follow the versioning strategy established in the project.
- If no versioning is in place, do not introduce it unilaterally — raise it as a design decision.

---

## 📄 OpenAPI Documentation Standards

### Required Annotations Per Endpoint
- Every REST method MUST have `@Operation(summary, description)`.
- Every REST method MUST have `@APIResponses` listing every reachable HTTP status code.
- Include only status codes that the method can actually produce.
- NEVER add a status code that is not reachable from the method.

### Tag Convention
- Each resource class carries a single `@Tag(name = "...")` at class level.
- Tag names use Title Case noun phrases.
- NEVER put `@Tag` on individual methods.

### Schema Documentation
- Request body record fields carry `@Schema(description = "...")` when purpose is not obvious from the name.
- Sensitive fields (passwords, tokens) carry `@Schema(format = "password")`.
- NEVER expose internal result types or JPA entity classes as API schema.

### Generation
- OpenAPI spec is auto-generated by `quarkus-smallrye-openapi` at `/q/openapi`.
- NEVER maintain a static `openapi.yaml` file manually.

---

## 🧠 Service Layer Standards

### Service Design
- Define a service interface and a single implementation class.
- Use concrete class-only services only for stateless utility helpers with no expected substitution.

### Dependency Injection
- Always use constructor injection.
- Use Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` or explicit `@Inject` constructor.
- NEVER use field injection.

### Result Modeling
- Business methods return a sealed `OperationResult<T>` (or equivalent) to make
  success and failure paths explicit without throwing exceptions for expected outcomes.

### Business Logic Organization
- Resources: auth/role checks and request-to-domain mapping.
- Services: domain validation, business rules, orchestration.
- Repositories: persistence operations only.
- NEVER bypass a layer.

---

## ❌ Exception Handling Standards

### Rules
- Use `OperationResult.Failure` for expected business outcomes — not exceptions.
- Use JAX-RS exception mappers for unexpected system-level failures.
- NEVER catch `ConstraintViolationException` in services — let the mapper handle it.
- NEVER catch `Throwable` — Checkstyle enforces this.
- NEVER return internal exception details or stack traces in HTTP responses.

### Required Mappers
- `ConstraintViolationExceptionMapper` — HTTP 400 with aggregated violation details.
- `GlobalExceptionMapper` — HTTP 500 with stable error code, logged stack trace.

---

## ✅ Validation Standards

### Where Validation Lives
| Layer      | Tool                        | Scope                              |
|------------|-----------------------------|------------------------------------|
| Resource   | Bean Validation annotations | Format, size, pattern, null checks |
| Service    | Explicit guard checks       | Business invariants                |
| Repository | None                        | No validation at persistence layer |

### Bean Validation Rules
- Annotate record fields in request DTOs with Bean Validation constraints.
- Add `@Valid` to every resource method parameter that accepts a request body.
- Bean validation is NOT triggered automatically without `@Valid`.
- Use `@NotNull` at method parameter level to reject a missing body before deserialization.

### Business Invariant Rules
- Return `OperationResult.Failure` with a domain error code for expected business violations.
- NEVER use Bean Validation annotations to enforce business rules.

### Custom Validators
- Create a custom `ConstraintValidator` only when a rule is reused across three or more request types.
- Place custom validator implementations and their annotation interfaces in `util/validation/`.
- Name annotations after the validated concept: `@ValidAmount`, `@ValidUsername`.

---

## 🧩 Lombok Usage Standards

### Annotations To Use
- `@RequiredArgsConstructor(onConstructor_ = @Inject)` — for CDI constructor injection.
- `@Slf4j` — for logging.
- `@Getter`, `@Setter` — explicit field-level control on entities.
- `@Builder`, `@Builder.Default` — for entities with optional or defaulted fields.
- `@AllArgsConstructor(access = AccessLevel.PACKAGE)` — for builder-only entities.
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — for JPA-required no-arg constructor.

### Rules
- NEVER use `@Data` on JPA entities — use explicit `@Getter` and `@Setter`.
- NEVER use `@Data` where equality and hashCode semantics could cause JPA issues.
- KEEP Lombok where it removes boilerplate without hiding business behavior.

---

## 🧪 Testing Standards

### Frameworks
- JUnit 5 for all tests.
- Mockito (`MockitoExtension`) for unit test isolation.
- AssertJ for fluent assertions.
- RestAssured for HTTP-level integration assertions.
- Quarkus test runtime (`@QuarkusTest`) for integration tests.

### Test Class Naming
- `*Test` suffix on all test classes.
- Test packages mirror the source package structure.

### Unit Test Style
- Arrange / Act / Assert structure in every test method.
- Service and resource unit tests use `@ExtendWith(MockitoExtension.class)`.
- Each test method covers one behavior — never assert unrelated outcomes in a single test.

### Integration Test Style
- `@QuarkusTest` combined with RestAssured for endpoint-level assertions.
- Integration tests verify observable HTTP behavior — status codes, response shape, headers.

### Coverage
- JaCoCo line coverage gate is 0.80 — new code must not drop coverage below this threshold.
- Aim to cover both the success path and all reachable failure paths in each unit.

### What Not To Use
- No Testcontainers (H2 in PostgreSQL mode is used for the test profile).
- No Spring test slices (project is Quarkus).

---

## ⚙️ Configuration Standards

### Configuration Files
- `src/main/resources/application.properties` — runtime configuration.
- `src/test/resources/application.properties` — test profile overrides.

### Typed Configuration
- Use `@ConfigMapping(prefix = "...")` for grouping related configuration properties.
- Model nested configuration groups as nested interfaces inside the mapping interface.

### Profile Strategy
- Use a dedicated test profile properties file with an in-memory database and clean-at-start migration.
- NEVER use the production datasource in the test profile.

### Secrets Handling
- NEVER hardcode secrets in properties files committed to the repository.
- ALWAYS use environment variable placeholders: `${SECRET_NAME:safe_local_default}`.
- ALWAYS provide a non-secret safe default for local development only.
- ALWAYS document every new environment variable in project documentation.

---

## 🪵 Logging Standards

### Framework
- SLF4J via Lombok `@Slf4j` on every class that needs logging.

### Log Levels — When To Use Each
- `log.error(...)` — unhandled exceptions, system failures, states that require immediate attention.
- `log.warn(...)` — recoverable situations, degraded behavior, unexpected but handled states.
- `log.info(...)` — significant business events, lifecycle transitions, state changes that matter operationally.
- `log.debug(...)` — detailed execution trace useful during development and troubleshooting.
- NEVER log at a level higher than the situation warrants — noise is as harmful as silence.

### What To Log And Where
- Log entry and exit of significant service operations at `debug` level with key identifiers.
- Log business-significant outcomes (entity created, state changed, action approved) at `info` level.
- Log recoverable fallbacks and unexpected-but-handled states at `warn` level with enough context
  to understand what was attempted and what happened instead.
- Log all unhandled exceptions at `error` level with the full stack trace.
- NEVER log secrets, raw tokens, passwords, or personally identifiable information.
- NEVER log the same event at multiple levels in the same flow.

### Proactive Logging Rule For AI Agent
- When implementing any service method or resource handler, ALWAYS add log statements
  that would allow a developer to diagnose a production issue without attaching a debugger.
- Logs must answer: what operation was attempted, with what key inputs, what was the outcome.
- Logs must be useful under failure — if something goes wrong, the log trail must show
  where the flow diverged and why.
- NEVER add log statements that only restate what the code obviously does (noise).
- ALWAYS choose the correct level — an `info` for a debug detail pollutes production logs;
  a `debug` for a critical failure makes incidents invisible.

### Structured Log Fields
- ALWAYS include a `traceId` in every log statement within a request-scoped operation.
- Include the primary domain entity identifier (e.g., the ID of the resource being operated on)
  as a named field in every log message within that operation's scope.
- Format log messages with named placeholders: `log.info("Order processed orderId={} status={}", id, status)`.
- NEVER use string concatenation to build log messages.

### TraceId Propagation
- Generate or extract a `traceId` at the entry point of every inbound request (filter or interceptor level).
- Store `traceId` in MDC at the start of the request and clear it at the end in a `finally` block.
- Propagate `traceId` to all downstream calls made within the same request scope.
- If a `traceparent` header is present on the inbound request, use its trace ID instead of generating a new one.
- Every log line within a request must carry the `traceId` automatically via MDC.

### MDC Setup Rule
- Initialize MDC keys (`traceId`, plus any other scope identifiers) in the request filter.
- Clear all MDC keys at the end of the request — NEVER leave stale MDC state between requests.

---

## 🔒 Security Standards

### Auth Mechanism
- Implement authentication via a request filter that validates credentials
  and stores the verified identity in request context.
- NEVER perform credential validation inside resource or service classes.

### Role-Based Access
- Extract the authenticated identity and role from request context inside each resource method.
- Check the required role before invoking any service method.
- NEVER read identity or role from the request body or query parameters.

### CSRF Protection
- Validate CSRF tokens on all state-mutating endpoints: POST, PUT, DELETE, PATCH.
- CSRF validation lives in the authentication filter — NEVER duplicate it in services.

### Security Headers
- Set security headers in a dedicated response filter:
  `X-Content-Type-Options`, `X-Frame-Options`, `X-XSS-Protection`,
  `Referrer-Policy`, `Cross-Origin-Resource-Policy`, `Strict-Transport-Security`.
- NEVER set security headers ad-hoc inside resource methods.

---

## 🔑 JWT & Cookie Implementation Rules

### Cookie Design
- Use HttpOnly cookies for authentication tokens — NEVER expose them to JavaScript.
- Use a separate non-HttpOnly cookie for CSRF double-submit tokens.
- Set SameSite=Strict on all auth-related cookies.
- Set explicit max-age, path, and secure attributes on every cookie.

### Token Lifecycle Rules
- Token generation lives in the auth service only — NEVER inline token creation in resources.
- A dedicated cookie builder class is the single point for setting cookie attributes.
- Logout MUST explicitly expire both the auth token cookie and the CSRF cookie by setting max-age to 0.

### AuthContext Usage Rules
- ALWAYS extract identity from the `AuthContext` populated by the auth filter.
- NEVER re-parse token cookies manually inside resources or services.
- NEVER trust user-supplied identity fields from request body for authorization decisions.

### New Endpoint Security Checklist
- [ ] Is this endpoint protected by the auth filter?
- [ ] Is the required role checked before any service call?
- [ ] Is CSRF validated for mutating HTTP methods?
- [ ] Is the response body free of token or secret data?
- [ ] Are all error responses using the standard `ErrorResponse` shape?

---

## 🔌 WebSocket Standards

### Endpoint Style
- Annotate WebSocket endpoints with the appropriate Quarkus WebSocket annotation.
- Keep endpoint classes in the `resource/` package.
- Name classes with `*Socket` suffix.
- NEVER put business logic inside WebSocket handler methods — delegate to service layer.

### Message Contract
- Define incoming and outgoing message types as records — same rule as REST DTOs.
- Serialize and deserialize through Jackson, consistent with the REST layer.
- NEVER send raw strings where a typed message contract can be used.

### Session & Broadcast
- Use single-session send for targeted messages.
- Use broadcast only when all connected clients in the defined scope must receive the event.
- ALWAYS apply scope guards before broadcasting — never send data across tenant or user scope boundaries.

### Error Handling
- NEVER let exceptions propagate silently in message or lifecycle handlers.
- Catch exceptions explicitly and send a typed error message using the same error code
  vocabulary as `ErrorResponse`.
- Log errors at `error` level with session context and `traceId`.

### Security
- Validate authentication credentials on connection open — reject unauthenticated connections
  immediately by closing the session with an appropriate close reason.
- NEVER trust identity data sent from the client in WebSocket message payload.
- Apply the same role-check rules as REST endpoints.

### Testing
- Cover at minimum: successful connection, valid message handling,
  invalid message rejection, unauthenticated connection rejection.

---

## ⚡ Async & Concurrency Standards

### Default Approach
- Keep the service layer synchronous and request-scoped unless a measured bottleneck justifies change.
- NEVER introduce concurrency speculatively.

### Rules If Async Is Needed
- Make the async boundary explicit — never hide it inside a service method with a synchronous signature.
- Add deterministic tests that verify correct behavior under the async execution model.
- Document the reason for introducing async in the relevant task or PR notes.

---

## 🧵 Virtual Threads Adoption Rules

### When To Introduce
- ONLY when a blocking operation (JDBC, file IO, outbound HTTP) is measured to be a throughput
  bottleneck under realistic load.
- NEVER add `@RunOnVirtualThread` speculatively.
- Document the performance justification in PR or task notes before applying.

### Rules If Adopted
- Apply `@RunOnVirtualThread` at resource method level only.
- NEVER mix `@RunOnVirtualThread` with reactive `Uni/Multi` return types on the same method.
- NEVER use `synchronized` blocks on virtual thread paths — use `ReentrantLock`.
- Enable JDK pin detection (`-Djdk.tracePinnedThreads=full`) in dev profile during testing.
- Add a test verifying the endpoint remains functionally correct after the switch.

### Structured Concurrency (Preview)
- Use `StructuredTaskScope.ShutdownOnFailure` for fail-fast fan-out of independent blocking calls
  with a clear shared lifetime.
- NEVER use structured concurrency for fire-and-forget patterns.

---

## 🧠 Caching Standards

### Rules
- Use caching only for stable, read-heavy data where recomputation is measurably expensive.
- ALWAYS define a cache invalidation strategy before introducing a cached method.
- NEVER cache data scoped to a specific user or tenant without including the scope key in the cache key.
- ALWAYS document the expected TTL and the invalidation trigger in a comment above the cached method.
- NEVER cache mutable domain state that must reflect writes immediately.

---

## 🚀 Performance & Resource Optimization

### General Principle
- Write resource-conscious code by default — CPU, memory, and IO efficiency are a baseline requirement,
  not an afterthought.

### Memory
- Prefer streaming and lazy evaluation over materializing full collections when processing large data sets.
- Avoid creating intermediate collections that are used only to produce a final transformed result.
- Use `.toList()` terminal collect only after all filtering and mapping is complete.
- NEVER load unbounded result sets from the database into memory — always paginate.
- Avoid unnecessary object allocation in hot paths — reuse stateless instances where safe.

### CPU
- Declare shared stateless utility instances (e.g., `SecureRandom`, compiled `Pattern`) as
  `private static final` to avoid repeated initialization.
- Avoid redundant computation inside loops — precompute stable values before entering the loop.
- NEVER call repository or service methods inside a stream lambda if the call can be batched
  or hoisted outside the stream.

### IO & Resources
- Always close IO resources (streams, connections, readers) in a try-with-resources block.
- NEVER perform synchronous blocking IO on a thread that is expected to remain responsive.
- Prefer bulk database operations over row-by-row iteration when inserting or updating multiple records.

### Database
- Always paginate queries that can return an unbounded number of rows.
- Declare indexes for foreign key columns and hot-lookup columns in migration files.
- Fetch only the columns needed for the operation — avoid `SELECT *` in named queries.
- NEVER execute N+1 queries — resolve associations through joins or batched lookups.

### Connection Pool
- Configure explicit pool size, idle-removal interval, and acquisition timeout.
- Size the pool to match the expected concurrent request load, not arbitrarily high.

---

## 🩺 Health & Observability Standards

### Built-in Endpoints
- `/q/health` — combined liveness and readiness.
- `/q/health/live` — liveness probe.
- `/q/health/ready` — readiness probe.

### Custom Health Checks
- Implement `HealthCheck` for any critical runtime dependency or required initialization state.
- Annotate with `@Liveness` (process alive, not deadlocked) or `@Readiness` (ready for traffic)
  based on the semantic of the check.
- NEVER perform heavy computation inside a health check — keep probes fast and side-effect-free.
- Database connectivity is automatically covered by the Agroal integration — do not duplicate it.

### Metrics
- No metrics extension is configured currently.
- If `quarkus-micrometer` is added, annotate only service-layer methods — never resource methods directly.

### Distributed Tracing
- Not configured currently.
- If added, propagate `traceparent` header through all outbound HTTP calls and use it
  as the source of `traceId` for MDC.

---

## 🔄 CI/CD Readiness Standards

### Required Local Verification Sequence
Every change must pass this full sequence before being finalized:
```bash
./mvnw validate      # Checkstyle — fails on style violations
./mvnw test          # Unit + integration tests
./mvnw verify        # JaCoCo coverage >= 0.80 + SpotBugs
./mvnw quarkus:build # JVM build smoke — confirms no wiring errors
```

### Environment Variable Contract
- NEVER hardcode secrets or environment-specific values in committed properties files.
- ALWAYS use the placeholder pattern: `${VAR_NAME:safe_local_default}`.
- ALWAYS document every new environment variable in project documentation.

### Container Readiness
- The service must bind to `0.0.0.0` — do not override to `localhost`.
- Health probes must be reachable before any traffic is routed to the container.

### Preview Features in CI
- `--enable-preview` must be present in both `maven-surefire-plugin` and `maven-failsafe-plugin`.
- Any new JVM argument additions must be mirrored in both plugin configurations.

---

## 📋 MASTER RULES FOR AI AGENT

### ⛔ NEVER DO
```text
COMMENTS & DOCS:
  Never write Javadoc on any class or method.
  Never write inline comments that restate what the code obviously does.
  Only write an inline comment when the algorithm is genuinely complex,
  the flow is non-obvious, or a bug fix addresses a subtle hard-to-catch issue.

SUPPRESSION:
  Never add warning suppressions as a first response to quality issues.
  Always fix the root cause.

ARCHITECTURE:
  Never bypass the service layer from resources for business logic.
  Never mix persistence and HTTP response assembly in repository classes.
  Never put business logic inside WebSocket handler methods.

SECURITY:
  Never log secrets, raw tokens, or passwords.
  Never return internal exception details to clients.
  Never trust user-supplied identity fields from request body.
  Never skip auth or CSRF validation on mutating endpoints.

PERSISTENCE:
  Never build SQL or JPQL strings through interpolation or concatenation.
  Never remove transaction boundaries from repository write methods.
  Never edit or rename an existing applied Flyway migration file.
  Never load unbounded result sets into memory.
  Never execute N+1 queries.

API:
  Never return ad-hoc error shapes — use ErrorResponse consistently.
  Never expose internal types or JPA entities in OpenAPI schema.

LOGGING:
  Never log at the wrong level — noise and silence are both harmful.
  Never use string concatenation to build log messages.
  Never log without a traceId in request-scoped operations.
  Never leave stale MDC state between requests.

PERFORMANCE:
  Never materialize large collections when streaming is sufficient.
  Never allocate shared stateless instances inside hot paths.
  Never perform blocking IO without proper resource cleanup.
```

### ✅ ALWAYS DO
```text
JAVA & STYLE:
  Always target Java 25 with preview features enabled.
  Always use records for request/response DTOs unless mutability is required.
  Always use OperationResult (sealed type) for business success/failure branching.

LAYERS:
  Always keep resource → service → repository boundaries clear.
  Always keep Bean Validation at the API boundary.
  Always keep business invariant checks in the service layer.
  Always delegate WebSocket logic to the service layer.

LOGGING:
  Always add proactive, useful log statements at the correct level.
  Always include traceId in every log statement within a request scope.
  Always include the primary domain entity identifier in log messages.
  Always initialize MDC at request entry and clear it at request end.
  Always make logs sufficient to diagnose a production issue without a debugger.

QUALITY:
  Always pass ./mvnw validate and ./mvnw verify before finalizing.
  Always keep JaCoCo coverage above 0.80 for new code.

SECURITY:
  Always extract identity from the AuthContext populated by the auth filter.
  Always validate the required role before invoking service methods.
  Always validate CSRF on mutating endpoints.
  Always reject unauthenticated WebSocket connections at open time.

DATABASE:
  Always use parameterized queries.
  Always add indexes for new foreign key and hot-lookup columns in the migration.
  Always paginate queries that can return unbounded rows.

DOCUMENTATION:
  Always annotate new REST endpoints with @Operation and @APIResponses.
  Always document new environment variables in project documentation.

PERFORMANCE:
  Always prefer streaming over full collection materialization for large data.
  Always declare shared stateless instances as private static final.
  Always close IO resources in try-with-resources.
  Always fetch only the columns needed for the operation.
```

---

## 🔄 PREFER OVER

```text
Records                   over mutable DTO classes with setters
Sealed result type         over ad-hoc boolean + message tuples
Switch expression          over long if-else branching on result types
Constructor DI             over field injection
Panache queries            over raw persistence boilerplate
Explicit mapping           over hidden reflection-based mappers
OperationResult            over control-flow exceptions for business outcomes
ReentrantLock              over synchronized blocks on virtual thread paths
@Readiness / @Liveness     over ad-hoc internal state checks
Env placeholder secrets    over hardcoded values in properties files
Typed WebSocket messages   over raw string frames
Streaming pipeline         over intermediate collection materialization
Static final instances     over repeated allocation of stateless objects
traceId in every log line  over untracked isolated log statements
```

---

## 🧭 Architecture-Specific Rules

### Layer Responsibilities
- Resources are thin: authenticate, authorize, validate input shape, delegate to service, map result to response.
- Services own business logic: validate invariants, orchestrate repositories, return typed results.
- Repositories own persistence: query, persist, and return domain entities only.

### Team Conventions
- Keep endpoints explicit and fully documented with OpenAPI annotations.
- Keep service methods deterministic and return typed result wrappers.
- Keep test coverage split between fast unit tests and Quarkus integration smoke tests.
- Keep WebSocket endpoints thin — all logic belongs in services.
- Keep migration files immutable after first application to any environment.
- Keep new environment variables documented with safe local defaults.
- Write comments only when the algorithm or flow is genuinely non-obvious
  or when a bug fix addresses a subtle hard-to-catch problem.

---
This skill reflects coding style, architectural constraints, and enforceable implementation rules
for Java service development on this stack.
All rules apply to every new feature, endpoint, migration, and test added to the project.
```