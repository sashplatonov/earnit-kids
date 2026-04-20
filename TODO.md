
```
You are an expert software engineer performing a comprehensive global refactoring
of a full-stack application: Java backend + SvelteKit frontend (children's coin shop).

## PROJECT CONTEXT
- Frontend: SvelteKit (migrated from legacy web stack)
- Backend: Java (Spring Boot assumed)
- Purpose: Children's coin shop (kid-friendly UI, simple UX)
- Goal: Production-ready, clean, well-tested, optimized codebase

---

## TASKS TO PERFORM

### 1. FRONTEND CLEANUP — Remove Legacy Web Code
- Identify and DELETE all legacy frontend files predating SvelteKit migration:
  * Old HTML/CSS/JS files not part of SvelteKit structure
  * Webpack/Parcel/Gulp configs replaced by Vite
  * Old template engines (Thymeleaf fragments served as frontend, JSP, etc.)
  * Duplicate static assets (old /public, /static, /resources/templates)
  * Unused npm packages from legacy stack (jQuery, Bootstrap JS if replaced, etc.)
  * Old routing files not using SvelteKit file-based routing
- Ensure ONLY SvelteKit (Vite-based) code remains in frontend
- Verify `src/routes/`, `src/lib/`, `src/app.html` are the single source of truth
- Remove any mixed SSR/CSR hacks from migration period
- Clean up `svelte.config.js` and `vite.config.js` from migration workarounds

### 2. CODE REFACTORING — Frontend (SvelteKit)
- Apply consistent component structure:
  * Smart components in `src/lib/components/`
  * Page components only in `src/routes/`
  * Shared types in `src/lib/types/`
  * API clients in `src/lib/api/`
  * Stores in `src/lib/stores/`
  * Utils in `src/lib/utils/`
- Replace any `any` TypeScript types with proper interfaces
- Ensure all API calls go through centralized fetch wrapper (handle errors, loading states)
- Use SvelteKit `load()` functions properly (server vs client load)
- Remove inline styles — use scoped `<style>` or design tokens (CSS custom properties)
- Ensure accessibility (a11y): aria-labels, keyboard navigation (kid-friendly = large targets)
- Apply consistent error boundaries and loading states across all routes

### 3. CODE REFACTORING — Backend (Java)
- Apply clean architecture / layered architecture strictly:
  * Controller → Service → Repository layers — no business logic in controllers
  * DTOs for all API request/response (never expose entities directly)
  * Mappers (MapStruct preferred) for Entity ↔ DTO conversion
- Remove dead code: unused endpoints, commented-out code blocks, unused imports
- Replace magic numbers/strings with named constants or enums
- Ensure all exceptions are handled via global `@ControllerAdvice` / `@ExceptionHandler`
- Validate all incoming DTOs with `@Valid` + Jakarta Bean Validation annotations
- Use `Optional` properly — no `.get()` without `isPresent()` check
- Ensure proper transaction boundaries `@Transactional` on service methods
- Replace deprecated Spring Boot APIs with current equivalents
- Use `records` for immutable DTOs (Java 16+)
- Ensure all JPA queries are optimized (no N+1 — use `@EntityGraph` or JOIN FETCH)
- Apply consistent logging: SLF4J + structured log levels (no `System.out.println`)

### 4. DOCUMENTATION UPDATE

#### 4a. General / Project README
- Update `README.md` with:
  * Project overview (what it is, who it's for)
  * Tech stack (current, no legacy references)
  * Quick start (Docker Compose one-liner)
  * Project structure tree (frontend + backend)
  * Environment variables reference table
  * Links to detailed docs

#### 4b. Frontend Documentation
- Create/update `frontend/docs/ARCHITECTURE.md`:
  * SvelteKit routing structure explanation
  * State management approach (stores)
  * API integration pattern
  * Component naming conventions
  * Styling approach (CSS variables / design tokens)
- Add JSDoc/TSDoc comments to all exported functions, stores, types
- Document all `$env` variables used

#### 4c. Backend Documentation
- Create/update `backend/docs/ARCHITECTURE.md`:
  * Package structure explanation
  * Layer responsibilities
  * Database schema overview
  * Authentication/authorization approach
  * API versioning strategy
- Add Javadoc to all public service methods and REST controllers
- Ensure OpenAPI/Swagger annotations are complete (`@Operation`, `@ApiResponse`, `@Schema`)
- Auto-generate and commit `openapi.yaml` / Swagger UI accessible at `/api/docs`

#### 4d. Architecture Documentation
- Create `docs/ARCHITECTURE.md` at root:
  * C4 model description (Context → Container → Component)
  * Data flow diagrams (user → frontend → backend → DB)
  * Auth flow diagram
  * Docker networking diagram
  * Decision log (ADR — Architecture Decision Records) for key choices

### 5. TEST COVERAGE — Frontend (minimum 80%)

- Setup: Vitest + @testing-library/svelte + jsdom
- Add `playwright` for E2E critical user flows
- Test targets:
  * All `src/lib/utils/` functions — unit tests (target 100%)
  * All `src/lib/stores/` — unit tests (test state transitions)
  * All `src/lib/api/` — unit tests with mocked fetch
  * All `src/lib/components/` — component tests (render, props, events, slots)
  * All `src/routes/` pages — integration tests (data loading, form submission)
- Coverage config in `vite.config.js`:
  ```js
  test: {
    coverage: {
      provider: 'v8',
      threshold: { lines: 80, functions: 80, branches: 80, statements: 80 }
    }
  }
  ```
- Add `npm run test:coverage` script
- Enforce coverage gate — fail if below 80%

### 6. TEST COVERAGE — Backend (minimum 80%)

- Setup: JUnit 5 + Mockito + AssertJ + Testcontainers
- Test targets:
  * Service layer: unit tests with mocked repositories (Mockito)
  * Repository layer: integration tests with Testcontainers (real DB)
  * Controller layer: `@WebMvcTest` with MockMvc (test HTTP layer in isolation)
  * Integration tests: `@SpringBootTest` for critical flows (purchase flow, auth flow)
  * Validation tests: test all Bean Validation constraints
- JaCoCo config in `build.gradle` / `pom.xml`:
  ```
  minimum coverage: LINE 80%, BRANCH 75%
  fail build if below threshold
  ```
- Test naming convention: `methodName_stateUnderTest_expectedBehavior()`
- Use `@ParameterizedTest` for boundary values (coin amounts, ages, limits)

### 7. DOCKER — Faster Builds

#### Frontend Dockerfile optimization:
```dockerfile
# Use exact Node version (not latest)
FROM node:20-alpine AS deps
WORKDIR /app
# Copy ONLY package files first (layer cache)
COPY package.json package-lock.json ./
RUN npm ci --frozen-lockfile

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/build ./build
COPY --from=builder /app/package.json ./
RUN npm ci --omit=dev --frozen-lockfile
USER node
EXPOSE 3000
CMD ["node", "build"]
```

#### Backend Dockerfile optimization:
```dockerfile
# Use layered JAR (Spring Boot)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
# Copy gradle/maven wrapper + deps first
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon  # cache deps layer

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Extract layered JAR for better caching
FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app
# Copy in dependency order (least → most frequently changed)
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./
USER 1001
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### 8. DOCKER COMPOSE — Best Practices

