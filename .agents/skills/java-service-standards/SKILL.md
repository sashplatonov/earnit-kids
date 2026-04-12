---
name: java-service-standards
description: Java service development standards inferred from the current EarnIt Kids backend codebase and enforced tooling.
---

# ☕ MY JAVA DEVELOPMENT SKILL

> This skill defines coding standards, architecture patterns, and implementation rules
> for Java services based on the current project code.
> AI agents must follow these rules when implementing new Java backend features.

## Table of Contents
- Tech Stack Reference
- Java 25 Standards
- Project Structure
- Code Quality & Linting
- Formatting Standards
- Builder Pattern Standards
- Mapping Standards
- Database & Persistence Standards
- REST API Standards
- Service Layer Standards
- Exception Handling Standards
- Lombok Usage Standards
- Testing Standards
- Configuration Standards
- Logging Standards
- Security Standards
- Async & Concurrency Standards
- Caching Standards
- Performance & Resource Optimization
- Master Rules For AI Agent
- Prefer Over
- Architecture-Specific Rules

## 🔧 Tech Stack Reference

### Core Platform
| Component | Version Found | Target |
|-----------|---------------|--------|
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
- `quarkus-maven-plugin` drives build + code generation.
- `maven-compiler-plugin`:
  - `release=25`
  - `--enable-preview`
  - annotation processors: Lombok + MapStruct
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

## ☕ Java 25 Standards

### Target Language Level
- Current codebase targets Java 25 and compiles with preview features enabled.
- New code should stay Java-25-first and use modern language constructs when they improve readability.

### Features Actively Used in Current Code

#### Records (widely used for DTOs and response models)
```java
public record CreateChildRequest(
    @NotBlank(message = "Child name is required")
    @Size(max = 50, message = "Child name must be at most 50 characters")
    String name
) { }
```

#### Sealed Types + Records (result modeling)
```java
public sealed interface OperationResult<T>
    permits OperationResult.Success, OperationResult.Failure {

    record Success<T>(T value) implements OperationResult<T> { }
    record Failure<T>(String errorCode, String message) implements OperationResult<T> { }
}
```

#### Switch Expressions with Type Patterns
```java
return switch (result) {
    case OperationResult.Success<AuthPayload> s -> Response.ok(s.value()).build();
    case OperationResult.Failure<AuthPayload> f ->
        Response.status(Response.Status.UNAUTHORIZED)
            .entity(ErrorResponse.of(f.message(), "AUTHENTICATION_FAILED", 401))
            .build();
};
```

#### `var` for obvious local inference
```java
var auth = getAuthOrFail(ctx);
var cookies = cookieBuilder.buildAuthCookies(...);
```

#### Stream API + collector pipelines
```java
List<TaskDto> tasks = familyDataRepository.getTasks(activeChild.getId()).stream()
    .map(t -> new TaskDto(t.getTaskId(), t.getName(), t.getCoins(), t.getGroupName(),
        t.getFrequency(), t.getComment(), t.getMoneyLimit(), t.getChildId()))
    .toList();
```

#### Sequenced collections (`getFirst`) in Java 25
```java
ChildEntity activeChild = children.getFirst();
```

### Features Not Currently Used (Allowed For New Code Where Appropriate)
- Virtual threads
- Structured concurrency
- Text blocks
- Stream gatherers
- Unnamed variables (`_`)

### Code Style Maturity Level
- Mature modern Java style: records, sealed types, switch expressions, streams, and explicit domain modeling.
- Pattern is pragmatic: advanced features are adopted where they directly improve clarity.

## 📁 Project Structure

### Package Layout
```text
backend/src/main/java/com/sashplatonov/earnit/kids/
  config/
  domain/model/
  dto/request/
  dto/response/
  exception/
  repository/
  resource/
  service/
  util/

backend/src/test/java/com/sashplatonov/earnit/kids/
  config/
  domain/model/
  exception/
  repository/
  resource/
  service/
  support/
```

