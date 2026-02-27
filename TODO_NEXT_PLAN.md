## Техническая модернизация EarnIt Kids (9–12 месяцев, Big-Bang)

### Краткое резюме

Цель: радикально повысить скорость разработки и технологическую зрелость через переход на TypeScript + Fastify + React/Vite, монорепо и современный релизный контур.
Подход: подготовка нового стека параллельно, затем один контролируемый big-bang cutover с коротким freeze-окном и rollback-планом.

### Целевое состояние (Target Architecture)

- Monorepo на pnpm + Turborepo.
- apps/api: Fastify + TypeScript, плагинная модульная архитектура, versioned API (/api/v1).
- apps/web: React + Vite + TypeScript, feature-based структура, TanStack Query для server-state.
- packages/contracts: общие Zod-схемы и TS-типы запросов/ответов.
- packages/sdk: типобезопасный API-клиент для веба.
- packages/eslint-config, packages/tsconfig, packages/ui (минимальный shared UI-kit).
- БД: PostgreSQL (managed), миграции через drizzle-kit (или Prisma Migrate; в этом плане фиксируем Drizzle).
- Инфраструктура: Docker, Render/Fly (app + worker), managed Postgres, object storage для бэкапов.
- Наблюдаемость: OpenTelemetry, Sentry, structured logs + request tracing + dashboards.

### Этапы реализации (Decision-Complete Roadmap)

#### Этап 0: Подготовка программы миграции (2 недели)

- Назначить freeze-политику: только критические фиксы в старом приложении после старта Этапа 2.
- Зафиксировать архитектурные ADR:
    - ADR-001: Fastify + TS.
    - ADR-002: React + Vite + TS.
    - ADR-003: Monorepo + pnpm + Turborepo.
    - ADR-004: Drizzle ORM + SQL-first миграции.
- Определить DOR/DOD для каждого домена: auth, tasks, shop, requests, history, super-admin.
- Подготовить cutover checklist и rollback checklist.

#### Этап 1: Фундамент платформы (4–6 недель)

- Инициализировать монорепо:
    - apps/api, apps/web, packages/contracts, packages/sdk, packages/config-*.
- Внедрить общие стандарты:
    - TS strict mode (noImplicitAny, exactOptionalPropertyTypes, noUncheckedIndexedAccess).
    - ESLint + Prettier + commit hooks (lint-staged, commitlint).
- CI/CD (GitHub Actions):
    - typecheck, lint, unit, integration, build, e2e.
    - preview deploy для PR.
- Базовая observability-подсистема:
    - correlation id, HTTP metrics (p95/p99), error tracking.
- Security baseline:
    - secret scanning, dependency scanning, SAST, CSP policy template.

#### Этап 2: Новый API (Fastify) и контрактная модель (8–10 недель)

- Поднять apps/api с доменными модулями:
    - auth, children, tasks, shop, requests, history, super-admin.
- Ввести contracts-first:
    - все DTO и схемы в packages/contracts на Zod.
    - runtime validation на входе/выходе API.
- Реализовать auth модель:
    - JWT/refresh rotation, CSRF (double-submit token), secure cookies.
    - magic link flow с безопасным токен-хранилищем (исправляет текущий TODO-подход).
- Репозитории через Drizzle с транзакциями и четкими boundary-интерфейсами.
- Версионирование API:
    - новые endpoint’ы только в /api/v1.
    - единый формат ошибок: { code, message, details?, requestId }.

#### Этап 3: Новый Web-клиент (React/Vite) (8–10 недель)

- Реализовать shell приложения:
    - role-based routing (admin/child/super-admin), защищенные роуты.
- Реализовать домены по приоритету:
    - auth → tasks → shop → requests → history → analytics → settings.
- Использовать packages/sdk для всех API-вызовов.
- Мигрировать mobile shell:
    - Capacitor подключается к новому web build (apps/web/dist), без нативного переписывания.
- Feature flags:
    - флаги для включения новых разделов на pre-prod и staging.

#### Этап 4: Big-Bang Cutover (1 неделя + 1 неделя стабилизации)