```yaml
# Use this structure and principles:

services:
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      target: runner
    restart: unless-stopped
    environment:
      - NODE_ENV=production
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M
        reservations:
          cpus: '0.1'
          memory: 128M
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:3000/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 10s
    networks:
      - frontend-net
    depends_on:
      backend:
        condition: service_healthy

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
      target: runner
    restart: unless-stopped
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${DB_NAME}
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          cpus: '0.25'
          memory: 256M
    healthcheck:
      # Use Spring Actuator liveness — lightweight endpoint
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health/liveness"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
    networks:
      - frontend-net
      - backend-net
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:16-alpine  # alpine = smaller footprint
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M
    healthcheck:
      # pg_isready is the lightest possible check — no query, no connection overhead
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 10s
      timeout: 3s
      retries: 5
      start_period: 10s
    networks:
      - backend-net

volumes:
  postgres_data:
    driver: local

networks:
  frontend-net:
    driver: bridge
  backend-net:
    driver: bridge
    internal: true  # DB not accessible from outside

# Healthcheck principles applied:
# - wget instead of curl (smaller image footprint)
# - /actuator/health/liveness NOT /actuator/health (liveness = no DB check = fast)
# - pg_isready instead of psql query (no auth overhead)
# - internal: true on backend-net (security + less routing overhead)
```

### 9. ADDITIONAL REFACTORING ITEMS (beyond CI/CD)

#### 9a. Security Hardening
- Audit and rotate all secrets — move to `.env` (never hardcoded)
- Add `.env.example` with all required vars (no values)
- Enable CORS properly — whitelist specific origins, not `*`
- Add rate limiting on backend (Spring Boot: Bucket4j or Resilience4j)
- Add Content Security Policy headers (SvelteKit hooks)
- Sanitize all user inputs (especially coin names, user-generated content)
- Add OWASP dependency check plugin (Gradle/Maven)
- Enable HTTPS redirect in production profile
- Since it's a kids app: ensure COPPA/GDPR-kids compliance audit