### File Organization Rules (Observed)
- Primary convention: one public type per file.
- Naming is strongly layer-based (`*Resource`, `*Service`, `*ServiceImpl`, `*Repository`, `*Entity`, `*Request`, `*Response`, `*Dto`).
- Nested records/interfaces are used in a few focused places:
  - `OperationResult.Success/Failure`
  - `AnalyticsResponse.*`
  - `AppConfig.*`

### Naming Conventions
```text
Controllers/Resources:   AuthResource, FamilyResource
Services:                AuthService, AuthServiceImpl, FamilyService, FamilyServiceImpl
Repositories:            ChildRepository, FamilyDataRepository
Entities:                FamilyEntity, ChildEntity, TaskEntity
Request DTOs:            CreateChildRequest, UpdateThemeRequest
Response DTOs:           ErrorResponse, FamilyDataResponse, TokenResponse
Tests:                   AuthResourceTest, FamilyServiceImplTest, RepositorySmokeTest
```

## 🔍 Code Quality & Linting

### Tools Configured
```text
Checkstyle:
- backend/config/checkstyle.xml
- runs in Maven validate phase

SpotBugs:
- backend/config/spotbugs-exclude.xml
- runs in Maven verify phase

JaCoCo:
- configured in pom.xml
- checks bundle line coverage >= 0.80 in verify

PMD:
- pmd.version property exists in pom.xml
- no active PMD plugin/ruleset file found in this module

SonarQube:
- no Sonar plugin/config found in this module
```

### Checkstyle Rules In Effect
```text
- max line length: 120
- newline at end of file required
- tabs forbidden
- forbid Javadoc comments
- forbid block comments /* ... */
- inline comments allowed only with EXPLAIN:/FIXME-like markers
- naming checks (TypeName, MethodName, etc.)
- import hygiene (no *, no unused/redundant imports)
- braces and structure checks
- method length max: 60
- parameter count max: 10
- IllegalCatch forbids catching Throwable
```

### SpotBugs Configuration
```text
- excludes generated code patterns
- suppresses selected false positives for Resource/Filter unread fields
- suppresses EI_EXPOSE_REP/EI_EXPOSE_REP2
- suppresses SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE
```

### Integration
```text
./mvnw validate   -> Checkstyle
./mvnw test       -> Unit + integration tests configured for test phase
./mvnw verify     -> JaCoCo check + SpotBugs check (+ all previous phases)
```

## 🎨 Formatting Standards

Extracted from codebase and quality config:

```text
indent_style             = spaces (observed)
indent_size              = 4 (observed)
max_line_length          = 120 (checkstyle)
charset                  = UTF-8 (pom.xml)
end_of_line              = lf (observed in repo)
insert_final_newline     = true (checkstyle)
trim_trailing_whitespace = not explicitly configured
.editorconfig            = not found in project sources
```

Import ordering:
```text
- No explicit custom import-order rule in Checkstyle.
- Observed style keeps regular imports grouped by package domain.
- Static imports are mostly used in tests and placed at the bottom.
```

## 🏗️ Builder Pattern Standards

### Rule (Observed)
- Entities and persistence models commonly use Lombok `@Builder`.
- Immutable API DTOs are records and usually do not use builders.

### Observed Entity Pattern
```java
@Entity
@Table(name = "children")
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Builder.Default
    private int balance = 0;
}

ChildEntity entity = ChildEntity.builder()
    .familyDbId(familyDbId)
    .name(name)
    .build();
```

### Builder Rules
- ALWAYS use `@Builder` for mutable JPA entities with several optional/default fields.
- ALWAYS use `@Builder.Default` for stable defaults (`balance`, `theme`, timestamps, flags).
- PREFER records for immutable request/response contracts.
- NEVER expose unnecessary mutable construction pathways in service code when a builder already exists.

## 🗺️ Mapping Standards

### Current Project Reality
- MapStruct dependency is configured, but no mapper interfaces are present in `src/main/java`.
- Mapping is currently explicit in services/resources via direct record constructors.

