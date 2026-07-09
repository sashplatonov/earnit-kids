# Бэклог backend: архитектура, наблюдаемость и метрики

## Цель

Собрать единый backend-focused backlog для `apps/backend`, который закрывает пять направлений:

1. улучшение структуры проекта;
2. оптимизация скорости и RAM;
3. оптимизация работы с БД;
4. улучшение observability;
5. добавление метрик для New Relic dashboard.

Документ опирается на текущее состояние репозитория и дополняет уже существующий [docs/performance-optimization-backlog.md](docs/performance-optimization-backlog.md), а не дублирует его.

## Текущее состояние

- Самые крупные backend-классы уже видны как архитектурные hotspots:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/AuthResource.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/SuperAdminResource.java`
- В проекте уже есть базовые элементы observability:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/TraceFilter.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/HttpRequestMetricsRegistry.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SystemDashboardService.java`
- New Relic path уже частично заведен через JVM agent и документацию:
  - `apps/backend/Dockerfile.jvm`
  - `apps/backend/ops/newrelic/newrelic.yml`
  - `.env.example`
  - `docs/monitoring/newrelic.md`
- При этом backend пока не использует стандартный metrics pipeline Quarkus/Micrometer и держит HTTP-метрики в ad-hoc in-memory registry.

## Базовые правила выполнения

- Сохранять слой `resource -> service -> repository`.
- Не расширять публичный API без явной необходимости.
- Все новые env vars отражать минимум в `.env.example`, `apps/backend/.env.example`, `docker-compose.yml`, `docker-compose.native.yml`, `docs/monitoring/newrelic.md`.
- Для backend-изменений реальным gate считать:

```bash
cd apps/backend
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw verify
./mvnw quarkus:build
```

## Приоритеты

- `P0` - сначала: даёт архитектурную развязку или снижает основной runtime-риск.
- `P1` - сразу после `P0`: закрепляет структуру, убирает заметные накладные расходы.
- `P2` - полезное усиление после стабилизации основных потоков.

## 1. Улучшение структуры проекта

### ARC-01. `P0` Разделить read-side family dashboard на отдельные сервисы

- Архитектурное решение:
  - выделить из `FamilyServiceImpl` отдельные роли:
    - `FamilySnapshotService` для orchestration snapshot;
    - `FamilyHistoryQueryService` для history/read-model;
    - `FamilyAnalyticsService` для analytics/read-model;
    - `FamilyDtoAssembler` или набор локальных mapper-компонентов без бизнес-логики.
  - `FamilyService` оставить фасадом use-case уровня, а не местом для всех read-path деталей.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyService.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TaskRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ShopItemRepository.java`
- Критерии проверки:
  - `FamilyServiceImpl.java` перестает быть центральным местом для history, requests, analytics и DTO enrichment одновременно.
  - read-path тесты в `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/FamilyServiceImplTest.java` либо остаются зелёными, либо переезжают в более узкие test classes.
  - `./mvnw verify` проходит без падения покрытия и Checkstyle.

### ARC-02. `P0` Убрать god-repository `FamilyDataRepository`

- Статус: выполнено. Runtime code переведён на узкие репозитории, `FamilyDataRepository` удалён.
- Архитектурное решение:
  - оставить repository per aggregate/query purpose, а не один класс, который пишет и читает tasks, shop items, history, requests и friends.
  - `FamilyDataRepository` разрезать на узкие query/command repositories или полностью убрать после переноса методов в существующие `TaskRepository`, `ShopItemRepository`, `HistoryRepository`, `PurchaseRequestRepository`, `FriendRepository`.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyDataRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TaskRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ShopItemRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FriendRepository.java`
- Критерии проверки:
  - repository-классы больше не смешивают unrelated aggregates.
  - write-методы остаются под явными `@Transactional`.
  - smoke/regression тесты repository слоя проходят.

### API-03. `P1` Разрезать крупные resource-классы по bounded API surfaces