#### 9b. Performance Optimization
- Frontend:
  * Enable SvelteKit preloading (`data-sveltekit-preload-data`)
  * Lazy load heavy components (`{#await import(...)}`)
  * Optimize images: use `<enhanced:img>` or WebP + proper `srcset`
  * Add `<link rel="preconnect">` for API domain
  * Minimize bundle: analyze with `vite-bundle-analyzer`
  * Add service worker for offline support (Workbox via `@vite-pwa/sveltekit`)
- Backend:
  * Add Redis cache layer for catalog/coins data (frequently read, rarely written)
  * Add database indexes audit (explain analyze on slow queries)
  * Enable Spring Boot lazy initialization for faster startup
  * Add response compression (GZIP in Spring Boot)
  * Use pagination on all list endpoints (`Pageable`)

#### 9c. Code Quality Gates
- Add ESLint + Prettier (frontend) with strict rules:
  * `eslint-plugin-svelte`
  * `eslint-plugin-jsx-a11y` equivalent for Svelte
  * Strict TypeScript: `"strict": true` in tsconfig
- Add Checkstyle + SpotBugs + PMD (backend)
- Add SonarLint local config (`.sonarcloud.properties`)
- Add `EditorConfig` (`.editorconfig`) for cross-IDE consistency
- Add `lefthook` or `husky` for pre-commit hooks:
  * lint + format on commit
  * type-check on commit
  * unit tests on push

#### 9d. Database / Data Layer
- Audit all Liquibase/Flyway migrations — ensure idempotent, no gaps
- Add missing database constraints (NOT NULL, UNIQUE, FK)
- Audit indexes — add composite indexes for common query patterns
- Add soft delete pattern where data retention matters
- Ensure connection pool is tuned (HikariCP settings in application.yml)
- Add read replica routing if applicable

#### 9e. Observability
- Add structured JSON logging (Logback JSON encoder)
- Add correlation ID / trace ID to all requests (MDC filter)
- Expose proper Spring Actuator endpoints (health, info, metrics, prometheus)
- Add Micrometer metrics for business events (coin purchased, user registered)
- Frontend: add error tracking (Sentry lightweight setup)
- Add `/health` endpoint to SvelteKit app (for Docker healthcheck)

#### 9f. Developer Experience (DX)
- Add `Makefile` with common commands:
  * `make dev` — start all services in dev mode
  * `make test` — run all tests
  * `make lint` — run all linters
  * `make build` — build all Docker images
  * `make clean` — clean build artifacts
- Add `.vscode/extensions.json` and `.vscode/settings.json`
- Add IntelliJ run configurations (`.idea/runConfigurations/`)
- Add `docker-compose.dev.yml` (override for hot reload, debug ports)
- Update `.gitignore` comprehensively

#### 9g. Dependency Audit & Update
- Run `npm audit fix` — fix all vulnerabilities
- Update all npm dependencies to latest compatible versions
- Run `./gradlew dependencyUpdates` — identify outdated Java deps
- Remove unused dependencies (both frontend and backend)
- Pin all Docker base image versions (no `latest` tags)
- Check for license compatibility of all dependencies

#### 9h. API Design Cleanup
- Ensure consistent REST API design:
  * Plural nouns: `/api/v1/coins`, `/api/v1/users`
  * HTTP verbs used correctly (GET/POST/PUT/PATCH/DELETE)
  * Consistent error response format: `{error, message, timestamp, path}`
  * Consistent success response format or HTTP status codes
  * API versioning: `/api/v1/` prefix on all endpoints
- Remove any duplicate or overlapping endpoints
- Add missing HTTP status codes (201 for create, 204 for delete)

---

## EXECUTION APPROACH

Work through tasks in this priority order:
1. Legacy cleanup (unblock everything else)
2. Docker optimization (speed up iteration)
3. Code refactoring (backend → frontend)
4. Documentation
5. Tests (write as you refactor each module)
6. Additional items (security → performance → DX)

For each file modified:
- State WHAT you changed and WHY
- Flag any breaking changes
- Note if manual migration steps are needed

When unsure about business logic, ASK before refactoring.
Never delete code without confirming it's truly unused (check all import references).
Preserve all existing API contracts unless explicitly told to break them.
```