### Observed Mapping Example
```java
List<FriendDto> friends = familyDataRepository.getFriendChildIds(activeChild.getId()).stream()
    .map(fid -> childRepository.findByIdOptional(fid).orElse(null))
    .filter(java.util.Objects::nonNull)
    .map(f -> new FriendDto(f.getId(), f.getName(), f.getBalance()))
    .toList();
```

### Mapping Rules For This Codebase
- KEEP mapping explicit and local when transformations are short and readable.
- INTRODUCE MapStruct only when mapping logic becomes repetitive across multiple services.
- NEVER hide business rules inside opaque mapping utilities.

## 🗄️ Database & Persistence Standards

### Persistence Stack
- Hibernate ORM + Panache repositories.
- PostgreSQL in runtime.
- H2 in test profile (PostgreSQL mode).
- Flyway migrations for schema lifecycle.

### Entity & Repository Style
- Entities use `@Entity`, `@Table`, explicit `@Column` names.
- Repositories extend `PanacheRepositoryBase<Entity, IdType>`.
- Query style uses Panache positional parameters (`?1`, `?2`, ...).
- Transaction boundaries are mostly at repository method level (`@Transactional`).

### Migration Tooling & Structure
```text
Main migration:
- backend/src/main/resources/db/migration/V1__initial_schema.sql

Test migration:
- backend/src/test/resources/db/migration/V1__initial_schema.sql
```

### Transaction Management (Observed)
- Writes are annotated with `@Transactional` in repositories.
- Service methods are mostly orchestration/business logic without transaction annotations.

### Fetch Strategy
- Domain model mostly uses scalar foreign keys (`familyId`, `childId`) rather than heavy JPA relation graphs.
- This keeps fetching explicit in repository queries.

### Connection Pool Configuration (Agroal)
```properties
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.idle-removal-interval=30S
quarkus.datasource.jdbc.acquisition-timeout=5S
```

## 🌐 REST API Standards

### Controller Style
- Jakarta REST (`@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`).
- Class-level base path is `/api`.
- Produces/consumes JSON explicitly.
- `Response` objects are built explicitly for each branch.

### OpenAPI Usage
- MicroProfile OpenAPI annotations are actively used:
  - `@Operation`
  - `@APIResponse` / `@APIResponses`
  - `@RequestBody`
  - `@Tag`

### Error Handling Style
- Error payload is standardized with `ErrorResponse` (RFC-7807-like shape):
  - `type`, `title`, `status`, `detail`, `errorCode`.

### API Versioning
- No URI version prefix (`/v1`) currently used.
- Compatibility aliases exist in some resource methods (POST/PUT equivalents).

### HTTP Client Style
- No internal outbound HTTP client abstraction observed in sampled backend code.

## 🧠 Service Layer Standards

### Service Design
- Mix of interface+implementation pattern (`AuthService` + `AuthServiceImpl`, `FamilyService` + `FamilyServiceImpl`).
- Some services are concrete class-only helpers (`BaseDataService`).