- Freeze legacy (кроме P0 багов).
- Финальная миграция данных и проверка консистентности.
- Switch DNS/route на новый стек.
- Наблюдение 24/7 первые 72 часа:
    - error rate, auth failures, purchase flow success, p95 latency.
- Rollback window:
    - мгновенный возврат на legacy через обратный switch маршрутизации и бэкап БД read-only snapshot.

#### Этап 5: Пост-миграционная оптимизация (4–6 недель)

- Удаление legacy-кода и скриптов.
- Оптимизация CI (parallel matrix, test sharding).
- Производительность:
    - API caching, bundle splitting, image strategy.
- Документация:
    - runbooks, onboarding, incident playbook, архитектурная карта v2.

### Изменения публичных интерфейсов/API

- Новый базовый префикс API: /api/v1/*.
- Унифицированные error responses:
    - code: машинный код ошибки.
    - message: user-safe текст.
    - requestId: для трассировки.
- Контракт child purchase:
    - фиксированный payload requestType: "shop_purchase", itemId, coins, moneyAmount? через Zod-схему.
- Auth endpoints:
    - refresh/logout/session introspection в отдельном auth-модуле.
- Deprecated legacy routes:
    - старые /api/* объявляются deprecated на период стабилизации (до полного удаления).

### Модель данных и миграции

- Перевод текущих SQL-скриптов в управляемые миграции Drizzle.
- Ввод таблиц/индексов для:
    - refresh sessions,
    - magic-link tokens (hash + ttl + consumed_at),
    - audit событий безопасности.
- Доработка индексов под hot-path:
    - requests by child_id,status,created_at,
    - history by child_id,created_at,
    - shop usage frequency queries.
- Политика миграций:
    - backward-compatible schema changes до cutover,
    - destructive changes только после стабилизации.

### Тестовая стратегия и сценарии приемки

- Unit:
    - сервисы доменов, валидация схем, auth helpers.
- Integration:
    - API + Postgres (testcontainers), транзакции, role-based access.
- Contract tests:
    - проверка apps/api против packages/contracts.
- E2E (Playwright):
    - login/admin/child/super-admin,
    - add/edit/delete shop item,
    - child request -> admin approve/reject,
    - frequency/money limits,
    - history consistency.
- Non-functional:
    - нагрузочный smoke (k6): auth, requests, history read paths.
    - security checks: CSRF, cookie flags, auth token invalidation.
- Acceptance критерии:
    - p95 API < 250ms для ключевых read endpoint’ов.
    - 0 критических регрессий в checkout/request flow.
    - CI green на main, coverage не ниже текущего baseline.

### DevOps и релизный контур

- Environments: dev, staging, prod.
- PR previews: автоматический deploy web+api.
- Release strategy:
    - semver tags, changelog generation, migration gate.
- SLO/alerts:
    - availability, error budget, auth failure spikes, DB saturation.

### Риски и меры снижения

- Big-bang риск регрессий:
    - mitigation: строгий freeze, полная e2e матрица, rollback-план.
- Потеря совместимости mobile shell:
    - mitigation: отдельный pre-release мобильный smoke на staging URL.
- Срыв сроков из-за объема:
    - mitigation: доменная декомпозиция и stage-gate критерии перед переходом к следующему этапу.

### Ресурсный план (ориентир)

- Команда: 3–5 инженеров (2 backend/fullstack, 1–2 frontend, 0.5 DevOps).
- Спринты: 2 недели.
- Контрольные точки:
    - M1: завершен платформенный фундамент.
    - M3: API v1 feature-complete.
    - M5: web v2 feature-complete.
    - M6: cutover + стабилизация.

### Явные допущения и выбранные по умолчанию решения

- Горизонт: 9–12 месяцев.
- Приоритет: скорость разработки.
- Риск-профиль: допускается радикальная миграция.
- Backend: Fastify + TypeScript.
- Frontend: React + Vite + TypeScript.
- Стратегия миграции: Big-Bang.
- Репозиторий: Monorepo (apps/*, packages/*).
- Инфраструктура: Docker + Render/Fly + managed Postgres.
- ORM/миграции: Drizzle (фиксировано в рамках этого плана).