- Архитектурное решение:
  - `FamilyResource` разделить на child-scoped/read/write endpoints с общей auth/scope проверкой через helper/service, а не через один длинный resource.
  - `SuperAdminResource` разделить минимум на `SystemDashboardResource`, `CatalogAdminResource`, `BackupAdminResource`.
  - `AuthResource` разрезать по login/recovery/google/child-link flows только если это не ломает текущий URL contract.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/SuperAdminResource.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/AuthResource.java`
  - `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/FamilyResourceTest.java`
  - `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/SuperAdminResourceTest.java`
  - `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/AuthResourceTest.java`
- Критерии проверки:
  - публичные пути `/api/*` не меняются без отдельного ADR.
  - resource-классы становятся thin и не содержат длинных ветвлений бизнес-логики.
  - OpenAPI и resource tests остаются зелёными.

## 2. Оптимизация работы сервиса по скорости и RAM

### PERF-04. `P0` Декомпозировать family snapshot на shell и section-scoped payloads

- Архитектурное решение:
  - backend должен поддерживать не один тяжёлый `FamilyDataResponse` для всех UI-сценариев, а отдельные read-model paths:
    - shell/session data;
    - child board data;
    - paginated history;
    - paginated requests;
    - analytics.
  - это должно продолжать линию из `docs/performance-optimization-backlog.md`, но уже как backend contract refactor.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/FamilyDataResponse.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/PaginatedHistory.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/PaginatedRequests.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
  - `docs/performance-optimization-backlog.md`
- Критерии проверки:
  - первый dashboard read больше не тянет history/requests/analytics без явного запроса.
  - pagination endpoints обслуживаются без materialization полного family snapshot.
  - build и resource/service regression tests проходят.

### PERF-05. `P1` Убрать лишние hot-path allocations в metrics/logging фильтрах

- Архитектурное решение:
  - перестать создавать `new ObjectMapper()` на каждый response в `HttpRequestMetricsFilter`.
  - вынести payload-size estimation в переиспользуемый bean и отключаемую конфигурацию.
  - не сериализовать большие DTO только ради приблизительной оценки размера ответа.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/HttpRequestMetricsRegistry.java`
  - `apps/backend/src/main/resources/application.properties`
  - `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/InfrastructureFiltersTest.java`
- Критерии проверки:
  - в request filter нет per-request `ObjectMapper` allocation.
  - отключение payload-bytes measurement возможно через config.
  - тесты фильтров и `./mvnw verify` проходят.

### PERF-06. `P1` Включить короткоживущий cache только для стабильных read-heavy данных

- Архитектурное решение:
  - использовать уже подключенный `quarkus-cache` только для данных с понятной invalidation model:
    - base data;
    - system dashboard static metadata;
    - возможно analytics aggregates с очень коротким TTL.
  - не кешировать child/family mutable state без scope key и invalidation strategy.
- Пути к файлам:
  - `apps/backend/pom.xml`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/BaseDataService.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SystemDashboardService.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
  - `apps/backend/src/main/resources/baseData.json`
- Критерии проверки:
  - для каждого cached метода зафиксированы TTL и invalidation trigger.
  - mutable family snapshot не попадает в cache без явной архитектурной причины.
  - повторный вызов read-heavy endpoint показывает снижение повторной CPU/IO работы в логах/метриках.

## 3. Оптимизация работы с БД

### DB-07. `P0` Убрать family-wide full scans из runtime API

- Архитектурное решение:
  - запретить использование методов вида `getAllHistoryForFamily()` и `getAllRequestsForFamily()` в runtime-потоках UI, если результат потенциально неограничен.
  - для UI read paths использовать pagination, aggregates и projections вместо full-row list loads.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyDataRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SystemDashboardService.java`
- Критерии проверки:
  - runtime endpoints не загружают unbounded rows в память.
  - read queries используют `range`, `page`, `count`, aggregate queries или explicit limits.
  - H2 test baseline и PostgreSQL-oriented verify остаются зелёными.

### DB-08. `P1` Перевести тяжёлые write paths на более явные command/update стратегии

- Архитектурное решение:
  - пересмотреть операции `replaceHistory()` и `replaceRequests()`, где сейчас возможны delete-then-insert сценарии.
  - для частых мутаций использовать explicit command services и targeted updates, а не wholesale replacement коллекций.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyDataRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TaskUpsertCommand.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ShopItemUpsertCommand.java`
- Критерии проверки:
  - частое редактирование tasks/shop/requests/history не требует wholesale rewrite таблиц без необходимости.
  - сервисные write-path тесты проходят без регрессии observable behavior.
  - транзакционные границы остаются явными.

### DB-09. `P1` Сделать индексный аудит под реальные query paths и закрепить его миграциями

- Архитектурное решение:
  - после уже существующего `V19__add_composite_indexes.sql` провести второй проход по query paths:
    - `family_parent_memberships`
    - `parent_accounts`
    - `device_push_tokens`
    - `friends`
    - `requests(status, family_id, created_at)`
  - новые индексы добавлять только после проверки запросов и explain-планов.
- Пути к файлам:
  - `apps/backend/src/main/resources/db/migration/`
  - `apps/backend/src/test/resources/db/migration/`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyParentMembershipRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ParentAccountRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FriendRepository.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/PushResource.java`
- Критерии проверки:
  - каждая новая миграция имеет H2 test counterpart при необходимости.
  - для новых hot queries зафиксирован `EXPLAIN` до/после.
  - `./mvnw verify` и миграционные тесты проходят.

## 4. Улучшение observability

### OBS-10. `P0` Довести trace propagation до стандарта `traceparent` + MDC correlation

- Архитектурное решение:
  - `TraceFilter` должен уметь извлекать trace id из `traceparent`, а `X-Trace-Id` оставить как backward-compatible fallback.
  - backend error responses, client error reports и service logs должны использовать один correlation key.
  - MDC наполняется на входе и очищается на выходе для каждого request.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/TraceFilter.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/exception/GlobalExceptionMapper.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/ClientErrorResource.java`
  - `apps/backend/src/main/resources/application.properties`
  - `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/InfrastructureFiltersTest.java`
- Критерии проверки:
  - inbound `traceparent` отражается в логах и в response header.
  - при отсутствии `traceparent` генерируется новый trace id.
  - в error flow trace id доступен и в log, и в response/client-report path.

### OBS-11. `P1` Заменить ad-hoc observability payloads на typed DTOs и явные operational contracts

- Архитектурное решение:
  - `Map<String, Object>` в `SystemDashboardService` и `SuperAdminResource` заменить на typed response models.
  - разделить overview/db/http-metrics/logs contracts, чтобы они были стабильными и тестируемыми.
  - health endpoints оставить стандартными Quarkus, а super-admin dashboard считать отдельным operational API.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SystemDashboardService.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/SuperAdminResource.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/`
  - `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/SuperAdminResourceTest.java`
- Критерии проверки:
  - observability endpoints возвращают типизированные DTO, а не свободные maps.
  - resource tests проверяют shape контрактов.
  - operational API остается только под `super_admin`.

### OBS-12. `P1` Добавить slow-request и slow-query operational visibility

- Архитектурное решение:
  - ввести пороговые warn/error логи для медленных endpoint-ов и тяжелых DB-paths.
  - логировать method/path/status/duration/familyId-childId только там, где это реально помогает разбирать инциденты.
  - не плодить шум: thresholds должны быть конфигурируемыми.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`
  - `apps/backend/src/main/resources/application.properties`
- Критерии проверки:
  - медленные запросы видны в логах без перехода в debug-only режим.
  - thresholds регулируются через config.
  - нет лавины одинаковых логов на каждом обычном запросе.

## 5. Метрики для New Relic dashboard

### NR-13. `P0` Перейти от ad-hoc registry к стандартному Micrometer-based metrics pipeline

- Архитектурное решение:
  - текущий JVM New Relic agent в `apps/backend/Dockerfile.jvm` оставить для APM/log forwarding.
  - application metrics перевести на стандартный Quarkus путь через Micrometer.
  - основной вариант для репозитория: `quarkus-micrometer` + export через OpenTelemetry/OTLP в New Relic, потому что это ближе к официальному Quarkus observability path и не привязывает доменные метрики к custom in-memory endpoint.
  - `GET /api/super/system/http-metrics` можно оставить как transitional admin view, но source of truth должен стать стандартный metrics pipeline.
- Пути к файлам:
  - `apps/backend/pom.xml`
  - `apps/backend/src/main/resources/application.properties`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/HttpRequestMetricsRegistry.java`
  - `apps/backend/Dockerfile.jvm`
  - `.env.example`
  - `apps/backend/.env.example`
  - `docker-compose.yml`
  - `docker-compose.native.yml`
  - `docs/monitoring/newrelic.md`
- Критерии проверки:
  - backend публикует стандартные HTTP/JVM/application metrics через Micrometer pipeline.
  - New Relic получает метрики через OTLP-configured export path.
  - локальный `docker compose --env-file .env.example config` остаётся валидным.

### NR-14. `P1` Добавить бизнес-метрики для ключевых backend use cases

- Архитектурное решение:
  - кроме инфраструктурных JVM/HTTP метрик, добавить counters/timers для доменных сценариев:
    - login success/failure;
    - family snapshot load;
    - task complete / reward purchase approve/reject;
    - websocket notify fan-out;
    - critical admin and maintenance flows.
  - метрики ставить в service layer, не в resource layer.
- Пути к файлам:
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/WebSocketNotificationService.java`
  - `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/WebSocketNotificationService.java`
- Критерии проверки:
  - каждая метрика имеет понятное имя, low-cardinality tags и owner use case.
  - в коде нет familyId/email/token в metric tags.
  - New Relic dashboard может строить минимум:
    - request rate/error rate/p95 latency;
    - login failure rate;
    - snapshot load latency;
    - operational success/failure count for critical admin flows.

### NR-15. `P1` Завести deployment contract для New Relic metrics отдельно от Browser agent

- Архитектурное решение:
  - не смешивать browser config (`VITE_NEW_RELIC_*`) и backend telemetry env vars.
  - backend metrics export contract документировать отдельно: OTLP endpoint, auth header/api key, enable flags, environment labels.
  - все переменные должны иметь безопасные local defaults или быть явно required only in prod.
- Пути к файлам:
  - `.env.example`
  - `apps/backend/.env.example`
  - `docker-compose.yml`
  - `docker-compose.native.yml`
  - `docs/monitoring/newrelic.md`
  - `README.md`
- Критерии проверки:
  - backend telemetry vars полностью отражены в env examples и compose.
  - README и monitoring doc объясняют отличие APM agent, browser agent и OTLP metrics.
  - `docker compose --env-file .env.example config` проходит без пропусков переменных.

## Рекомендуемый порядок выполнения

Оптимальная очередь для ИИ-агента:

1. `ARC-01` Разрезать `FamilyServiceImpl` на отдельные сервисы для read-side family dashboard.
2. `ARC-02` Убрать god-repository `FamilyDataRepository`.
3. `API-03` Разрезать крупные `resource`-классы по bounded API surfaces.
4. `PERF-04` Декомпозировать family snapshot на shell и section-scoped payloads.
5. `PERF-05` Убрать лишние hot-path allocations в metrics/logging фильтрах.
6. `PERF-06` Включить короткоживущий cache только для стабильных read-heavy данных.
7. `DB-07` Убрать family-wide full scans из runtime API.
8. `DB-08` Перевести тяжёлые write paths на более явные command/update стратегии.
9. `DB-09` Сделать индексный аудит под реальные query paths и закрепить его миграциями.
10. `OBS-10` Довести trace propagation до стандарта `traceparent` + MDC correlation.
11. `OBS-11` Заменить ad-hoc observability payloads на typed DTOs и явные operational contracts.
12. `OBS-12` Добавить slow-request и slow-query operational visibility.
13. `NR-13` Перейти от ad-hoc registry к стандартному Micrometer-based metrics pipeline.
14. `NR-14` Добавить бизнес-метрики для ключевых backend use cases.
15. `NR-15` Завести deployment contract для New Relic metrics отдельно от Browser agent.

## Definition of Done для всего backlog

- Backend остается в рамках `resource -> service -> repository`.
- Крупные классы больше не являются точками концентрации unrelated responsibility.
- Runtime API не грузит unbounded rows в память.
- Trace/log/metrics path стандартизирован и пригоден для прод-диагностики.
- New Relic dashboard получает и инфраструктурные, и доменные backend metrics по documented contract.
- `cd apps/backend && ./mvnw verify && ./mvnw quarkus:build` проходит.