### Dependency Injection
- Constructor injection preferred.
- Two common idioms:
  - explicit `@Inject` constructor
  - Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`

### Result Modeling
- Business methods frequently return `OperationResult<T>` to avoid exception-driven control flow.

### Business Logic Organization
- Resources perform auth/role checks and request mapping.
- Services perform domain validations and orchestration.
- Repositories perform persistence operations.

## ❌ Exception Handling Standards

### Current Pattern
- No deep custom exception hierarchy observed.
- Exception-to-response mapping is centralized via JAX-RS mappers.

### Observed Mappers
- `ConstraintViolationExceptionMapper` -> HTTP 400 with aggregated validation details.
- `GlobalExceptionMapper` -> HTTP 500 with `INTERNAL_ERROR` payload and error log.

### Rule
- Prefer controlled domain failures (`OperationResult.Failure`) for expected business outcomes.
- Use exception mappers for unexpected/system-level failures.

## 🧩 Lombok Usage Standards

### Lombok Annotations In Active Use
- `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- `@Slf4j`
- `@Getter`
- `@Setter`
- `@Builder`
- `@Builder.Default`
- `@AllArgsConstructor(access = AccessLevel.PACKAGE)`
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`

### Lombok Configuration
- `lombok.config` file is not present in this backend module.
- Lombok is configured via Maven dependency + annotation processor path.

### Builder Pattern Usage Observed
- Strongly used in persistence entities and in repository create/upsert flows.
- Not used for record DTOs.

### Lombok Rules
- KEEP Lombok where it removes boilerplate without hiding business behavior.
- AVOID `@Data` on entities (prefer explicit field-level intent via `@Getter/@Setter`).
- Keep constructor injection explicit and consistent with Quarkus CDI.

## 🧪 Testing Standards

### Frameworks Observed
- JUnit 5
- Mockito (`MockitoExtension`, mocks, stubs)
- AssertJ assertions
- RestAssured for HTTP-level integration checks
- Quarkus test runtime (`@QuarkusTest`)

### Test Class Naming
- `*Test` suffix consistently used.
- Layer-mirrored test packages (`resource`, `service`, `repository`, `config`, `exception`).

### Unit Test Style
- Arrange / Act / Assert structure with explicit setup in each test.
- Service/resource unit tests typically use `@ExtendWith(MockitoExtension.class)`.

### Integration Test Style
- `@QuarkusTest` + RestAssured request assertions.

### Example (from project)
```java
@QuarkusTest
class SessionPageDataResourceTest {
    @Test
    void session_missingCookies_returnsUnauthenticatedSnapshot() {
        given()
            .when()
            .get("/api/page-data/session")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(false));
    }
}
```

### Testcontainers / Slices
- No Testcontainers usage found.
- No Spring test slices (project is Quarkus).

## ⚙️ Configuration Standards

### Configuration Files Found
- `backend/src/main/resources/application.properties`
- `backend/src/test/resources/application.properties`

### `application.properties` Structure
- HTTP & CORS
- JWT secret compatibility
- datasource (PostgreSQL + Agroal)
- Flyway behavior
- Hibernate generation mode
- app feature toggles
- health endpoint

### Typed Configuration
- `@ConfigMapping(prefix = "app")` used in `AppConfig`.
- Nested config interfaces model grouped settings:
  - `SuperAdmin`
  - `EmailVerification`
  - `PasswordRecovery`

### Profile Strategy
- Separate test profile file under test resources (H2 + Flyway clean-at-start).

### Secrets Handling
- Runtime secrets are injected through env placeholders:
  - `${JWT_SECRET:...}`
  - `${DB_PASSWORD:...}`
  - `${SUPER_ADMIN_PASSWORD:...}`

### CI Workflows
- No `.github/workflows/*.yml` files found in this workspace.

## 🪵 Logging Standards

### Logging Framework
- SLF4J via Lombok `@Slf4j`.
- Quarkus logging manager is configured in test JVM properties.

### Logging Conventions Observed
- `log.debug(...)` for operational debug traces.
- `log.warn(...)` for recoverable fallback behavior.
- `log.error(...)` for unhandled exceptions.

### What Is Logged
- Security-sensitive flows avoid dumping secrets/tokens in logs.
- Global exception mapper logs throwable stack traces.

### MDC
- No MDC usage found in sampled code.

## 🔒 Security Standards

### Auth Mechanism
- Custom JWT-in-cookie mechanism (`app_auth`).
- `AuthFilter` parses cookie, verifies token, and stores `AuthContext` in request context.
- Role model includes `admin`, `child`, `super_admin`.

### CSRF Model
- `csrf_token` cookie propagated and validated through context.

### Security Headers
- `SecurityHeadersFilter` sets:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `X-XSS-Protection`
  - `Referrer-Policy`
  - `Cross-Origin-Resource-Policy`
  - `Strict-Transport-Security`

### Spring Security
- Not applicable (Quarkus/Jakarta stack).

## ⚡ Async & Concurrency Standards

### Observed State
- No `@Async`, `CompletableFuture`, reactive `Uni/Multi`, or virtual-thread executors found.
- Current service layer is synchronous and request-scoped through REST calls.

### Rule
- Keep synchronous flow unless there is a measured bottleneck that justifies concurrency.
- If asynchronous processing is introduced, keep boundary explicit and add deterministic tests.

## 🧠 Caching Standards

### Observed State
- `quarkus-cache` dependency is present.
- No `@CacheResult` / `@CacheInvalidate` usage found in sampled code.
- `BaseDataService` keeps in-memory initialized snapshot (`baseData`) for static catalog data.

### Rule
- Use caching only for stable read-heavy data.
- Define cache invalidation strategy before introducing cache annotations.

## 🚀 Performance & Resource Optimization

### Memory
```java
// Stream mapping for DTO projection (observed pattern)
List<FriendDto> friends = familyDataRepository.getFriendChildIds(activeChild.getId()).stream()
    .map(fid -> childRepository.findByIdOptional(fid).orElse(null))
    .filter(java.util.Objects::nonNull)
    .map(f -> new FriendDto(f.getId(), f.getName(), f.getBalance()))
    .toList();
```

### CPU
```java
// Reusable secure random instance (observed)
private static final SecureRandom SECURE_RANDOM = new SecureRandom();
```

### Disk / IO
```java
// Safe resource handling (observed)
try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("baseData.json")) {
    ...
}
```

### Database
- Pagination is used for history/requests via `limit + offset` and Panache range.
- Indexes are declared in migration for hot lookup paths.

### Connection Pool
```properties
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.idle-removal-interval=30S
quarkus.datasource.jdbc.acquisition-timeout=5S
```

## 📋 MASTER RULES FOR AI AGENT

### ⛔ NEVER DO
```text
SUPPRESSION:
  Never add warning suppressions as first response to quality issues.
  Fix root cause whenever feasible.

ARCHITECTURE:
  Never bypass service layer from resources for business logic.
  Never mix persistence and HTTP response assembly in repository classes.

SECURITY:
  Never log secrets, raw JWTs, reset tokens, or passwords.
  Never return internal exception details to clients.

PERSISTENCE:
  Never build SQL/JPQL with unsafe string interpolation.
  Never remove transaction boundaries from repository write methods.

API:
  Never return ad-hoc error shapes; use ErrorResponse consistently.
```

### ✅ ALWAYS DO
```text
JAVA & STYLE:
  Always target Java 25 and keep preview compatibility with current build setup.
  Always use records for request/response DTOs unless mutability is required.
  Always use OperationResult for expected business failure/success branching.

LAYERS:
  Always keep resource -> service -> repository boundaries clear.
  Always keep validation close to API boundary and business invariants in services.

QUALITY:
  Always pass ./mvnw validate and ./mvnw test before finalizing.
  Always keep checkstyle policy in mind (comments/imports/line length).

SECURITY:
  Always rely on AuthContext extracted by AuthFilter for role decisions.
  Always keep CSRF and auth-cookie behavior aligned.

DATABASE:
  Always preserve migration consistency in Flyway scripts.
  Always use parameterized repository queries.
```

## 🔄 PREFER OVER

```text
Records            over mutable DTO classes with setters
Sealed result type over ad-hoc boolean + message tuples
Switch expression  over long if-else branching on result types
Constructor DI     over field injection
Panache queries    over raw persistence boilerplate
Explicit mapping   over hidden reflection-based mappers
OperationResult    over control-flow exceptions for business outcomes
```

## 🧭 Architecture-Specific Rules

### Extracted Domain Rules
- Family context and child context drive most read/write operations.
- Role-aware authorization paths:
  - admin endpoints
  - child endpoints
  - super admin auth branch
- API payloads and persistence payloads are intentionally decoupled.

### Team Conventions Observed In Code
- Keep endpoints explicit and documented with OpenAPI annotations.
- Keep service methods deterministic and return typed result wrappers.
- Keep test coverage split between fast unit tests and Quarkus integration smoke tests.

---
Generated from project analysis of the current workspace (`backend/`): build config, quality config, runtime config, domain code, resources, repositories, services, and tests.
This skill reflects observed coding style plus enforceable implementation rules for future Java service work.
